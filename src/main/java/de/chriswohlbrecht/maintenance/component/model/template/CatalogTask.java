package de.chriswohlbrecht.maintenance.component.model.template;

import java.util.List;

/**
 * One maintenance task of a template.
 *
 * @param key                   key into {@link CatalogDictionary#tasks()}
 * @param oneTime               never {@code null}
 * @param name                  overrides the name of the dictionary, may be {@code null}
 * @param description           overrides the description of the dictionary, may be {@code null}
 * @param plausibilityException reason for a value outside the plausible range, may be {@code null}
 * @param sources               ids of {@link CatalogSource}
 */
public record CatalogTask(String key, Integer intervalKm, Integer intervalMonths, Integer firstDueKm,
                          Integer firstDueMonths, Boolean oneTime, LocalizedText name, LocalizedText description,
                          List<CatalogDetail> details, String plausibilityException, List<String> sources) {

    public CatalogTask {
        oneTime = Boolean.TRUE.equals(oneTime);
        details = details == null ? List.of() : List.copyOf(details);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
