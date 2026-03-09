package com.volunteer.signup.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Task {

    private String id;
    private String name;
    private String personAssisting;
    private String date;
    private String startTime;
    private String endTime;
    private int maxVolunteers;
    private List<SignUp> signUps = new ArrayList<>();

    private String recurringGroupId;
    private String recurrenceType;
    private Integer occurrenceNumber;

    public Task() {
        this.id = UUID.randomUUID().toString();
    }

    public Task(String name, String personAssisting, String date, String startTime, String endTime, int maxVolunteers) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.personAssisting = personAssisting;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxVolunteers = maxVolunteers;
    }

    public boolean isRecurring() {
        return recurringGroupId != null && !recurringGroupId.isEmpty();
    }

    public String getRecurrenceDisplayLabel() {
        if (!isRecurring() || occurrenceNumber == null || recurrenceType == null) {
            return null;
        }
        switch (recurrenceType) {
            case "DAILY": return "Day " + occurrenceNumber;
            case "WEEKLY": return "Week " + occurrenceNumber;
            case "BIWEEKLY": return "Week " + occurrenceNumber;
            case "MONTHLY": return "Month " + occurrenceNumber;
            default: return null;
        }
    }

    public List<String> getSignUpEmails() {
        return signUps.stream().map(SignUp::getEmail).collect(Collectors.toList());
    }

    public boolean hasSignUp(String email) {
        return signUps.stream().anyMatch(s -> s.getEmail().equalsIgnoreCase(email));
    }

    public boolean isFull() {
        return maxVolunteers > 0 && signUps.size() >= maxVolunteers;
    }

    public int getSpotsRemaining() {
        if (maxVolunteers <= 0) return -1;
        return Math.max(0, maxVolunteers - signUps.size());
    }

    public String getFormattedDate() {
        if (date == null || date.isEmpty()) return null;
        try {
            LocalDate ld = LocalDate.parse(date);
            return ld.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"));
        } catch (DateTimeParseException e) {
            return date;
        }
    }

    public String getFormattedStartTime() {
        return formatTime(startTime);
    }

    public String getFormattedEndTime() {
        return formatTime(endTime);
    }

    public String getFormattedTimeRange() {
        String start = formatTime(startTime);
        String end = formatTime(endTime);
        if (start == null) return null;
        if (end == null) return start;
        return start + " – " + end;
    }

    private String formatTime(String time) {
        if (time == null || time.isEmpty()) return null;
        try {
            LocalTime lt = LocalTime.parse(time);
            return lt.format(DateTimeFormatter.ofPattern("h:mm a"));
        } catch (DateTimeParseException e) {
            return time;
        }
    }

    public int getMaxVolunteers() { return maxVolunteers; }
    public void setMaxVolunteers(int maxVolunteers) { this.maxVolunteers = maxVolunteers; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPersonAssisting() { return personAssisting; }
    public void setPersonAssisting(String personAssisting) { this.personAssisting = personAssisting; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public List<SignUp> getSignUps() { return signUps; }
    public void setSignUps(List<SignUp> signUps) { this.signUps = signUps; }

    public String getRecurringGroupId() { return recurringGroupId; }
    public void setRecurringGroupId(String recurringGroupId) { this.recurringGroupId = recurringGroupId; }

    public String getRecurrenceType() { return recurrenceType; }
    public void setRecurrenceType(String recurrenceType) { this.recurrenceType = recurrenceType; }

    public Integer getOccurrenceNumber() { return occurrenceNumber; }
    public void setOccurrenceNumber(Integer occurrenceNumber) { this.occurrenceNumber = occurrenceNumber; }
}
