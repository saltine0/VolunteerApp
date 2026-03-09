package com.volunteer.signup.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailConfig {

    private String smtpHost = "";
    private int smtpPort = 587;
    private String smtpUsername = "";
    private String smtpPassword = "";
    private boolean tlsEnabled = true;
    private String fromAddress = "";

    private int daysBeforeReminder = 1;
    private String reminderTime = "08:00";
    private boolean enabled = false;

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public int getSmtpPort() { return smtpPort; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort; }

    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }

    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }

    public boolean isTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public int getDaysBeforeReminder() { return daysBeforeReminder; }
    public void setDaysBeforeReminder(int daysBeforeReminder) { this.daysBeforeReminder = daysBeforeReminder; }

    public String getReminderTime() { return reminderTime; }
    public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
