package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param from        sender address of all emails ({@code BM_MAIL_FROM})
 * @param dailyLimit  emails sent per day at most, all kinds together; protects the mail account against abuse
 * @param linkBaseUrl address of the app the links in emails point to ({@code BM_PUBLIC_URL}), without trailing slash
 */
@Validated
@ConfigurationProperties(prefix = "bm.mail")
public record AccountMailProperties(
        @NotBlank String from,
        @Positive int dailyLimit,
        @NotBlank String linkBaseUrl) {

    public AccountMailProperties {
        while (linkBaseUrl != null && linkBaseUrl.endsWith("/")) {
            linkBaseUrl = linkBaseUrl.substring(0, linkBaseUrl.length() - 1);
        }
    }
}
