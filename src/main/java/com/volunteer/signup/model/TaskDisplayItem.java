package com.volunteer.signup.model;

public class TaskDisplayItem {

    private final Task displayTask;
    private final RecurringTaskGroup recurringGroup;

    /** Create a display item for a single (non-recurring) task. */
    public static TaskDisplayItem ofSingle(Task task) {
        return new TaskDisplayItem(task, null);
    }

    /** Create a display item for a recurring group (shows the upcoming task). */
    public static TaskDisplayItem ofRecurring(RecurringTaskGroup group) {
        return new TaskDisplayItem(group.getUpcomingTask(), group);
    }

    private TaskDisplayItem(Task displayTask, RecurringTaskGroup recurringGroup) {
        this.displayTask = displayTask;
        this.recurringGroup = recurringGroup;
    }

    public boolean isRecurring() {
        return recurringGroup != null;
    }

    public Task getDisplayTask() {
        return displayTask;
    }

    public RecurringTaskGroup getRecurringGroup() {
        return recurringGroup;
    }
}
