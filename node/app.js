'use strict';

require('dotenv').config();

const express      = require('express');
const cookieParser = require('cookie-parser');
const session      = require('express-session');
const flash        = require('connect-flash');
const csrf         = require('csurf');
const bodyParser   = require('body-parser');
const cron         = require('node-cron');
const path         = require('path');
const { auth, requiresAuth } = require('express-openid-connect');

const signUpService = require('./services/signUpService');
const emailService  = require('./services/emailService');

const app = express();

// ── View engine ───────────────────────────────────────────────────────────────

app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// ── Body parsing ──────────────────────────────────────────────────────────────

app.use(bodyParser.urlencoded({ extended: true }));
app.use(cookieParser());

// ── Session (required for connect-flash) ──────────────────────────────────────

app.use(session({
    secret: process.env.SESSION_SECRET || 'change-this-secret',
    resave: false,
    saveUninitialized: false,
    cookie: { secure: false } // set to true when serving over HTTPS
}));

app.use(flash());

// ── Auth0 ─────────────────────────────────────────────────────────────────────

app.use(auth({
    authRequired: false,
    auth0Logout: true,
    secret: process.env.SESSION_SECRET || 'change-this-secret',
    baseURL: process.env.BASE_URL || `http://localhost:${process.env.PORT || 3000}`,
    clientID: process.env.AUTH0_CLIENT_ID,
    issuerBaseURL: `https://${process.env.AUTH0_DOMAIN}`,
    clientSecret: process.env.AUTH0_CLIENT_SECRET,
    authorizationParams: {
        response_type: 'code',
        scope: 'openid profile email'
    }
}));

// ── CSRF ──────────────────────────────────────────────────────────────────────
// Uses a cookie to store the CSRF secret (no session dependency).
// Note: the `csurf` package is deprecated but still functional. For a
// production app, consider migrating to `csrf-csrf`.

const csrfProtection = csrf({ cookie: true });
app.use(csrfProtection);

// ── Routes ────────────────────────────────────────────────────────────────────

// Helper to get the display name from the Auth0 user object
function getUserName(oidcUser) {
    if (!oidcUser) return null;
    return oidcUser.email || oidcUser.name || oidcUser.sub || null;
}

function formattedDate(dateStr) {
    if (!dateStr) return null;
    const p = dateStr.split('-');
    const d = new Date(parseInt(p[0]), parseInt(p[1]) - 1, parseInt(p[2]));
    return isNaN(d.getTime()) ? dateStr
        : d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
}

function formattedTimeRange(startTime, endTime) {
    const fmt = t => {
        if (!t) return null;
        const p = t.split(':');
        const d = new Date(2000, 0, 1, parseInt(p[0]), parseInt(p[1]));
        return isNaN(d.getTime()) ? t : d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
    };
    const s = fmt(startTime), e = fmt(endTime);
    if (!s) return null;
    return e ? `${s} \u2013 ${e}` : s;
}

// GET / — main page
app.get('/', async (req, res) => {
    try {
        const filterStartDate = req.query.filterStartDate || '';
        const filterEndDate   = req.query.filterEndDate   || '';

        const [taskItems, allTasks, totalVolunteers, emailConfig] = await Promise.all([
            (filterStartDate || filterEndDate)
                ? signUpService.getTaskDisplayItemsByDateRange(filterStartDate, filterEndDate)
                : signUpService.getTaskDisplayItems(),
            signUpService.getAllTasks(),
            signUpService.getTotalVolunteers(),
            emailService.getConfig()
        ]);

        res.render('index', {
            taskItems,
            taskCount: allTasks.length,
            totalVolunteers,
            filterStartDate,
            filterEndDate,
            emailConfig,
            user:     req.oidc.isAuthenticated() ? req.oidc.user : null,
            userName: getUserName(req.oidc.isAuthenticated() ? req.oidc.user : null),
            message:  req.flash('message')[0] || null,
            error:    req.flash('error')[0]   || null,
            csrfToken: req.csrfToken()
        });
    } catch (e) {
        res.status(500).send('Server error: ' + e.message);
    }
});

// POST /tasks/add
app.post('/tasks/add', requiresAuth(), async (req, res) => {
    const { name, personAssisting = '', date = '', startTime = '', endTime = '',
            recurrenceType = 'NONE', recurrenceEndDate = '' } = req.body;
    const maxVolunteers = parseInt(req.body.maxVolunteers) || 0;
    try {
        const created = await signUpService.addTask(name, personAssisting, date, startTime, endTime,
            maxVolunteers, recurrenceType, recurrenceEndDate);
        req.flash('message', created.length === 1
            ? 'Task added successfully!'
            : `${created.length} recurring tasks created!`);
    } catch (e) {
        req.flash('error', 'Error creating task: ' + e.message);
    }
    res.redirect('/');
});

// POST /tasks/update
app.post('/tasks/update', requiresAuth(), async (req, res) => {
    const { taskId, name, personAssisting = '', date = '', startTime = '', endTime = '' } = req.body;
    const maxVolunteers = parseInt(req.body.maxVolunteers) || 0;
    const updated = await signUpService.updateTask(taskId, name, personAssisting, date, startTime, endTime, maxVolunteers);
    req.flash(updated ? 'message' : 'error', updated ? 'Task updated successfully!' : 'Could not update task.');
    res.redirect('/');
});

// POST /tasks/remove
app.post('/tasks/remove', requiresAuth(), async (req, res) => {
    const removed = await signUpService.removeTask(req.body.taskId);
    req.flash(removed ? 'message' : 'error', removed ? 'Task removed successfully.' : 'Could not remove task.');
    res.redirect('/');
});

// POST /signup
app.post('/signup', async (req, res) => {
    const { taskId, signUpName, signUpEmail } = req.body;
    const error = await signUpService.signUpForTask(taskId, signUpName, signUpEmail);
    if (!error) {
        const task = await signUpService.getTaskById(taskId);
        let msg = 'Signed up successfully!';
        if (task) {
            const fmtDate = formattedDate(task.date);
            const fmtTime = formattedTimeRange(task.startTime, task.endTime);
            msg = 'Signed up successfully for ' + (fmtDate || '') + (fmtTime ? ` (${fmtTime})` : '');
        }
        req.flash('message', msg);
    } else {
        req.flash('error', error);
    }
    res.redirect('/');
});

// POST /signup/multiple
app.post('/signup/multiple', async (req, res) => {
    let taskIds = req.body.taskIds;
    if (!taskIds) {
        req.flash('error', 'Please select at least one occurrence.');
        return res.redirect('/');
    }
    if (!Array.isArray(taskIds)) taskIds = [taskIds];
    const { signUpName, signUpEmail } = req.body;
    const result = await signUpService.signUpForMultipleTasks(taskIds, signUpName, signUpEmail);
    if (result.errors.length === 0) {
        const details = result.succeededTasks.map(t => {
            let s = t.formattedDate || '';
            if (t.formattedStartTime) s += ` (${t.formattedStartTime})`;
            return s;
        }).join(', ');
        req.flash('message', 'Signed up successfully for: ' + details);
    } else {
        req.flash('error', result.errors.join('; '));
    }
    res.redirect('/');
});

// POST /signup/remove
app.post('/signup/remove', requiresAuth(), async (req, res) => {
    const { taskId, signUpId } = req.body;
    const removed = await signUpService.removeSignUp(taskId, signUpId);
    req.flash(removed ? 'message' : 'error', removed ? 'Sign-up removed.' : 'Could not remove sign-up.');
    res.redirect('/');
});

// POST /email/config/save
app.post('/email/config/save', requiresAuth(), async (req, res) => {
    try {
        const { smtpHost = '', smtpUsername = '', smtpPassword = '',
                fromAddress = '', reminderTime = '08:00' } = req.body;
        const smtpPort           = parseInt(req.body.smtpPort) || 587;
        const daysBeforeReminder = parseInt(req.body.daysBeforeReminder) || 1;
        const tlsEnabled         = req.body.tlsEnabled === 'true';
        const enabled            = req.body.enabled === 'true';
        const current            = await emailService.getConfig();
        await emailService.saveConfig({
            smtpHost, smtpPort, smtpUsername,
            smtpPassword: smtpPassword.trim() === '' ? current.smtpPassword : smtpPassword,
            tlsEnabled, fromAddress, daysBeforeReminder, reminderTime, enabled
        });
        req.flash('message', 'Email settings saved successfully.');
    } catch (e) {
        req.flash('error', 'Error saving email settings: ' + e.message);
    }
    res.redirect('/');
});

// POST /email/test
app.post('/email/test', requiresAuth(), async (req, res) => {
    const { testEmailAddress } = req.body;
    try {
        await emailService.sendTestEmail(testEmailAddress);
        req.flash('message', `Test email sent to ${testEmailAddress}.`);
    } catch (e) {
        req.flash('error', 'Test email failed: ' + e.message);
    }
    res.redirect('/');
});

// ── Vercel Cron / external scheduler endpoint ─────────────────────────────────
// Called by Vercel Cron Jobs (Pro) or an external scheduler like cron-job.org.
// Secured by a shared secret passed as a Bearer token.

app.get('/api/run-reminders', async (req, res) => {
    const secret = process.env.CRON_SECRET;
    if (secret) {
        const auth = req.headers['authorization'] || '';
        if (auth !== `Bearer ${secret}`) {
            return res.status(401).json({ error: 'Unauthorized' });
        }
    }
    try {
        const allTasks = await signUpService.getAllTasks();
        await emailService.runReminderJob(allTasks);
        res.json({ ok: true });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// ── Hourly reminder cron (local / long-running server only) ───────────────────

cron.schedule('0 * * * *', async () => {
    const allTasks = await signUpService.getAllTasks();
    await emailService.runReminderJob(allTasks);
});

// ── Start ─────────────────────────────────────────────────────────────────────

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log(`Volunteer Sign-Up running at http://localhost:${PORT}`);
});
