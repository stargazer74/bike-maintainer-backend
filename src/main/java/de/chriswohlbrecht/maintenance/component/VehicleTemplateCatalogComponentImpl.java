package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.template.CatalogDetail;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDictionary;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogSource;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTask;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTemplate;
import de.chriswohlbrecht.maintenance.configuration.VehicleTemplateProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Reads the catalog once in the constructor and fails the startup with one message listing every problem (file and
 * reason) if a file does not fit the format, a task key, detail label or source reference is unknown, an id is used
 * twice or there is no template at all.
 */
@Slf4j
@Component
public class VehicleTemplateCatalogComponentImpl implements IVehicleTemplateCatalogComponent {

    private static final String DICTIONARY_FILE = "tasks.yaml";

    private final YAMLMapper mapper = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final List<CatalogTemplate> templates;
    private final Map<String, CatalogTemplate> templatesById;
    private final CatalogDictionary dictionary;

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW",
            justification = "An invalid catalog must fail the startup; the bean is never used then")
    public VehicleTemplateCatalogComponentImpl(VehicleTemplateProperties properties) {
        List<String> problems = new ArrayList<>();
        String location = properties.location().endsWith("/") ? properties.location() : properties.location() + "/";
        String marker = "/" + location.substring(location.indexOf(':') + 1);
        Resource[] resources = findResources(location, problems);

        CatalogDictionary loadedDictionary = null;
        Map<String, CatalogTemplate> byId = new TreeMap<>();
        List<Resource> templateResources = new ArrayList<>();
        Map<Resource, String> names = new HashMap<>();
        for (Resource resource : resources) {
            String name = relativeName(resource, marker);
            names.put(resource, name);
            if (DICTIONARY_FILE.equals(name)) {
                loadedDictionary = read(resource, name, CatalogDictionary.class, problems);
            } else {
                templateResources.add(resource);
            }
        }
        if (loadedDictionary == null) {
            problems.add(DICTIONARY_FILE + ": missing or unreadable");
            loadedDictionary = new CatalogDictionary(Map.of(), Map.of());
        }
        for (Resource resource : templateResources) {
            String name = names.get(resource);
            CatalogTemplate template = read(resource, name, CatalogTemplate.class, problems);
            if (template == null) {
                continue;
            }
            int before = problems.size();
            check(template, loadedDictionary, name, problems);
            if (problems.size() == before) {
                CatalogTemplate previous = byId.putIfAbsent(template.id(), template);
                if (previous != null) {
                    problems.add(name + ": duplicate id " + template.id());
                }
            }
        }
        if (templateResources.isEmpty()) {
            problems.add("no templates found at " + location);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid vehicle template catalog:\n"
                    + String.join("\n", problems));
        }
        this.dictionary = loadedDictionary;
        this.templatesById = Map.copyOf(byId);
        this.templates = byId.values().stream().sorted(Comparator.comparing(CatalogTemplate::id)).toList();
        log.info("Vehicle template catalog loaded: {} templates", templates.size());
    }

    @Override
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The list is immutable")
    public List<CatalogTemplate> templates() {
        return templates;
    }

    @Override
    public Optional<CatalogTemplate> find(String id) {
        return Optional.ofNullable(templatesById.get(id));
    }

    @Override
    public CatalogDictionary dictionary() {
        return dictionary;
    }

    private Resource[] findResources(String location, List<String> problems) {
        try {
            return new PathMatchingResourcePatternResolver().getResources(location + "**/*.yaml");
        } catch (IOException e) {
            problems.add(location + ": " + e.getMessage());
            return new Resource[0];
        }
    }

    /** Path of the resource below the catalog directory, e.g. {@code motorcycle/honda/cb650r-2019.yaml}. */
    private String relativeName(Resource resource, String marker) {
        try {
            String url = resource.getURL().toString();
            int index = url.lastIndexOf(marker);
            return index < 0 ? url : url.substring(index + marker.length());
        } catch (IOException e) {
            return String.valueOf(resource.getFilename());
        }
    }

    private <T> T read(Resource resource, String name, Class<T> type, List<String> problems) {
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, type);
        } catch (JacksonException e) {
            problems.add(name + ": " + e.getOriginalMessage());
        } catch (IOException e) {
            problems.add(name + ": cannot be read: " + e.getMessage());
        }
        return null;
    }

    private void check(CatalogTemplate template, CatalogDictionary dict, String name, List<String> problems) {
        requireText(template.id(), "id", name, problems);
        requireText(template.make(), "make", name, problems);
        requireText(template.model(), "model", name, problems);
        if (template.type() == null) {
            problems.add(name + ": type is missing");
        }
        if (template.years() == null || template.years().from() == null) {
            problems.add(name + ": years.from is missing");
        }
        if (template.sources().isEmpty()) {
            problems.add(name + ": no sources");
        }
        if (template.tasks().isEmpty()) {
            problems.add(name + ": no tasks");
        }
        Set<String> sourceIds = template.sources().stream().map(CatalogSource::id)
                .collect(Collectors.toCollection(HashSet::new));
        for (CatalogTask task : template.tasks()) {
            if (!dict.tasks().containsKey(task.key())) {
                problems.add(name + ": unknown task key " + task.key());
            }
            checkSourceRefs(task.sources(), sourceIds, name, "task " + task.key(), problems);
            for (CatalogDetail detail : task.details()) {
                if (!dict.details().containsKey(detail.label())) {
                    problems.add(name + ": unknown detail label " + detail.label());
                }
                checkSourceRefs(detail.sources(), sourceIds, name, "detail " + detail.label(), problems);
            }
        }
    }

    private void checkSourceRefs(List<String> refs, Set<String> sourceIds, String name, String owner,
                                 List<String> problems) {
        if (refs.isEmpty()) {
            problems.add(name + ": " + owner + " has no sources");
        }
        for (String ref : refs) {
            if (!sourceIds.contains(ref)) {
                problems.add(name + ": " + owner + " references unknown source " + ref);
            }
        }
    }

    private void requireText(String value, String field, String name, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(name + ": " + field + " is missing");
        }
    }
}
