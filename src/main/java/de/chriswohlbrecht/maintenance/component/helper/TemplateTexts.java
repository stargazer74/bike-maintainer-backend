package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.component.model.template.CatalogDetail;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDictionary;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTask;
import de.chriswohlbrecht.maintenance.component.model.template.DictionaryTask;

import java.util.Locale;

/** Texts of a template task in one language: the override of the template, else the dictionary. */
public final class TemplateTexts {

    private TemplateTexts() {
    }

    public static String taskName(CatalogTask task, CatalogDictionary dictionary, Locale locale) {
        if (task.name() != null) {
            return task.name().in(locale);
        }
        return dictionaryTask(task, dictionary).name().in(locale);
    }

    /** May be {@code null}. */
    public static String taskDescription(CatalogTask task, CatalogDictionary dictionary, Locale locale) {
        if (task.description() != null) {
            return task.description().in(locale);
        }
        DictionaryTask dictionaryTask = dictionaryTask(task, dictionary);
        return dictionaryTask.description() == null ? null : dictionaryTask.description().in(locale);
    }

    public static String detailLabel(CatalogDetail detail, CatalogDictionary dictionary, Locale locale) {
        return dictionary.details().get(detail.label()).in(locale);
    }

    private static DictionaryTask dictionaryTask(CatalogTask task, CatalogDictionary dictionary) {
        return dictionary.tasks().get(task.key());
    }
}
