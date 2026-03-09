package com.volunteer.signup.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${auth0.domain}")
    private String auth0Domain;

    @Value("${auth0.client-id}")
    private String auth0ClientId;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/signup", "/signup/multiple").permitAll()
                .requestMatchers("/tasks/add", "/tasks/update", "/tasks/remove", "/signup/remove",
                                 "/email/config/save", "/email/test").authenticated()
                .anyRequest().permitAll()
            )
            .csrf(csrf -> {
                CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
                handler.setCsrfRequestAttributeName(null); // force eager loading of token
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(handler);
            })
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .addLogoutHandler(auth0LogoutHandler())
            );

        return http.build();
    }

    private LogoutHandler auth0LogoutHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            String returnTo = URLEncoder.encode(
                request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/",
                StandardCharsets.UTF_8
            );
            String logoutUrl = String.format(
                "https://%s/v2/logout?client_id=%s&returnTo=%s",
                auth0Domain, auth0ClientId, returnTo
            );
            try {
                response.sendRedirect(logoutUrl);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
