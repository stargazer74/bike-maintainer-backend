package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.component.IRateLimitComponent;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Session-based security for the SPA (same origin behind the frontend nginx).
 *
 * <ul>
 *   <li>Login/logout are REST endpoints ({@code AuthController}); the session (Spring Session JDBC) holds
 *       the security context.</li>
 *   <li>CSRF: double-submit cookie {@code XSRF-TOKEN} / header {@code X-XSRF-TOKEN}, as Angular expects.</li>
 *   <li>401/403 are answered as JSON {@code ErrorResponse}; no redirects, no basic-auth dialog.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CsrfTokenRepository csrfTokenRepository,
                                                   SecurityContextRepository securityContextRepository,
                                                   IRateLimitComponent rateLimitComponent,
                                                   JsonMapper jsonMapper) throws Exception {
        SecurityErrorWriter errorWriter = new SecurityErrorWriter(jsonMapper);
        http
                .authorizeHttpRequests(auth -> auth
                        // error dispatches render the original error (e.g. 400), not a 401 on top of it
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Not logged in"))
                        .accessDeniedHandler((request, response, e) ->
                                errorWriter.write(request, response, HttpStatus.FORBIDDEN, "Access denied")))
                .addFilterBefore(new RateLimitFilter(rateLimitComponent, errorWriter), CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        // Readable by JavaScript on purpose: Angular reads XSRF-TOKEN and echoes it as X-XSRF-TOKEN.
        // Secure is derived from the request (https behind the proxy), like the session cookie.
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider loginAuthenticationProvider) {
        return new ProviderManager(loginAuthenticationProvider);
    }
}
