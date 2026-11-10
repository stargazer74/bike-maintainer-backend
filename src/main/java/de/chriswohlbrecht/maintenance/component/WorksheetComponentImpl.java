package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.component.helper.ValveLayout;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetPdfHelper;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetTemplates;
import de.chriswohlbrecht.maintenance.component.model.Worksheet;
import de.chriswohlbrecht.maintenance.exception.InvalidWorksheetRequestException;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorksheetComponentImpl implements IWorksheetComponent {

    private final VehicleRepository vehicleRepository;
    private final ICurrentUserComponent currentUserComponent;
    private final IUserLocaleComponent userLocaleComponent;
    private final WorksheetPdfHelper worksheetPdfHelper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Override
    public Optional<List<WorksheetTemplate>> listWorksheetTemplates(Long vehicleId) {
        return findOwnVehicle(vehicleId).map(vehicle -> WorksheetTemplates.availableFor(vehicle.getType()));
    }

    @Override
    public Optional<Worksheet> generateWorksheet(Long vehicleId, WorksheetTemplate template, Integer cylinders,
                                                 Integer valvesPerCylinder) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            if (!WorksheetTemplates.isAvailable(template, vehicle.getType())) {
                throw new InvalidWorksheetRequestException("Worksheet " + template + " is not available for "
                        + vehicle.getType());
            }
            ValveLayout layout = valveLayout(template, cylinders, valvesPerCylinder);
            LocalDate today = LocalDate.now(clock);
            Locale locale = userLocaleComponent.localeOf(vehicle.getUser());
            return new Worksheet(worksheetPdfHelper.fileName(vehicle, template, today, locale),
                    timedGeneration(() -> worksheetPdfHelper.generate(vehicle, template, layout, today, locale)));
        });
    }

    /** Range checks (1–8, 2–4) are done by the bean validation of the API. */
    private static ValveLayout valveLayout(WorksheetTemplate template, Integer cylinders, Integer valvesPerCylinder) {
        if (template != WorksheetTemplate.VALVE_CLEARANCE) {
            if (cylinders != null || valvesPerCylinder != null) {
                throw new InvalidWorksheetRequestException("Valve parameters are only allowed for VALVE_CLEARANCE");
            }
            return null;
        }
        if (cylinders == null || valvesPerCylinder == null) {
            throw new InvalidWorksheetRequestException("cylinders and valvesPerCylinder are required");
        }
        return new ValveLayout(cylinders, valvesPerCylinder);
    }

    private Optional<Vehicle> findOwnVehicle(Long vehicleId) {
        return vehicleRepository.findByIdAndUser_Id(vehicleId, currentUserComponent.getCurrentUserId());
    }

    /** Times the PDF generation like checklist and report ({@code bm.report.generated}, type {@code worksheet}). */
    private byte[] timedGeneration(Supplier<byte[]> generation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "failure";
        try {
            byte[] content = generation.get();
            result = "success";
            return content;
        } finally {
            sample.stop(meterRegistry.timer("bm.report.generated", "type", "worksheet", "result", result));
        }
    }
}
