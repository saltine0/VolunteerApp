package com.volunteer.signup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {MailSenderAutoConfiguration.class})
@EnableScheduling
public class VolunteerSignUpApplication {

    public static void main(String[] args) {
        SpringApplication.run(VolunteerSignUpApplication.class, args);
    }
}
