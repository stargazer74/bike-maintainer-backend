package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDetail;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDictionary;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTask;
import de.chriswohlbrecht.maintenance.component.model.template.DictionaryTask;
import de.chriswohlbrecht.maintenance.component.model.template.LocalizedText;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateTaskFactoryTest {

    private static final LocalDate REGISTRATION = LocalDate.of(2012, 4, 1);

    private static final CatalogDictionary DICTIONARY = new CatalogDictionary(
            Map.of("engine-oil", new DictionaryTask(new LocalizedText("Öl wechseln", "Change oil"),
                            new LocalizedText("Das Öl erneuern.", "Renew the oil."), null),
                    "bare", new DictionaryTask(new LocalizedText("Prüfen", "Check"), null, null)),
            Map.of("oil", new LocalizedText("Öl", "Oil"), "plug", new LocalizedText("Kerze", "Plug")));

    private final TemplateTaskFactory factory = new TemplateTaskFactory();

    private static CatalogTask task(String key, Integer km, Integer months, Integer dueKm, Integer dueMonths,
                                    List<CatalogDetail> details) {
        return new CatalogTask(key, km, months, dueKm, dueMonths, false, null, null, details, null, List.of());
    }

    @Test
    void firstDueKmIsLastDoneKmPlusInterval() {
        CatalogTask task = task("engine-oil", 12000, 24, 1000, 3, List.of());

        MaintenanceTaskRequest request = factory.build(task, DICTIONARY, Locale.GERMANY, REGISTRATION, 38000, null);

        assertThat(request.getFirstDueKm()).isEqualTo(50000);
        assertThat(request.getFirstDueMonths()).isEqualTo(3);
        assertThat(request.getIntervalKm()).isEqualTo(12000);
        assertThat(request.getIntervalMonths()).isEqualTo(24);
        assertThat(request.getActive()).isTrue();
        assertThat(request.getOneTime()).isFalse();
    }

    @Test
    void firstDueMonthsCountsWholeMonthsFromRegistration() {
        CatalogTask task = task("engine-oil", 12000, 24, 1000, 3, List.of());

        MaintenanceTaskRequest request = factory.build(task, DICTIONARY, Locale.GERMANY, REGISTRATION, null,
                LocalDate.of(2025, 3, 15));

        assertThat(request.getFirstDueMonths()).isEqualTo(155 + 24);
        assertThat(request.getFirstDueKm()).isEqualTo(1000);
    }

    @Test
    void axisWithoutMatchingIntervalKeepsTemplateValue() {
        CatalogTask kmOnly = task("engine-oil", 12000, null, 1000, null, List.of());

        MaintenanceTaskRequest request = factory.build(kmOnly, DICTIONARY, Locale.GERMANY, REGISTRATION, null,
                LocalDate.of(2025, 3, 15));

        assertThat(request.getFirstDueKm()).isEqualTo(1000);
        assertThat(request.getFirstDueMonths()).isNull();

        CatalogTask monthsOnly = task("engine-oil", null, 12, null, 6, List.of());
        MaintenanceTaskRequest other = factory.build(monthsOnly, DICTIONARY, Locale.GERMANY, REGISTRATION, 5000,
                null);
        assertThat(other.getFirstDueKm()).isNull();
        assertThat(other.getFirstDueMonths()).isEqualTo(6);
    }

    @Test
    void descriptionHasTextBlankLineAndDetailLines() {
        CatalogTask task = task("engine-oil", 12000, null, null, null, List.of(
                new CatalogDetail("oil", "10W-30, JASO MA", "2,3 l", null),
                new CatalogDetail("plug", "NGK", null, null)));

        assertThat(factory.build(task, DICTIONARY, Locale.GERMANY, REGISTRATION, null, null).getDescription())
                .isEqualTo("Das Öl erneuern.\n\nÖl: 10W-30, JASO MA (2,3 l)\nKerze: NGK");
        assertThat(factory.build(task, DICTIONARY, Locale.UK, REGISTRATION, null, null).getDescription())
                .startsWith("Renew the oil.\n\nOil: ");
        assertThat(factory.build(task, DICTIONARY, Locale.UK, REGISTRATION, null, null).getName())
                .isEqualTo("Change oil");
    }

    @Test
    void descriptionWithoutDictionaryTextHasOnlyDetails() {
        CatalogTask task = task("bare", 1000, null, null, null,
                List.of(new CatalogDetail("plug", "NGK", null, null)));

        assertThat(factory.build(task, DICTIONARY, Locale.GERMANY, REGISTRATION, null, null).getDescription())
                .isEqualTo("Kerze: NGK");
    }

    @Test
    void descriptionIsNullWithoutTextAndDetails() {
        CatalogTask task = task("bare", 1000, null, null, null, List.of());

        assertThat(factory.build(task, DICTIONARY, Locale.GERMANY, REGISTRATION, null, null).getDescription())
                .isNull();
    }

    @Test
    void manyDetailsAreCutAtALineBoundaryWithinTheLimit() {
        List<CatalogDetail> details = IntStream.range(0, 40)
                .mapToObj(i -> new CatalogDetail("oil", "Value number " + i + " ".repeat(10) + "x".repeat(20),
                        "2 l", null))
                .toList();
        CatalogTask task = task("engine-oil", 12000, null, null, null, details);

        String description = factory.build(task, DICTIONARY, Locale.GERMANY, REGISTRATION, null, null)
                .getDescription();

        assertThat(description.length()).isLessThanOrEqualTo(1000);
        assertThat(description).startsWith("Das Öl erneuern.\n\nÖl: Value number 0");
        assertThat(description).endsWith("x".repeat(20) + " (2 l)");
        assertThat(description.lines().count()).isLessThan(42);
        assertThat(description.lines().skip(2)).allMatch(line -> line.startsWith("Öl: Value number "));
    }

    @Test
    void singleOverlongLineIsCutAtTheLimit() {
        CatalogDictionary dictionary = new CatalogDictionary(
                Map.of("long", new DictionaryTask(new LocalizedText("Lang", "Long"),
                        new LocalizedText("a".repeat(1500), "b"), null)), Map.of());

        String description = factory.build(task("long", 1000, null, null, null, List.of()), dictionary,
                Locale.GERMANY, REGISTRATION, null, null).getDescription();

        assertThat(description).hasSize(1000);
    }
}
