package com.volunteer.signup.model;

import java.util.ArrayList;
import java.util.List;

public class VolunteerSummary {

    private final String name;
    private final String email;
    private final List<OccurrenceRef> occurrences = new ArrayList<>();

    public VolunteerSummary(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void addOccurrence(String label, String date, String taskId, String signUpId) {
        occurrences.add(new OccurrenceRef(label, date, taskId, signUpId));
    }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public List<OccurrenceRef> getOccurrences() { return occurrences; }

    public String getOccurrenceLabels() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < occurrences.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(occurrences.get(i).getLabel());
        }
        return sb.toString();
    }

    public static class OccurrenceRef {
        private final String label;
        private final String date;
        private final String taskId;
        private final String signUpId;

        public OccurrenceRef(String label, String date, String taskId, String signUpId) {
            this.label = label;
            this.date = date;
            this.taskId = taskId;
            this.signUpId = signUpId;
        }

        public String getLabel() { return label; }
        public String getDate() { return date; }
        public String getTaskId() { return taskId; }
        public String getSignUpId() { return signUpId; }
    }
}
