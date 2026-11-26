package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the vehicle template catalog is read from ({@code bm.templates.location}): a resource location whose
 * directory holds {@code tasks.yaml} and the templates below it. The default is the catalog inside the
 * {@code vehicle-templates} artifact.
 *
 * @param location resource location, e.g. {@code classpath*:vehicle-templates/}
 */
@Validated
@ConfigurationProperties(prefix = "bm.templates")
public record VehicleTemplateProperties(String location) {

    public static final String DEFAULT_LOCATION = "classpath*:vehicle-templates/";

    public VehicleTemplateProperties {
        if (location == null || location.isBlank()) {
            location = DEFAULT_LOCATION;
        }
    }
}
