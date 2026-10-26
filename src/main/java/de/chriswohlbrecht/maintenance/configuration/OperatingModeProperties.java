package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param mode operating mode ({@code bm.mode}); an unknown value fails the startup */
@Validated
@ConfigurationProperties(prefix = "bm")
public record OperatingModeProperties(@NotNull OperatingMode mode) {
}
