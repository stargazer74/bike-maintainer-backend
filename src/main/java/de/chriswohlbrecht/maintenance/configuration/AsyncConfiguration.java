package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async}, used for sending emails outside the request. */
@Configuration
@EnableAsync
public class AsyncConfiguration {
}
