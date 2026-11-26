package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Vehicle template catalog; available in both operating modes. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VehicleTemplateProperties.class)
public class VehicleTemplateConfiguration {
}
