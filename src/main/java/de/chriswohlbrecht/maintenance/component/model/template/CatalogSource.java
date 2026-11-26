package de.chriswohlbrecht.maintenance.component.model.template;

import java.time.LocalDate;

/**
 * A source of a template.
 *
 * @param id        referenced by tasks and details
 * @param locator   where to find it, e.g. a page
 * @param retrieved date the source was consulted
 */
public record CatalogSource(String id, CatalogSourceKind kind, String title, String url, String locator,
                            LocalDate retrieved) {
}
