package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/** The public website only exists in portal mode; household mode ignores {@code bm.website-url} entirely. */
@Configuration(proxyBeanMethods = false)
@Conditional(OperatingModeCondition.Portal.class)
@EnableConfigurationProperties(WebsiteProperties.class)
public class WebsiteConfiguration {
}
