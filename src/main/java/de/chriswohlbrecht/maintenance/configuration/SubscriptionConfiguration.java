package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SubscriptionProperties.class)
public class SubscriptionConfiguration {
}
