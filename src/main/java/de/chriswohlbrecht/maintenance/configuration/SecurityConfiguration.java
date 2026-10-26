package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.component.IRateLimitComponent;
import jakarta.servlet.DispatcherType;
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
 * Portal mode (default): accounts with email and password for internet operation. Login/logout are REST
 * endpoints ({@code AuthController}); session and CSRF handling see {@link SessionSecurityConfiguration}.
 * The profile endpoints of household mode answer 404.
 */
@Configuration
@Conditional(OperatingModeCondition.Portal.class)
public class SecurityConfiguration {

    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm"
    };

    private static final String[] HOUSEHOLD_ONLY_ENDPOINTS = {
            "/api/v1/profiles",
            "/api/v1/profiles/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CsrfTokenRepository csrfTokenRepository,
                                                   SecurityContextRepository securityContextRepository,
                                                   IRateLimitComponent rateLimitComponent,
                                                   JsonMapper jsonMapper) throws Exception {
        SecurityErrorWriter errorWriter = new SecurityErrorWriter(jsonMapper);
        SessionSecurityConfiguration.applySessionDefaults(http, csrfTokenRepository, securityContextRepository,
                errorWriter);
        http
                .authorizeHttpRequests(auth -> auth
                        // error dispatches render the original error (e.g. 400), not a 401 on top of it
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/config").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new UnavailableEndpointFilter(errorWriter, HOUSEHOLD_ONLY_ENDPOINTS), CsrfFilter.class)
                .addFilterBefore(new RateLimitFilter(rateLimitComponent, errorWriter), CsrfFilter.class);
        return http.build();
    }
}
