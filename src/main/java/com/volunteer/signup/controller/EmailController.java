package com.volunteer.signup.controller;

import com.volunteer.signup.model.EmailConfig;
import com.volunteer.signup.service.EmailConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EmailController {

    private final EmailConfigService emailConfigService;

    public EmailController(EmailConfigService emailConfigService) {
        this.emailConfigService = emailConfigService;
    }

    @PostMapping("/email/config/save")
    public String saveEmailConfig(
            @RequestParam(defaultValue = "") String smtpHost,
            @RequestParam(defaultValue = "587") int smtpPort,
            @RequestParam(defaultValue = "") String smtpUsername,
            @RequestParam(defaultValue = "") String smtpPassword,
            @RequestParam(defaultValue = "false") boolean tlsEnabled,
            @RequestParam(defaultValue = "") String fromAddress,
            @RequestParam(defaultValue = "1") int daysBeforeReminder,
            @RequestParam(defaultValue = "08:00") String reminderTime,
            @RequestParam(defaultValue = "false") boolean enabled,
            RedirectAttributes redirectAttributes) {
        try {
            EmailConfig cfg = new EmailConfig();
            cfg.setSmtpHost(smtpHost);
            cfg.setSmtpPort(smtpPort);
            cfg.setSmtpUsername(smtpUsername);
            // Preserve existing password if form field left blank
            cfg.setSmtpPassword(smtpPassword.isBlank()
                    ? emailConfigService.getConfig().getSmtpPassword()
                    : smtpPassword);
            cfg.setTlsEnabled(tlsEnabled);
            cfg.setFromAddress(fromAddress);
            cfg.setDaysBeforeReminder(daysBeforeReminder);
            cfg.setReminderTime(reminderTime);
            cfg.setEnabled(enabled);
            emailConfigService.saveConfig(cfg);
            redirectAttributes.addFlashAttribute("message", "Email settings saved successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error saving email settings: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/email/test")
    public String sendTestEmail(
            @RequestParam String testEmailAddress,
            RedirectAttributes redirectAttributes) {
        try {
            emailConfigService.sendTestEmail(testEmailAddress);
            redirectAttributes.addFlashAttribute("message", "Test email sent to " + testEmailAddress + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Test email failed: " + e.getMessage());
        }
        return "redirect:/";
    }
}
