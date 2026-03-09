package com.volunteer.signup.controller;

import com.volunteer.signup.model.Task;
import com.volunteer.signup.model.TaskDisplayItem;
import com.volunteer.signup.service.EmailConfigService;
import com.volunteer.signup.service.SignUpService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

@Controller
public class SignUpController {

    private final SignUpService signUpService;
    private final EmailConfigService emailConfigService;

    public SignUpController(SignUpService signUpService, EmailConfigService emailConfigService) {
        this.signUpService = signUpService;
        this.emailConfigService = emailConfigService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false, defaultValue = "") String filterStartDate,
                        @RequestParam(required = false, defaultValue = "") String filterEndDate,
                        Model model,
                        Principal principal) {
        List<TaskDisplayItem> taskItems;
        if (!filterStartDate.isEmpty() || !filterEndDate.isEmpty()) {
            taskItems = signUpService.getTaskDisplayItemsByDateRange(filterStartDate, filterEndDate);
        } else {
            taskItems = signUpService.getTaskDisplayItems();
        }
        model.addAttribute("taskItems", taskItems);
        model.addAttribute("taskCount", signUpService.getAllTasks().size());
        model.addAttribute("totalVolunteers", signUpService.getTotalVolunteers());
        model.addAttribute("filterStartDate", filterStartDate);
        model.addAttribute("filterEndDate", filterEndDate);
        model.addAttribute("emailConfig", emailConfigService.getConfig());
        if (principal instanceof OAuth2AuthenticationToken token) {
            Object user = token.getPrincipal();
            if (user instanceof OidcUser oidcUser) {
                String name = oidcUser.getEmail();
                if (name == null) name = oidcUser.getFullName();
                if (name == null) name = oidcUser.getSubject();
                model.addAttribute("userName", name);
            } else {
                model.addAttribute("userName", principal.getName());
            }
        }
        return "index";
    }

    @PostMapping("/tasks/add")
    public String addTask(@RequestParam String name,
                          @RequestParam(required = false, defaultValue = "") String personAssisting,
                          @RequestParam(required = false, defaultValue = "") String date,
                          @RequestParam(required = false, defaultValue = "") String startTime,
                          @RequestParam(required = false, defaultValue = "") String endTime,
                          @RequestParam(required = false, defaultValue = "0") int maxVolunteers,
                          @RequestParam(required = false, defaultValue = "NONE") String recurrenceType,
                          @RequestParam(required = false, defaultValue = "") String recurrenceEndDate,
                          RedirectAttributes redirectAttributes) {
        try {
            List<Task> created = signUpService.addTask(name, personAssisting, date, startTime,
                    endTime, maxVolunteers, recurrenceType, recurrenceEndDate);
            if (created.size() == 1) {
                redirectAttributes.addFlashAttribute("message", "Task added successfully!");
            } else {
                redirectAttributes.addFlashAttribute("message", created.size() + " recurring tasks created!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating task: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/tasks/update")
    public String updateTask(@RequestParam String taskId,
                             @RequestParam String name,
                             @RequestParam(required = false, defaultValue = "") String personAssisting,
                             @RequestParam(required = false, defaultValue = "") String date,
                             @RequestParam(required = false, defaultValue = "") String startTime,
                             @RequestParam(required = false, defaultValue = "") String endTime,
                             @RequestParam(required = false, defaultValue = "0") int maxVolunteers,
                             RedirectAttributes redirectAttributes) {
        boolean updated = signUpService.updateTask(taskId, name, personAssisting, date, startTime, endTime, maxVolunteers);
        if (updated) {
            redirectAttributes.addFlashAttribute("message", "Task updated successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Could not update task.");
        }
        return "redirect:/";
    }

    @PostMapping("/tasks/remove")
    public String removeTask(@RequestParam String taskId,
                             RedirectAttributes redirectAttributes) {
        boolean removed = signUpService.removeTask(taskId);
        if (removed) {
            redirectAttributes.addFlashAttribute("message", "Task removed successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Could not remove task.");
        }
        return "redirect:/";
    }

    @PostMapping("/signup")
    public String signUp(@RequestParam String taskId,
                         @RequestParam String signUpName,
                         @RequestParam String signUpEmail,
                         RedirectAttributes redirectAttributes) {
        String error = signUpService.signUpForTask(taskId, signUpName, signUpEmail);
        if (error == null) {
            Optional<Task> task = signUpService.getTaskById(taskId);
            String msg = "Signed up successfully!";
            if (task.isPresent()) {
                Task t = task.get();
                StringBuilder sb = new StringBuilder("Signed up successfully for ");
                if (t.getFormattedDate() != null) sb.append(t.getFormattedDate());
                if (t.getFormattedTimeRange() != null) sb.append(" (").append(t.getFormattedTimeRange()).append(")");
                msg = sb.toString();
            }
            redirectAttributes.addFlashAttribute("message", msg);
        } else {
            redirectAttributes.addFlashAttribute("error", error);
        }
        return "redirect:/";
    }

    @PostMapping("/signup/multiple")
    public String signUpMultiple(@RequestParam List<String> taskIds,
                                 @RequestParam String signUpName,
                                 @RequestParam String signUpEmail,
                                 RedirectAttributes redirectAttributes) {
        if (taskIds == null || taskIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select at least one occurrence.");
            return "redirect:/";
        }
        SignUpService.MultiSignUpResult result = signUpService.signUpForMultipleTasks(taskIds, signUpName, signUpEmail);
        if (result.getErrors().isEmpty()) {
            String details = result.getSucceededTasks().stream().map(t -> {
                StringBuilder sb = new StringBuilder();
                if (t.getFormattedDate() != null) sb.append(t.getFormattedDate());
                if (t.getFormattedStartTime() != null) sb.append(" (").append(t.getFormattedStartTime()).append(")");
                return sb.toString();
            }).collect(Collectors.joining(", "));
            redirectAttributes.addFlashAttribute("message", "Signed up successfully for: " + details);
        } else {
            redirectAttributes.addFlashAttribute("error", String.join("; ", result.getErrors()));
        }
        return "redirect:/";
    }

    @PostMapping("/signup/remove")
    public String removeSignUp(@RequestParam String taskId,
                               @RequestParam String signUpId,
                               RedirectAttributes redirectAttributes) {
        boolean removed = signUpService.removeSignUp(taskId, signUpId);
        if (removed) {
            redirectAttributes.addFlashAttribute("message", "Sign-up removed.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Could not remove sign-up.");
        }
        return "redirect:/";
    }
}
