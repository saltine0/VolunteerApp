'use strict';

const fs         = require('fs');
const path       = require('path');
const nodemailer = require('nodemailer');
const cron       = require('node-cron');

const CONFIG_FILE = path.resolve(__dirname, '..', process.env.EMAIL_CONFIG_FILE || 'data/email-config.json');

const DEFAULT_CONFIG = {
    smtpHost: '',
    smtpPort: 587,
    smtpUsername: '',
    smtpPassword: '',
    tlsEnabled: true,
    fromAddress: '',
    daysBeforeReminder: 1,
    reminderTime: '08:00',
    enabled: false
};

let config = { ...DEFAULT_CONFIG };

// ── File I/O ──────────────────────────────────────────────────────────────────

function loadFromFile() {
    try {
        const raw = fs.readFileSync(CONFIG_FILE, 'utf8');
        config = { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
    } catch (e) {
        console.error('Error reading email config file:', e.message);
        config = { ...DEFAULT_CONFIG };
    }
}

function saveToFile() {
    try {
        const dir = path.dirname(CONFIG_FILE);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2), 'utf8');
    } catch (e) {
        console.error('Error writing email config file:', e.message);
    }
}

// ── Init ──────────────────────────────────────────────────────────────────────

(function init() {
    if (fs.existsSync(CONFIG_FILE) && fs.statSync(CONFIG_FILE).size > 0) {
        loadFromFile();
    }
})();

// ── Transporter ───────────────────────────────────────────────────────────────

function createTransporter(cfg) {
    // tlsEnabled=true  → STARTTLS (secure:false, upgrades after connect, typical port 587)
    // tlsEnabled=false → SSL     (secure:true,  direct TLS, typical port 465)
    return nodemailer.createTransport({
        host: cfg.smtpHost,
        port: cfg.smtpPort,
        secure: !cfg.tlsEnabled,
        auth: { user: cfg.smtpUsername, pass: cfg.smtpPassword },
        connectionTimeout: 5000,
        socketTimeout: 5000
    });
}

// ── HTML escape ───────────────────────────────────────────────────────────────

function escapeHtml(text) {
    if (!text) return '';
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ── Public API ────────────────────────────────────────────────────────────────

function getConfig() {
    return config;
}

function saveConfig(newConfig) {
    config = { ...DEFAULT_CONFIG, ...newConfig };
    saveToFile();
}

async function sendTestEmail(toAddress) {
    if (!config.smtpHost || !config.smtpHost.trim()) {
        throw new Error('SMTP host is not configured.');
    }
    const transporter = createTransporter(config);
    const from = (config.fromAddress && config.fromAddress.trim())
        ? config.fromAddress
        : config.smtpUsername;

    await transporter.sendMail({
        from,
        to: toAddress,
        subject: 'Volunteer Reminder \u2014 Test Email',
        html: '<p>Your email settings are configured correctly!</p>'
    });
}

async function runReminderJob(allTasks) {
    if (!config.enabled) return;
    if (!config.smtpHost || !config.smtpHost.trim()) return;

    const configuredHour = parseReminderHour();
    if (new Date().getHours() !== configuredHour) return;

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(today);
    target.setDate(target.getDate() + config.daysBeforeReminder);
    const targetDateStr = dateToString(target);

    for (const task of allTasks) {
        if (task.date !== targetDateStr) continue;
        if (!task.signUps || task.signUps.length === 0) continue;
        for (const signUp of task.signUps) {
            await sendReminderEmail(task, signUp);
        }
    }
}

async function sendReminderEmail(task, signUp) {
    try {
        const transporter = createTransporter(config);
        const from = (config.fromAddress && config.fromAddress.trim())
            ? config.fromAddress
            : config.smtpUsername;

        const fmtDate = formattedDate(task.date) || task.date || '';
        const person  = (task.personAssisting && task.personAssisting.trim()) ? task.personAssisting : '\u2014';
        const time    = formattedTimeRange(task.startTime, task.endTime) || '\u2014';

        const body = `<!DOCTYPE html><html><body style="font-family:'Plus Jakarta Sans',Arial,sans-serif;background:#F6F1EB;padding:32px;">
<div style="max-width:520px;margin:0 auto;background:#FFFDF9;border-radius:12px;border:1px solid #E8E2DA;padding:32px;">
<h2 style="color:#C2593B;margin-top:0;">Volunteer Reminder</h2>
<p>Hi ${escapeHtml(signUp.name)},</p>
<p>This is a friendly reminder that you're signed up to volunteer for:</p>
<table style="width:100%;border-collapse:collapse;margin:20px 0;">
<tr><td style="color:#8A7E76;padding:6px 0;width:130px;">Task</td><td style="font-weight:600;">${escapeHtml(task.name)}</td></tr>
<tr><td style="color:#8A7E76;padding:6px 0;">Date</td><td>${escapeHtml(fmtDate)}</td></tr>
<tr><td style="color:#8A7E76;padding:6px 0;">Time</td><td>${escapeHtml(time)}</td></tr>
<tr><td style="color:#8A7E76;padding:6px 0;">Coordinator</td><td>${escapeHtml(person)}</td></tr>
</table>
<p style="color:#5B7B6A;">Thank you for volunteering!</p>
</div></body></html>`;

        await transporter.sendMail({
            from,
            to: signUp.email,
            subject: `Reminder: You're volunteering for "${task.name}" on ${fmtDate}`,
            html: body
        });
    } catch (e) {
        console.error(`Failed to send reminder to ${signUp.email}:`, e.message);
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function parseReminderHour() {
    try {
        const t = config.reminderTime;
        if (!t) return 8;
        return parseInt(t.split(':')[0]) || 8;
    } catch (e) {
        return 8;
    }
}

function dateToString(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

function formattedDate(dateStr) {
    if (!dateStr) return null;
    const parts = dateStr.split('-');
    if (parts.length !== 3) return dateStr;
    const d = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
}

function formattedTime(timeStr) {
    if (!timeStr) return null;
    const parts = timeStr.split(':');
    if (parts.length < 2) return timeStr;
    const d = new Date(2000, 0, 1, parseInt(parts[0]), parseInt(parts[1]));
    if (isNaN(d.getTime())) return timeStr;
    return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
}

function formattedTimeRange(startTime, endTime) {
    const start = formattedTime(startTime);
    const end   = formattedTime(endTime);
    if (!start) return null;
    if (!end)   return start;
    return `${start} \u2013 ${end}`;
}

module.exports = { getConfig, saveConfig, sendTestEmail, runReminderJob };
