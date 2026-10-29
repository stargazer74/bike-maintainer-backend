package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled}, used for the daily cleanup of registration data. */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
