package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDetail;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDictionary;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTask;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the maintenance task of a vehicle from a task of a template. Pure, no Spring dependencies. */
public class TemplateTaskFactory {

    /** Maximum length of a task description (API contract). */
    static final int MAX_DESCRIPTION_LENGTH = 1000;

    /**
     * @param firstRegistrationDate start of the month count for {@code lastDoneDate}
     * @param lastDoneKm            may be {@code null}
     * @param lastDoneDate          may be {@code null}
     */
    public MaintenanceTaskRequest build(CatalogTask task, CatalogDictionary dictionary, Locale locale,
                                        LocalDate firstRegistrationDate, Integer lastDoneKm,
                                        LocalDate lastDoneDate) {
        Integer firstDueKm = task.firstDueKm();
        if (lastDoneKm != null && task.intervalKm() != null) {
            firstDueKm = lastDoneKm + task.intervalKm();
        }
        Integer firstDueMonths = task.firstDueMonths();
        if (lastDoneDate != null && task.intervalMonths() != null) {
            firstDueMonths = (int) ChronoUnit.MONTHS.between(firstRegistrationDate, lastDoneDate)
                    + task.intervalMonths();
        }
        MaintenanceTaskRequest request = new MaintenanceTaskRequest();
        request.setName(TemplateTexts.taskName(task, dictionary, locale));
        request.setDescription(description(task, dictionary, locale));
        request.setIntervalKm(task.intervalKm());
        request.setIntervalMonths(task.intervalMonths());
        request.setFirstDueKm(firstDueKm);
        request.setFirstDueMonths(firstDueMonths);
        request.setOneTime(task.oneTime());
        request.setActive(true);
        return request;
    }

    /** Dictionary text, blank line, one line per detail; whole lines are dropped to stay within the limit. */
    private static String description(CatalogTask task, CatalogDictionary dictionary, Locale locale) {
        List<String> lines = new ArrayList<>();
        String text = TemplateTexts.taskDescription(task, dictionary, locale);
        if (text != null && !text.isBlank()) {
            lines.add(text.strip());
        }
        if (!lines.isEmpty() && !task.details().isEmpty()) {
            lines.add("");
        }
        for (CatalogDetail detail : task.details()) {
            lines.add(detailLine(detail, dictionary, locale));
        }
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            int added = (result.isEmpty() ? 0 : 1) + line.length();
            if (result.length() + added > MAX_DESCRIPTION_LENGTH) {
                break;
            }
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(line);
        }
        if (result.isEmpty() && !lines.isEmpty()) {
            // A single line longer than the limit: cut it rather than lose the text.
            result.append(lines.getFirst(), 0, Math.min(lines.getFirst().length(), MAX_DESCRIPTION_LENGTH));
        }
        String joined = result.toString().stripTrailing();
        return joined.isEmpty() ? null : joined;
    }

    private static String detailLine(CatalogDetail detail, CatalogDictionary dictionary, Locale locale) {
        String line = TemplateTexts.detailLabel(detail, dictionary, locale) + ": " + detail.value();
        return detail.quantity() == null ? line : line + " (" + detail.quantity() + ")";
    }
}
