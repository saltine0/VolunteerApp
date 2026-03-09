package com.volunteer.signup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.volunteer.signup.model.EmailConfig;
import com.volunteer.signup.model.SignUp;
import com.volunteer.signup.model.Task;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;

@Service
public class EmailConfigService {

    private final ObjectMapper objectMapper;
    private final File configFile;
    private final JavaMailSenderImpl mailSender;
    private final SignUpService signUpService;
    private EmailConfig config;

    public EmailConfigService(
            @Value("${app.email-config-file:src/main/resources/data/email-config.json}") String configFilePath,
            JavaMailSenderImpl mailSender,
            SignUpService signUpService) {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.configFile = new File(configFilePath);
        this.mailSender = mailSender;
        this.signUpService = signUpService;
    }

    @PostConstruct
    public void init() {
        if (configFile.exists() && configFile.length() > 0) {
            loadFromFile();
        } else {
            config = new EmailConfig();
        }
        applyToMailSender();
    }

    public EmailConfig getConfig() {
        return config;
    }

    public synchronized void saveConfig(EmailConfig newConfig) {
        this.config = newConfig;
        saveToFile();
        applyToMailSender();
    }

    private synchronized void applyToMailSender() {
        mailSender.setHost(config.getSmtpHost());
        mailSender.setPort(config.getSmtpPort());
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (config.isTlsEnabled()) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.remove("mail.smtp.ssl.enable");
        } else {
            props.put("mail.smtp.ssl.enable", "true");
            props.remove("mail.smtp.starttls.enable");
            props.remove("mail.smtp.starttls.required");
        }
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
    }

    public void sendTestEmail(String toAddress) throws Exception {
        if (config.getSmtpHost() == null || config.getSmtpHost().isBlank()) {
            throw new IllegalStateException("SMTP host is not configured.");
        }
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(config.getFromAddress().isBlank() ? config.getSmtpUsername() : config.getFromAddress());
        helper.setTo(toAddress);
        helper.setSubject("Volunteer Reminder — Test Email");
        helper.setText("<p>Your email settings are configured correctly!</p>", true);
        mailSender.send(message);
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runReminderJob() {
        if (!config.isEnabled()) return;
        if (config.getSmtpHost() == null || config.getSmtpHost().isBlank()) return;

        int configuredHour = parseReminderHour();
        if (LocalTime.now().getHour() != configuredHour) return;

        LocalDate targetDate = LocalDate.now().plusDays(config.getDaysBeforeReminder());
        String targetDateStr = targetDate.toString();

        List<Task> allTasks = signUpService.getAllTasks();
        for (Task task : allTasks) {
            if (!targetDateStr.equals(task.getDate())) continue;
            if (task.getSignUps().isEmpty()) continue;
            for (SignUp signUp : task.getSignUps()) {
                sendReminderEmail(task, signUp);
            }
        }
    }

    private void sendReminderEmail(Task task, SignUp signUp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(config.getFromAddress().isBlank() ? config.getSmtpUsername() : config.getFromAddress());
            helper.setTo(signUp.getEmail());
            helper.setSubject("Reminder: You're volunteering for \"" + task.getName() + "\" on " + task.getFormattedDate());

            String person = (task.getPersonAssisting() != null && !task.getPersonAssisting().isBlank())
                    ? task.getPersonAssisting() : "—";
            String time = (task.getFormattedTimeRange() != null) ? task.getFormattedTimeRange() : "—";

            String body = "<!DOCTYPE html><html><body style=\"font-family:'Plus Jakarta Sans',Arial,sans-serif;"
                    + "background:#F6F1EB;padding:32px;\">"
                    + "<div style=\"max-width:520px;margin:0 auto;background:#FFFDF9;border-radius:12px;"
                    + "border:1px solid #E8E2DA;padding:32px;\">"
                    + "<h2 style=\"color:#C2593B;margin-top:0;\">Volunteer Reminder</h2>"
                    + "<p>Hi " + escapeHtml(signUp.getName()) + ",</p>"
                    + "<p>This is a friendly reminder that you're signed up to volunteer for:</p>"
                    + "<table style=\"width:100%;border-collapse:collapse;margin:20px 0;\">"
                    + "<tr><td style=\"color:#8A7E76;padding:6px 0;width:130px;\">Task</td>"
                    + "<td style=\"font-weight:600;\">" + escapeHtml(task.getName()) + "</td></tr>"
                    + "<tr><td style=\"color:#8A7E76;padding:6px 0;\">Date</td>"
                    + "<td>" + escapeHtml(task.getFormattedDate()) + "</td></tr>"
                    + "<tr><td style=\"color:#8A7E76;padding:6px 0;\">Time</td>"
                    + "<td>" + escapeHtml(time) + "</td></tr>"
                    + "<tr><td style=\"color:#8A7E76;padding:6px 0;\">Coordinator</td>"
                    + "<td>" + escapeHtml(person) + "</td></tr>"
                    + "</table>"
                    + "<p style=\"color:#5B7B6A;\">Thank you for volunteering!</p>"
                    + "</div></body></html>";

            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send reminder to " + signUp.getEmail() + ": " + e.getMessage());
        }
    }

    private int parseReminderHour() {
        try {
            String time = config.getReminderTime();
            if (time == null || time.isBlank()) return 8;
            return Integer.parseInt(time.split(":")[0]);
        } catch (Exception e) {
            return 8;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void loadFromFile() {
        try {
            config = objectMapper.readValue(configFile, EmailConfig.class);
        } catch (IOException e) {
            System.err.println("Error reading email config file: " + e.getMessage());
            config = new EmailConfig();
        }
    }

    private void saveToFile() {
        try {
            configFile.getParentFile().mkdirs();
            objectMapper.writeValue(configFile, config);
        } catch (IOException e) {
            System.err.println("Error writing email config file: " + e.getMessage());
        }
    }
}
