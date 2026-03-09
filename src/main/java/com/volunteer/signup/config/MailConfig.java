package com.volunteer.signup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSenderImpl mailSender() {
        // Intentionally unconfigured at startup.
        // EmailConfigService.init() populates this from email-config.json.
        return new JavaMailSenderImpl();
    }
}
