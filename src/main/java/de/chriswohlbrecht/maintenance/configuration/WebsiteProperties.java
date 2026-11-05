package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

/**
 * Address of the public website of the portal ({@code bm.website-url}), linked from the app (landing page, legal
 * notice, privacy policy). Portal mode only, see {@link WebsiteConfiguration}; empty means no website. Anything but
 * an http(s) address fails the startup.
 *
 * @param websiteUrl e.g. {@code https://www.example.com}, a trailing slash is ignored
 */
@Validated
@ConfigurationProperties(prefix = "bm")
public record WebsiteProperties(
        @Pattern(regexp = "|https?://[^\\s/?#]+(/[^\\s?#]*)?", message = "must be empty or an http(s) address")
        String websiteUrl) {

    /** The configured address without trailing slashes, empty if none is configured. */
    public Optional<String> url() {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(websiteUrl.replaceAll("/+$", ""));
    }
}
