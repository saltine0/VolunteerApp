package com.volunteer.signup.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RecurringTaskGroup {

    private final String recurringGroupId;
    private final String taskName;
    private final String recurrenceType;
    private final List<Task> allOccurrences;
    private final Task upcomingTask;
    private final List<Task> futureOccurrences;

    public RecurringTaskGroup(String recurringGroupId, List<Task> occurrences) {
        this.recurringGroupId = recurringGroupId;
        this.allOccurrences = occurrences.stream()
                .sorted(Comparator.comparing(t -> t.getDate() != null ? t.getDate() : ""))
                .collect(Collectors.toList());

        this.taskName = allOccurrences.isEmpty() ? "" : allOccurrences.get(0).getName();
        this.recurrenceType = allOccurrences.isEmpty() ? "" : allOccurrences.get(0).getRecurrenceType();

        LocalDate today = LocalDate.now();
        this.futureOccurrences = allOccurrences.stream()
                .filter(t -> {
                    if (t.getDate() == null || t.getDate().isEmpty()) return false;
                    try {
                        return !LocalDate.parse(t.getDate()).isBefore(today);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        this.upcomingTask = futureOccurrences.isEmpty() ? null : futureOccurrences.get(0);
    }

    public boolean hasUpcoming() {
        return upcomingTask != null;
    }

    public String getRecurrenceTypeDisplay() {
        if (recurrenceType == null) return "Recurring";
        switch (recurrenceType) {
            case "DAILY": return "Daily";
            case "WEEKLY": return "Weekly";
            case "BIWEEKLY": return "Biweekly";
            case "MONTHLY": return "Monthly";
            default: return "Recurring";
        }
    }

    public int getUpcomingCount() {
        return futureOccurrences.size();
    }

    public boolean isAllFutureOccurrencesFull() {
        if (futureOccurrences.isEmpty()) return true;
        return futureOccurrences.stream().allMatch(Task::isFull);
    }

    public List<VolunteerSummary> getVolunteerSummaries() {
        Map<String, VolunteerSummary> byEmail = new LinkedHashMap<>();
        for (Task occ : allOccurrences) {
            String label = occ.getRecurrenceDisplayLabel();
            if (label == null) label = occ.getFormattedDate();
            for (SignUp s : occ.getSignUps()) {
                String key = s.getEmail().toLowerCase();
                VolunteerSummary vs = byEmail.get(key);
                if (vs == null) {
                    vs = new VolunteerSummary(s.getName(), s.getEmail());
                    byEmail.put(key, vs);
                }
                vs.addOccurrence(label, occ.getFormattedDate(), occ.getId(), s.getId());
            }
        }
        return new ArrayList<>(byEmail.values());
    }

    public int getTotalSignUpCount() {
        int count = 0;
        for (Task occ : allOccurrences) {
            count += occ.getSignUps().size();
        }
        return count;
    }

    public int getUpcomingSignUpCount() {
        if (upcomingTask == null) return 0;
        return upcomingTask.getSignUps().size();
    }

    public List<SignUp> getUpcomingSignUps() {
        if (upcomingTask == null) return new ArrayList<>();
        return upcomingTask.getSignUps();
    }

    public String getRecurringGroupId() { return recurringGroupId; }
    public String getTaskName() { return taskName; }
    public String getRecurrenceType() { return recurrenceType; }
    public List<Task> getAllOccurrences() { return allOccurrences; }
    public Task getUpcomingTask() { return upcomingTask; }
    public List<Task> getFutureOccurrences() { return futureOccurrences; }
}
