package com.volunteer.signup.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.volunteer.signup.model.RecurringTaskGroup;
import com.volunteer.signup.model.SignUp;
import com.volunteer.signup.model.Task;
import com.volunteer.signup.model.TaskDisplayItem;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class SignUpService {

    private final ObjectMapper objectMapper;
    private final File dataFile;
    private List<Task> tasks = new ArrayList<>();

    public SignUpService(@Value("${app.data-file:src/main/resources/data/data.json}") String dataFilePath) {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.dataFile = new File(dataFilePath);
    }

    @PostConstruct
    public void init() {
        if (dataFile.exists() && dataFile.length() > 0) {
            loadFromFile();
        } else {
            seedData();
            saveToFile();
        }
    }

    public List<Task> getAllTasks() {
        return sortTasksByDate(tasks);
    }

    public List<Task> getTasksByDateRange(String startDate, String endDate) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start == null && end == null) {
            return getAllTasks();
        }
        List<Task> filtered = tasks.stream().filter(t -> {
            LocalDate taskDate = parseDate(t.getDate());
            if (taskDate == null) return false;
            if (start != null && taskDate.isBefore(start)) return false;
            if (end != null && taskDate.isAfter(end)) return false;
            return true;
        }).collect(Collectors.toList());
        return sortTasksByDate(filtered);
    }

    public List<TaskDisplayItem> getTaskDisplayItems() {
        return buildDisplayItems(getAllTasks());
    }

    public List<TaskDisplayItem> getTaskDisplayItemsByDateRange(String startDate, String endDate) {
        return buildDisplayItems(getTasksByDateRange(startDate, endDate));
    }

    private List<TaskDisplayItem> buildDisplayItems(List<Task> taskList) {
        List<TaskDisplayItem> items = new ArrayList<>();

        // Partition into recurring (by groupId) and non-recurring
        Map<String, List<Task>> recurringGroups = new LinkedHashMap<>();
        List<Task> singleTasks = new ArrayList<>();

        for (Task t : taskList) {
            if (t.isRecurring()) {
                recurringGroups.computeIfAbsent(t.getRecurringGroupId(), k -> new ArrayList<>()).add(t);
            } else {
                singleTasks.add(t);
            }
        }

        // Wrap single tasks
        for (Task t : singleTasks) {
            items.add(TaskDisplayItem.ofSingle(t));
        }

        // Wrap recurring groups — only include if there's an upcoming occurrence
        // Note: we build the group from ALL occurrences (not just filtered ones)
        // so the multi-select shows all future dates
        for (Map.Entry<String, List<Task>> entry : recurringGroups.entrySet()) {
            String groupId = entry.getKey();
            // Get all tasks for this group (not just the filtered set)
            List<Task> allGroupTasks = tasks.stream()
                    .filter(t -> groupId.equals(t.getRecurringGroupId()))
                    .collect(Collectors.toList());
            RecurringTaskGroup group = new RecurringTaskGroup(groupId, allGroupTasks);
            if (group.hasUpcoming()) {
                items.add(TaskDisplayItem.ofRecurring(group));
            }
        }

        // Sort by display task date
        items.sort((a, b) -> {
            LocalDate dateA = parseDate(a.getDisplayTask().getDate());
            LocalDate dateB = parseDate(b.getDisplayTask().getDate());
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            int cmp = dateA.compareTo(dateB);
            if (cmp != 0) return cmp;
            String timeA = a.getDisplayTask().getStartTime() != null ? a.getDisplayTask().getStartTime() : "";
            String timeB = b.getDisplayTask().getStartTime() != null ? b.getDisplayTask().getStartTime() : "";
            return timeA.compareTo(timeB);
        });

        return items;
    }

    public MultiSignUpResult signUpForMultipleTasks(List<String> taskIds, String name, String email) {
        List<String> errors = new ArrayList<>();
        List<Task> succeededTasks = new ArrayList<>();
        for (String taskId : taskIds) {
            String error = signUpForTask(taskId, name, email);
            if (error != null) {
                errors.add(error);
            } else {
                getTaskById(taskId).ifPresent(succeededTasks::add);
            }
        }
        return new MultiSignUpResult(errors, succeededTasks);
    }

    public static class MultiSignUpResult {
        private final List<String> errors;
        private final List<Task> succeededTasks;

        public MultiSignUpResult(List<String> errors, List<Task> succeededTasks) {
            this.errors = errors;
            this.succeededTasks = succeededTasks;
        }

        public List<String> getErrors() { return errors; }
        public List<Task> getSucceededTasks() { return succeededTasks; }
    }

    public int getTotalVolunteers() {
        return tasks.stream().mapToInt(t -> t.getSignUps().size()).sum();
    }

    public Optional<Task> getTaskById(String taskId) {
        return tasks.stream().filter(t -> t.getId().equals(taskId)).findFirst();
    }

    public List<Task> addTask(String name, String personAssisting, String date, String startTime,
                              String endTime, int maxVolunteers, String recurrenceType, String recurrenceEndDate) {
        if (recurrenceType == null || recurrenceType.isEmpty() || "NONE".equals(recurrenceType)
                || recurrenceEndDate == null || recurrenceEndDate.isEmpty()) {
            Task task = new Task(name, personAssisting, date, startTime, endTime, maxVolunteers);
            tasks.add(task);
            saveToFile();
            return List.of(task);
        }
        List<Task> generated = generateRecurringTasks(name, personAssisting, date, startTime,
                endTime, maxVolunteers, recurrenceType, recurrenceEndDate);
        tasks.addAll(generated);
        saveToFile();
        return generated;
    }

    public boolean updateTask(String taskId, String name, String personAssisting, String date,
                              String startTime, String endTime, int maxVolunteers) {
        Optional<Task> optTask = getTaskById(taskId);
        if (optTask.isEmpty()) return false;
        Task task = optTask.get();
        task.setName(name);
        task.setPersonAssisting(personAssisting);
        task.setDate(date);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setMaxVolunteers(maxVolunteers);
        saveToFile();
        return true;
    }

    public boolean removeTask(String taskId) {
        boolean removed = tasks.removeIf(t -> t.getId().equals(taskId));
        if (removed) {
            saveToFile();
        }
        return removed;
    }

    public String signUpForTask(String taskId, String name, String email) {
        Optional<Task> optTask = getTaskById(taskId);
        if (optTask.isEmpty()) {
            return "Task not found.";
        }
        Task task = optTask.get();
        if (task.isFull()) {
            return "This task is full. No more volunteers can sign up.";
        }
        if (task.hasSignUp(email)) {
            return "You are already signed up for this task.";
        }
        task.getSignUps().add(new SignUp(name, email));
        saveToFile();
        return null;
    }

    public boolean removeSignUp(String taskId, String signUpId) {
        Optional<Task> optTask = getTaskById(taskId);
        if (optTask.isEmpty()) {
            return false;
        }
        boolean removed = optTask.get().getSignUps().removeIf(s -> s.getId().equals(signUpId));
        if (removed) {
            saveToFile();
        }
        return removed;
    }

    private List<Task> generateRecurringTasks(String name, String personAssisting, String startDateStr,
                                               String startTime, String endTime, int maxVolunteers,
                                               String recurrenceType, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        // Cap at 365 days max span
        if (endDate.isAfter(startDate.plusDays(365))) {
            endDate = startDate.plusDays(365);
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date.");
        }

        String groupId = UUID.randomUUID().toString();
        List<Task> result = new ArrayList<>();
        LocalDate current = startDate;
        int occurrence = 1;

        while (!current.isAfter(endDate)) {
            Task task = new Task(name, personAssisting, current.toString(), startTime, endTime, maxVolunteers);
            task.setRecurringGroupId(groupId);
            task.setRecurrenceType(recurrenceType);
            task.setOccurrenceNumber(occurrence);
            result.add(task);
            occurrence++;

            switch (recurrenceType) {
                case "DAILY": current = current.plusDays(1); break;
                case "WEEKLY": current = current.plusWeeks(1); break;
                case "BIWEEKLY": current = current.plusWeeks(2); break;
                case "MONTHLY": current = current.plusMonths(1); break;
                default: return result;
            }
        }
        return result;
    }

    private List<Task> sortTasksByDate(List<Task> taskList) {
        return taskList.stream().sorted((a, b) -> {
            LocalDate dateA = parseDate(a.getDate());
            LocalDate dateB = parseDate(b.getDate());
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            int cmp = dateA.compareTo(dateB);
            if (cmp != 0) return cmp;
            String timeA = a.getStartTime() != null ? a.getStartTime() : "";
            String timeB = b.getStartTime() != null ? b.getStartTime() : "";
            return timeA.compareTo(timeB);
        }).collect(Collectors.toList());
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void seedData() {
        tasks = new ArrayList<>();
        tasks.add(new Task("Setup/Cleanup", "Jane Doe", "2026-03-01", "08:00", "10:00", 10));
        tasks.add(new Task("Registration Desk", "John Smith", "2026-03-01", "09:00", "12:00", 5));
        tasks.add(new Task("Food Service", "Maria Garcia", "2026-03-01", "11:00", "14:00", 8));
    }

    private void loadFromFile() {
        try {
            Map<String, List<Task>> data = objectMapper.readValue(dataFile,
                    new TypeReference<Map<String, List<Task>>>() {});
            tasks = data.getOrDefault("tasks", new ArrayList<>());
        } catch (IOException e) {
            System.err.println("Error reading data file: " + e.getMessage());
            seedData();
        }
    }

    private void saveToFile() {
        try {
            dataFile.getParentFile().mkdirs();
            Map<String, List<Task>> data = Map.of("tasks", tasks);
            objectMapper.writeValue(dataFile, data);
        } catch (IOException e) {
            System.err.println("Error writing data file: " + e.getMessage());
        }
    }
}
