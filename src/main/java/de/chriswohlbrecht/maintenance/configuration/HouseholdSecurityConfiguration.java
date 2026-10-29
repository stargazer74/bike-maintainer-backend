package de.chriswohlbrecht.maintenance.configuration;

import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Household mode: private operation in a trusted home network without passwords. People pick a profile
 * ({@code /api/v1/profiles/{id}/select}), which starts the same session as a login in portal mode, so all
 * other endpoints work unchanged. Account endpoints of portal mode (login, registration, passwords, admin)
 * answer 404. Profiles are not a security boundary: anyone who can reach the service can use every profile.
 */
@Slf4j
@Configuration
@Conditional(OperatingModeCondition.Household.class)
public class HouseholdSecurityConfiguration {

    private static final String[] PORTAL_ONLY_ENDPOINTS = {
            "/api/v1/auth/challenge",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend",
            "/api/v1/auth/password",
            "/api/v1/auth/password-reset/**",
            "/api/v1/admin/**"
    };

    @Bean
    public SecurityFilterChain householdSecurityFilterChain(HttpSecurity http,
                                                            CsrfTokenRepository csrfTokenRepository,
                                                            SecurityContextRepository securityContextRepository,
                                                            JsonMapper jsonMapper) throws Exception {
        log.warn("Household mode: profiles without passwords, no authentication. "
                + "Run this only in a trusted home network and never expose it to the internet.");
        SecurityErrorWriter errorWriter = new SecurityErrorWriter(jsonMapper);
        SessionSecurityConfiguration.applySessionDefaults(http, csrfTokenRepository, securityContextRepository,
                errorWriter);
        http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/config").permitAll()
                        .requestMatchers("/api/v1/profiles", "/api/v1/profiles/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new UnavailableEndpointFilter(errorWriter, PORTAL_ONLY_ENDPOINTS), CsrfFilter.class);
        return http.build();
    }
}
