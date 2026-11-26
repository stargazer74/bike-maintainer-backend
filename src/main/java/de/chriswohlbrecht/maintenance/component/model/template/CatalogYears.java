package de.chriswohlbrecht.maintenance.component.model.template;

/**
 * Model years, both inclusive.
 *
 * @param from first model year
 * @param to   last model year, {@code null} means until today
 */
public record CatalogYears(Integer from, Integer to) {
}
