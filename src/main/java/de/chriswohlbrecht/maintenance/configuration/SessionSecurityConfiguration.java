package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.List;

/**
 * Session handling shared by both operating modes ({@link SecurityConfiguration} for portal,
 * {@link HouseholdSecurityConfiguration} for household): the session (Spring Session JDBC) holds the security
 * context, CSRF uses the double-submit cookie {@code XSRF-TOKEN} / header {@code X-XSRF-TOKEN} as Angular
 * expects, and 401/403 are answered as JSON {@code ErrorResponse} without redirects or basic-auth dialog.
 */
@Configuration
@EnableConfigurationProperties({SecurityProperties.class, OperatingModeProperties.class})
public class SessionSecurityConfiguration {

    /** Applies the session, CSRF and error handling common to both filter chains. */
    static void applySessionDefaults(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
                                     SecurityContextRepository securityContextRepository,
                                     SecurityErrorWriter errorWriter) throws Exception {
        http
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Not logged in"))
                        .accessDeniedHandler((request, response, e) ->
                                errorWriter.write(request, response, HttpStatus.FORBIDDEN, "Access denied")))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
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

    /** Also present in household mode, where the login endpoint itself is unavailable. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider loginAuthenticationProvider) {
        return new ProviderManager(loginAuthenticationProvider);
    }
}
