package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(InstancioExtension.class)
class MaintenanceReportPdfHelperTest {

    private final MaintenanceReportPdfHelper helper = new MaintenanceReportPdfHelper();

    @Test
    void generate_withEntries_returnsValidPdfDocument() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        vehicle.setType(VehicleType.MOTORCYCLE);
        MaintenanceReportEntry entry = new MaintenanceReportEntry(
                LocalDate.of(2026, 1, 15), 12000, "Ölwechsel durchgeführt", List.of("Ölwechsel", "Bremsen prüfen"));

        byte[] pdf = helper.generate(vehicle, List.of(entry));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void generate_withoutEntries_returnsValidPdfDocument() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        vehicle.setType(VehicleType.CAR);

        byte[] pdf = helper.generate(vehicle, List.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
