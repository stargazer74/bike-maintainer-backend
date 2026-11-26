package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateRequest;
import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSummaryResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleType;
import de.chriswohlbrecht.maintenance.component.helper.VehicleTemplateNames;
import de.chriswohlbrecht.maintenance.component.helper.TemplateTaskFactory;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogDictionary;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTask;
import de.chriswohlbrecht.maintenance.component.model.template.CatalogTemplate;
import de.chriswohlbrecht.maintenance.exception.InvalidTemplateRequestException;
import de.chriswohlbrecht.maintenance.mapper.VehicleTemplateMapper;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Search by make through an index built once at startup; search terms are never logged. */
@Component
public class VehicleTemplateComponentImpl implements IVehicleTemplateComponent {

    private static final String APPLIED_METRIC = "bm.vehicle_templates.applied";

    private static final Comparator<CatalogTemplate> ORDER = Comparator
            .comparing((CatalogTemplate t) -> t.make().toLowerCase(Locale.ROOT))
            .thenComparing(t -> t.model().toLowerCase(Locale.ROOT))
            .thenComparing(t -> t.years().from());

    private final IVehicleTemplateCatalogComponent catalog;
    private final ICurrentUserComponent currentUserComponent;
    private final IUserLocaleComponent userLocaleComponent;
    private final VehicleTemplateMapper mapper;
    private final IAccessComponent accessComponent;
    private final VehicleRepository vehicleRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final IMaintenanceTaskComponent maintenanceTaskComponent;
    private final TemplateTaskFactory taskFactory = new TemplateTaskFactory();
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Map<String, List<CatalogTemplate>> byMake;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Injected Spring beans are meant to be shared")
    public VehicleTemplateComponentImpl(IVehicleTemplateCatalogComponent catalog,
                                        ICurrentUserComponent currentUserComponent,
                                        IUserLocaleComponent userLocaleComponent, VehicleTemplateMapper mapper,
                                        IAccessComponent accessComponent, VehicleRepository vehicleRepository,
                                        MaintenanceTaskRepository maintenanceTaskRepository,
                                        IMaintenanceTaskComponent maintenanceTaskComponent,
                                        Clock clock, MeterRegistry meterRegistry) {
        this.accessComponent = accessComponent;
        this.vehicleRepository = vehicleRepository;
        this.maintenanceTaskRepository = maintenanceTaskRepository;
        this.maintenanceTaskComponent = maintenanceTaskComponent;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
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

    @Override
    @Transactional
    public Optional<ApplyTemplateResponse> apply(Long vehicleId, ApplyTemplateRequest request) {
        accessComponent.requireWriteAccess();
        Optional<Vehicle> vehicle = vehicleRepository.findByIdAndUser_Id(vehicleId,
                currentUserComponent.getCurrentUserId());
        if (vehicle.isEmpty()) {
            return Optional.empty();
        }
        return catalog.find(request.getTemplateId()).map(template -> apply(vehicle.get(), template, request));
    }

    private ApplyTemplateResponse apply(Vehicle vehicle, CatalogTemplate template, ApplyTemplateRequest request) {
        Map<String, CatalogTask> templateTasks = template.tasks().stream()
                .collect(Collectors.toMap(CatalogTask::key, task -> task));
        validate(vehicle, templateTasks, request);

        Set<String> existingKeys = new HashSet<>();
        for (MaintenanceTask task : maintenanceTaskRepository.findAllByVehicle_Id(vehicle.getId())) {
            if (task.getTemplateKey() != null) {
                existingKeys.add(task.getTemplateKey());
            }
        }
        Locale locale = userLocaleComponent.localeOf(currentUserComponent.getCurrentUser());
        CatalogDictionary dictionary = catalog.dictionary();
        int created = 0;
        for (ApplyTemplateTaskRequest selected : request.getTasks()) {
            if (existingKeys.contains(selected.getKey())) {
                continue;
            }
            maintenanceTaskComponent.createMaintenanceTask(vehicle.getId(),
                    taskFactory.build(templateTasks.get(selected.getKey()), dictionary, locale,
                            vehicle.getFirstRegistrationDate(), selected.getLastDoneKm(),
                            selected.getLastDoneDate()),
                    selected.getKey());
            created++;
        }
        if (created > 0) {
            meterRegistry.counter(APPLIED_METRIC).increment();
        }
        return new ApplyTemplateResponse(created, request.getTasks().size() - created);
    }

    private void validate(Vehicle vehicle, Map<String, CatalogTask> templateTasks, ApplyTemplateRequest request) {
        LocalDate today = LocalDate.now(clock);
        Set<String> seen = new HashSet<>();
        for (ApplyTemplateTaskRequest selected : request.getTasks()) {
            if (!templateTasks.containsKey(selected.getKey())) {
                throw new InvalidTemplateRequestException("Task is not part of the template: " + selected.getKey());
            }
            if (!seen.add(selected.getKey())) {
                throw new InvalidTemplateRequestException("Task selected twice: " + selected.getKey());
            }
            Integer lastDoneKm = selected.getLastDoneKm();
            if (lastDoneKm != null && lastDoneKm > vehicle.getCurrentMileage()) {
                throw new InvalidTemplateRequestException("lastDoneKm is above the current mileage");
            }
            LocalDate lastDoneDate = selected.getLastDoneDate();
            // The server runs in UTC while clients send their local date: tolerate one day so users
            // east of UTC are not rejected shortly after local midnight.
            if (lastDoneDate != null
                    && (lastDoneDate.isAfter(today.plusDays(1)) || lastDoneDate.isBefore(vehicle.getFirstRegistrationDate()))) {
                throw new InvalidTemplateRequestException(
                        "lastDoneDate is in the future or before the first registration");
            }
        }
    }

    private boolean matchesModel(CatalogTemplate template, String wanted) {
        return VehicleTemplateNames.normalize(template.model()).contains(wanted)
                || template.aliases().stream().anyMatch(a -> VehicleTemplateNames.normalize(a).contains(wanted));
    }

    private boolean containsYear(CatalogTemplate template, int year) {
        return year >= template.years().from() && (template.years().to() == null || year <= template.years().to());
    }
}
