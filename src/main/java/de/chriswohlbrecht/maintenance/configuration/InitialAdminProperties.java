package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Initial admin account created by migration V4.
 *
 * @param email    email of the initial admin; only read by the migration when it runs for the first time,
 *                 changing it later does not rename the account
 * @param password initial password (BM_INITIAL_ADMIN_PASSWORD); applied once if the account has none yet
 */
@Validated
@ConfigurationProperties(prefix = "bm.initial-admin")
public record InitialAdminProperties(
        @NotBlank @Email String email,
        String password) {
}
