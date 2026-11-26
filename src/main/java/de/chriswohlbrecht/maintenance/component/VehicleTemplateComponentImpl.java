package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSummaryResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleType;
import de.chriswohlbrecht.maintenance.component.helper.VehicleTemplateNames;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTemplate;
import de.chriswohlbrecht.maintenance.mapper.VehicleTemplateMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Search by make through an index built once at startup; search terms are never logged. */
@Component
public class VehicleTemplateComponentImpl implements IVehicleTemplateComponent {

    private static final Comparator<CatalogTemplate> ORDER = Comparator
            .comparing((CatalogTemplate t) -> t.make().toLowerCase(Locale.ROOT))
            .thenComparing(t -> t.model().toLowerCase(Locale.ROOT))
            .thenComparing(t -> t.years().from());

    private final IVehicleTemplateCatalogComponent catalog;
    private final ICurrentUserComponent currentUserComponent;
    private final IUserLocaleComponent userLocaleComponent;
    private final VehicleTemplateMapper mapper;
    private final Map<String, List<CatalogTemplate>> byMake;

    public VehicleTemplateComponentImpl(IVehicleTemplateCatalogComponent catalog,
                                        ICurrentUserComponent currentUserComponent,
                                        IUserLocaleComponent userLocaleComponent, VehicleTemplateMapper mapper) {
        this.catalog = catalog;
        this.currentUserComponent = currentUserComponent;
        this.userLocaleComponent = userLocaleComponent;
        this.mapper = mapper;
        this.byMake = catalog.templates().stream()
                .sorted(ORDER)
                .collect(Collectors.groupingBy(t -> VehicleTemplateNames.normalize(t.make()),
                        Collectors.toUnmodifiableList()));
    }

    @Override
    public List<VehicleTemplateSummaryResponse> search(String make, VehicleType type, String model, Integer year) {
        String wantedModel = VehicleTemplateNames.normalize(model);
        return byMake.getOrDefault(VehicleTemplateNames.normalize(make), List.of()).stream()
                .filter(t -> type == null || t.type() == type)
                .filter(t -> wantedModel.isEmpty() || matchesModel(t, wantedModel))
                .filter(t -> year == null || containsYear(t, year))
                .map(mapper::toSummary)
                .toList();
    }

    @Override
    public Optional<VehicleTemplateResponse> get(String templateId) {
        return catalog.find(templateId).map(template -> {
            Locale locale = userLocaleComponent.localeOf(currentUserComponent.getCurrentUser());
            return mapper.toResponse(template, new VehicleTemplateMapper.Texts(locale, catalog.dictionary()));
        });
    }

    private boolean matchesModel(CatalogTemplate template, String wanted) {
        return VehicleTemplateNames.normalize(template.model()).contains(wanted)
                || template.aliases().stream().anyMatch(a -> VehicleTemplateNames.normalize(a).contains(wanted));
    }

    private boolean containsYear(CatalogTemplate template, int year) {
        return year >= template.years().from() && (template.years().to() == null || year <= template.years().to());
    }
}
