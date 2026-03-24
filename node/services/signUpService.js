'use strict';

const fs = require('fs');
const path = require('path');
const { v4: uuidv4 } = require('uuid');

const DATA_FILE = path.resolve(__dirname, '..', process.env.DATA_FILE || 'data/data.json');

let tasks = [];

// ── Date / time helpers ──────────────────────────────────────────────────────

function parseLocalDate(dateStr) {
    if (!dateStr) return null;
    const parts = dateStr.split('-');
    if (parts.length !== 3) return null;
    const d = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
    return isNaN(d.getTime()) ? null : d;
}

function dateToString(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

function formattedDate(dateStr) {
    if (!dateStr) return null;
    const d = parseLocalDate(dateStr);
    if (!d) return dateStr;
    return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
}

function formattedTime(timeStr) {
    if (!timeStr) return null;
    const parts = timeStr.split(':');
    if (parts.length < 2) return timeStr;
    const hours = parseInt(parts[0]);
    const minutes = parseInt(parts[1]);
    const d = new Date(2000, 0, 1, hours, minutes);
    if (isNaN(d.getTime())) return timeStr;
    return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
}

function recurrenceDisplayLabel(task) {
    if (!task.recurringGroupId || !task.occurrenceNumber || !task.recurrenceType) return null;
    switch (task.recurrenceType) {
        case 'DAILY':    return `Day ${task.occurrenceNumber}`;
        case 'WEEKLY':   return `Week ${task.occurrenceNumber}`;
        case 'BIWEEKLY': return `Week ${task.occurrenceNumber}`;
        case 'MONTHLY':  return `Month ${task.occurrenceNumber}`;
        default:         return null;
    }
}

// Attaches computed display properties to a task object (mutates in place).
function decorateTask(task) {
    task.formattedDate      = formattedDate(task.date);
    task.formattedStartTime = formattedTime(task.startTime);
    task.formattedEndTime   = formattedTime(task.endTime);

    const start = formattedTime(task.startTime);
    const end   = formattedTime(task.endTime);
    if (!start)      task.formattedTimeRange = null;
    else if (!end)   task.formattedTimeRange = start;
    else             task.formattedTimeRange = `${start} \u2013 ${end}`;

    const signUps = task.signUps || [];
    task.full           = task.maxVolunteers > 0 && signUps.length >= task.maxVolunteers;
    task.spotsRemaining = task.maxVolunteers <= 0 ? -1 : Math.max(0, task.maxVolunteers - signUps.length);
    task.signUpEmails   = signUps.map(s => s.email);
    task.recurrenceDisplayLabel = recurrenceDisplayLabel(task);
    return task;
}

// ── RecurringTaskGroup ────────────────────────────────────────────────────────

function buildRecurringTaskGroup(groupId, allGroupTasks) {
    const sorted = [...allGroupTasks]
        .sort((a, b) => (a.date || '').localeCompare(b.date || ''));

    const recurrenceType = sorted.length > 0 ? sorted[0].recurrenceType : '';

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const futureOccurrences = sorted.filter(t => {
        const d = parseLocalDate(t.date);
        return d && d >= today;
    });

    const upcomingTask = futureOccurrences.length > 0 ? futureOccurrences[0] : null;

    const recurrenceTypeDisplay = {
        DAILY: 'Daily', WEEKLY: 'Weekly', BIWEEKLY: 'Biweekly', MONTHLY: 'Monthly'
    }[recurrenceType] || 'Recurring';

    const allFutureOccurrencesFull =
        futureOccurrences.length === 0 || futureOccurrences.every(t => t.full);

    const upcomingSignUpCount = upcomingTask ? (upcomingTask.signUps || []).length : 0;

    // Aggregate volunteers across all occurrences
    const byEmail = new Map();
    for (const occ of sorted) {
        const label = occ.recurrenceDisplayLabel || occ.formattedDate;
        for (const s of (occ.signUps || [])) {
            const key = s.email.toLowerCase();
            if (!byEmail.has(key)) {
                byEmail.set(key, { name: s.name, email: s.email, occurrences: [] });
            }
            byEmail.get(key).occurrences.push({
                label,
                date: occ.formattedDate,
                taskId: occ.id,
                signUpId: s.id
            });
        }
    }
    const volunteerSummaries = [...byEmail.values()];

    return {
        recurringGroupId: groupId,
        recurrenceType,
        recurrenceTypeDisplay,
        allOccurrences: sorted,
        upcomingTask,
        futureOccurrences,
        upcomingCount: futureOccurrences.length,
        allFutureOccurrencesFull,
        upcomingSignUpCount,
        volunteerSummaries
    };
}

// ── Display items ─────────────────────────────────────────────────────────────

function buildDisplayItems(taskList) {
    // Decorate all tasks in the working list
    taskList.forEach(decorateTask);

    const recurringGroups = new Map();
    const singleTasks = [];

    for (const t of taskList) {
        if (t.recurringGroupId) {
            if (!recurringGroups.has(t.recurringGroupId)) {
                recurringGroups.set(t.recurringGroupId, []);
            }
            recurringGroups.get(t.recurringGroupId).push(t);
        } else {
            singleTasks.push(t);
        }
    }

    const items = [];

    for (const t of singleTasks) {
        items.push({ displayTask: t, recurringGroup: null, recurring: false });
    }

    for (const [groupId] of recurringGroups) {
        // Always build the group from ALL tasks so the date picker shows every occurrence
        const allGroupTasks = tasks
            .filter(t => t.recurringGroupId === groupId)
            .map(t => decorateTask({ ...t }));
        const group = buildRecurringTaskGroup(groupId, allGroupTasks);
        if (group.upcomingTask) {
            items.push({ displayTask: group.upcomingTask, recurringGroup: group, recurring: true });
        }
    }

    // Sort by date then start time
    items.sort((a, b) => {
        const da = parseLocalDate(a.displayTask.date);
        const db = parseLocalDate(b.displayTask.date);
        if (!da && !db) return 0;
        if (!da) return 1;
        if (!db) return -1;
        const cmp = da - db;
        if (cmp !== 0) return cmp;
        const ta = a.displayTask.startTime || '';
        const tb = b.displayTask.startTime || '';
        return ta.localeCompare(tb);
    });

    return items;
}

// ── Sort helpers ──────────────────────────────────────────────────────────────

function sortTasksByDate(taskList) {
    return [...taskList].sort((a, b) => {
        const da = parseLocalDate(a.date);
        const db = parseLocalDate(b.date);
        if (!da && !db) return 0;
        if (!da) return 1;
        if (!db) return -1;
        const cmp = da - db;
        if (cmp !== 0) return cmp;
        return (a.startTime || '').localeCompare(b.startTime || '');
    });
}

// ── File I/O ──────────────────────────────────────────────────────────────────

function loadFromFile() {
    try {
        const raw = fs.readFileSync(DATA_FILE, 'utf8');
        const data = JSON.parse(raw);
        tasks = data.tasks || [];
    } catch (e) {
        console.error('Error reading data file:', e.message);
        seedData();
    }
}

function saveToFile() {
    try {
        const dir = path.dirname(DATA_FILE);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(DATA_FILE, JSON.stringify({ tasks }, null, 2), 'utf8');
    } catch (e) {
        console.error('Error writing data file:', e.message);
    }
}

function seedData() {
    tasks = [
        makeTask('Setup/Cleanup',      'Jane Doe',     '2026-03-01', '08:00', '10:00', 10),
        makeTask('Registration Desk',  'John Smith',   '2026-03-01', '09:00', '12:00', 5),
        makeTask('Food Service',       'Maria Garcia', '2026-03-01', '11:00', '14:00', 8)
    ];
}

function makeTask(name, personAssisting, date, startTime, endTime, maxVolunteers) {
    return { id: uuidv4(), name, personAssisting, date, startTime, endTime, maxVolunteers, signUps: [] };
}

// ── Init ──────────────────────────────────────────────────────────────────────

(function init() {
    if (fs.existsSync(DATA_FILE) && fs.statSync(DATA_FILE).size > 0) {
        loadFromFile();
    } else {
        seedData();
        saveToFile();
    }
})();

// ── Public API ────────────────────────────────────────────────────────────────

function getAllTasks() {
    return sortTasksByDate(tasks);
}

function getTasksByDateRange(startDate, endDate) {
    const start = parseLocalDate(startDate);
    const end   = parseLocalDate(endDate);
    if (!start && !end) return getAllTasks();
    const filtered = tasks.filter(t => {
        const d = parseLocalDate(t.date);
        if (!d) return false;
        if (start && d < start) return false;
        if (end   && d > end)   return false;
        return true;
    });
    return sortTasksByDate(filtered);
}

function getTaskDisplayItems() {
    return buildDisplayItems(getAllTasks());
}

function getTaskDisplayItemsByDateRange(startDate, endDate) {
    return buildDisplayItems(getTasksByDateRange(startDate, endDate));
}

function getTotalVolunteers() {
    return tasks.reduce((sum, t) => sum + (t.signUps || []).length, 0);
}

function getTaskById(taskId) {
    return tasks.find(t => t.id === taskId) || null;
}

function addTask(name, personAssisting, date, startTime, endTime, maxVolunteers, recurrenceType, recurrenceEndDate) {
    if (!recurrenceType || recurrenceType === 'NONE' || !recurrenceEndDate) {
        const task = makeTask(name, personAssisting, date, startTime, endTime, maxVolunteers);
        tasks.push(task);
        saveToFile();
        return [task];
    }
    const generated = generateRecurringTasks(name, personAssisting, date, startTime, endTime,
        maxVolunteers, recurrenceType, recurrenceEndDate);
    tasks.push(...generated);
    saveToFile();
    return generated;
}

function updateTask(taskId, name, personAssisting, date, startTime, endTime, maxVolunteers) {
    const task = getTaskById(taskId);
    if (!task) return false;
    task.name = name;
    task.personAssisting = personAssisting;
    task.date = date;
    task.startTime = startTime;
    task.endTime = endTime;
    task.maxVolunteers = maxVolunteers;
    saveToFile();
    return true;
}

function removeTask(taskId) {
    const before = tasks.length;
    tasks = tasks.filter(t => t.id !== taskId);
    if (tasks.length !== before) { saveToFile(); return true; }
    return false;
}

function signUpForTask(taskId, name, email) {
    const task = getTaskById(taskId);
    if (!task) return 'Task not found.';
    const signUps = task.signUps || [];
    if (task.maxVolunteers > 0 && signUps.length >= task.maxVolunteers)
        return 'This task is full. No more volunteers can sign up.';
    if (signUps.some(s => s.email.toLowerCase() === email.toLowerCase()))
        return 'You are already signed up for this task.';
    signUps.push({ id: uuidv4(), name, email });
    task.signUps = signUps;
    saveToFile();
    return null;
}

function signUpForMultipleTasks(taskIds, name, email) {
    const errors = [];
    const succeededTasks = [];
    for (const taskId of taskIds) {
        const err = signUpForTask(taskId, name, email);
        if (err) {
            errors.push(err);
        } else {
            const t = getTaskById(taskId);
            if (t) succeededTasks.push(decorateTask({ ...t }));
        }
    }
    return { errors, succeededTasks };
}

function removeSignUp(taskId, signUpId) {
    const task = getTaskById(taskId);
    if (!task) return false;
    const before = (task.signUps || []).length;
    task.signUps = (task.signUps || []).filter(s => s.id !== signUpId);
    if (task.signUps.length !== before) { saveToFile(); return true; }
    return false;
}

// ── Recurring task generation ─────────────────────────────────────────────────

function generateRecurringTasks(name, personAssisting, startDateStr, startTime, endTime,
                                 maxVolunteers, recurrenceType, endDateStr) {
    const startDate = parseLocalDate(startDateStr);
    let endDate     = parseLocalDate(endDateStr);
    if (!startDate || !endDate) throw new Error('Invalid date format.');

    const maxEnd = new Date(startDate);
    maxEnd.setDate(maxEnd.getDate() + 365);
    if (endDate > maxEnd) endDate = maxEnd;
    if (endDate < startDate) throw new Error('End date must be after start date.');

    const groupId = uuidv4();
    const result  = [];
    const current = new Date(startDate);
    let occurrence = 1;

    while (current <= endDate) {
        const task = makeTask(name, personAssisting, dateToString(current), startTime, endTime, maxVolunteers);
        task.recurringGroupId = groupId;
        task.recurrenceType   = recurrenceType;
        task.occurrenceNumber = occurrence;
        result.push(task);
        occurrence++;

        switch (recurrenceType) {
            case 'DAILY':    current.setDate(current.getDate() + 1);    break;
            case 'WEEKLY':   current.setDate(current.getDate() + 7);    break;
            case 'BIWEEKLY': current.setDate(current.getDate() + 14);   break;
            case 'MONTHLY':  current.setMonth(current.getMonth() + 1);  break;
            default: return result;
        }
    }
    return result;
}

module.exports = {
    getAllTasks,
    getTasksByDateRange,
    getTaskDisplayItems,
    getTaskDisplayItemsByDateRange,
    getTotalVolunteers,
    getTaskById,
    addTask,
    updateTask,
    removeTask,
    signUpForTask,
    signUpForMultipleTasks,
    removeSignUp
};
