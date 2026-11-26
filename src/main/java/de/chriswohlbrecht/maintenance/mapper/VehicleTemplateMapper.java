package de.chriswohlbrecht.maintenance.mapper;

import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateDetailResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSourceQuality;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSourceResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSummaryResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateTaskResponse;
import de.chriswohlbrecht.maintenance.component.helper.TemplateTexts;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDetail;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDictionary;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogSource;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogSourceKind;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTask;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTemplate;
import de.chriswohlbrecht.maintenance.component.model.template.LocalizedText;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Locale;

/** Catalog records to API models; texts come in the language of the {@link Texts} context. */
@Mapper(componentModel = "spring")
public interface VehicleTemplateMapper {

    /** Language and dictionary needed to resolve texts. */
    record Texts(Locale locale, CatalogDictionary dictionary) {
    }

    @Mapping(target = "yearFrom", source = "years.from")
    @Mapping(target = "yearTo", source = "years.to")
    @Mapping(target = "taskCount", expression = "java(template.tasks().size())")
    @Mapping(target = "sourceQuality", expression = "java(sourceQuality(template))")
    VehicleTemplateSummaryResponse toSummary(CatalogTemplate template);

    @Mapping(target = "yearFrom", source = "years.from")
    @Mapping(target = "yearTo", source = "years.to")
    @Mapping(target = "taskCount", expression = "java(template.tasks().size())")
    @Mapping(target = "sourceQuality", expression = "java(sourceQuality(template))")
    @Mapping(target = "notes", expression = "java(text(template.notes(), texts))")
    VehicleTemplateResponse toResponse(CatalogTemplate template, @Context Texts texts);

    VehicleTemplateSourceResponse toSource(CatalogSource source);

    @Mapping(target = "name", expression = "java(taskName(task, texts))")
    @Mapping(target = "description", expression = "java(taskDescription(task, texts))")
    @Mapping(target = "sourceIds", source = "sources")
    VehicleTemplateTaskResponse toTask(CatalogTask task, @Context Texts texts);

    @Mapping(target = "label", expression = "java(detailLabel(detail, texts))")
    @Mapping(target = "sourceIds", source = "sources")
    VehicleTemplateDetailResponse toDetail(CatalogDetail detail, @Context Texts texts);

    List<VehicleTemplateTaskResponse> toTasks(List<CatalogTask> tasks, @Context Texts texts);

    /** Secondary as soon as one task has no manufacturer source. */
    default VehicleTemplateSourceQuality sourceQuality(CatalogTemplate template) {
        List<String> manufacturerIds = template.sources().stream()
                .filter(source -> source.kind() == CatalogSourceKind.MANUFACTURER)
                .map(CatalogSource::id)
                .toList();
        boolean allBacked = template.tasks().stream()
                .allMatch(task -> task.sources().stream().anyMatch(manufacturerIds::contains));
        return allBacked ? VehicleTemplateSourceQuality.MANUFACTURER : VehicleTemplateSourceQuality.SECONDARY;
    }

    default String text(LocalizedText text, Texts texts) {
        return text == null ? null : text.in(texts.locale());
    }

    default String taskName(CatalogTask task, Texts texts) {
        return TemplateTexts.taskName(task, texts.dictionary(), texts.locale());
    }

    default String taskDescription(CatalogTask task, Texts texts) {
        return TemplateTexts.taskDescription(task, texts.dictionary(), texts.locale());
    }

    default String detailLabel(CatalogDetail detail, Texts texts) {
        return TemplateTexts.detailLabel(detail, texts.dictionary(), texts.locale());
    }
}
