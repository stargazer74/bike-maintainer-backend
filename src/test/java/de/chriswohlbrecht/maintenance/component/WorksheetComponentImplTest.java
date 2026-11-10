package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.component.helper.ValveLayout;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetPdfHelper;
import de.chriswohlbrecht.maintenance.component.model.Worksheet;
import de.chriswohlbrecht.maintenance.component.helper.WorksheetPrefill;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MeasurementValve;
import de.chriswohlbrecht.maintenance.persistence.model.VehicleSpecification;
import de.chriswohlbrecht.maintenance.persistence.repository.MeasurementValveRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleSpecificationRepository;
import de.chriswohlbrecht.maintenance.persistence.type.ValveKind;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.Map;
import de.chriswohlbrecht.maintenance.exception.InvalidWorksheetRequestException;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class WorksheetComponentImplTest {

    private static final Long USER_ID = 99L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneId.of("Europe/Berlin"));

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ICurrentUserComponent currentUserComponent;

    @Mock
    private IUserLocaleComponent userLocaleComponent;

    @Mock
    private WorksheetPdfHelper worksheetPdfHelper;

    @Mock
    private VehicleSpecificationRepository vehicleSpecificationRepository;

    @Mock
    private MeasurementValveRepository measurementValveRepository;

    private SimpleMeterRegistry meterRegistry;

    private WorksheetComponentImpl worksheetComponent;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient().when(currentUserComponent.getCurrentUserId()).thenReturn(USER_ID);
        worksheetComponent = new WorksheetComponentImpl(vehicleRepository, vehicleSpecificationRepository,
                measurementValveRepository, currentUserComponent, userLocaleComponent, worksheetPdfHelper, CLOCK,
                meterRegistry);
    }

    @Test
    void listWorksheetTemplates_ofABicycle_offersNoValveClearance() {
        Vehicle vehicle = ownVehicle(VehicleType.BICYCLE);

        assertThat(worksheetComponent.listWorksheetTemplates(vehicle.getId()))
                .contains(List.of(WorksheetTemplate.BRAKES, WorksheetTemplate.CHAIN, WorksheetTemplate.TIRES));
    }

    @Test
    void listWorksheetTemplates_ofAForeignVehicle_isEmpty() {
        assertThat(worksheetComponent.listWorksheetTemplates(5L)).isEmpty();
    }

    @Test
    void generateWorksheet_valveClearance_rendersWithLayoutDateAndOwnersLocale() {
        Vehicle vehicle = ownVehicle(VehicleType.MOTORCYCLE);
        when(userLocaleComponent.localeOf(vehicle.getUser())).thenReturn(Locale.UK);
        when(worksheetPdfHelper.fileName(vehicle, WorksheetTemplate.VALVE_CLEARANCE, TODAY, Locale.UK))
                .thenReturn("worksheet.pdf");
        when(worksheetPdfHelper.generate(ArgumentMatchers.eq(vehicle),
                ArgumentMatchers.eq(WorksheetTemplate.VALVE_CLEARANCE), ArgumentMatchers.eq(new ValveLayout(2, 4)),
                any(), ArgumentMatchers.eq(TODAY), ArgumentMatchers.eq(Locale.UK))).thenReturn(new byte[]{1, 2});

        Optional<Worksheet> result = worksheetComponent.generateWorksheet(vehicle.getId(),
                WorksheetTemplate.VALVE_CLEARANCE, 2, 4);

        assertThat(result).isPresent();
        assertThat(result.get().fileName()).isEqualTo("worksheet.pdf");
        assertThat(result.get().content()).containsExactly(1, 2);
        assertThat(meterRegistry.timer("bm.report.generated", "type", "worksheet", "result", "success").count())
                .isEqualTo(1);
    }

    @Test
    void generateWorksheet_otherTemplate_rendersWithoutLayout() {
        Vehicle vehicle = ownVehicle(VehicleType.CAR);
        when(worksheetPdfHelper.generate(ArgumentMatchers.eq(vehicle), ArgumentMatchers.eq(WorksheetTemplate.TIRES),
                ArgumentMatchers.isNull(), any(), ArgumentMatchers.eq(TODAY), any())).thenReturn(new byte[]{3});

        assertThat(worksheetComponent.generateWorksheet(vehicle.getId(), WorksheetTemplate.TIRES, null, null))
                .isPresent();
    }

    @Test
    void generateWorksheet_failingRendering_isCountedAsFailure() {
        Vehicle vehicle = ownVehicle(VehicleType.CAR);
        when(worksheetPdfHelper.generate(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> worksheetComponent.generateWorksheet(vehicle.getId(), WorksheetTemplate.BRAKES,
                null, null)).isInstanceOf(IllegalStateException.class);
        assertThat(meterRegistry.timer("bm.report.generated", "type", "worksheet", "result", "failure").count())
                .isEqualTo(1);
    }

    @Test
    void generateWorksheet_templateNotForTheVehicleType_isRejected() {
        Vehicle vehicle = ownVehicle(VehicleType.CAR);

        assertThatThrownBy(() -> worksheetComponent.generateWorksheet(vehicle.getId(), WorksheetTemplate.CHAIN,
                null, null)).isInstanceOf(InvalidWorksheetRequestException.class);
        verifyNoInteractions(worksheetPdfHelper);
    }

    @Test
    void generateWorksheet_valveClearanceWithoutParameters_isRejected() {
        Vehicle vehicle = ownVehicle(VehicleType.MOTORCYCLE);

        assertThatThrownBy(() -> worksheetComponent.generateWorksheet(vehicle.getId(),
                WorksheetTemplate.VALVE_CLEARANCE, 2, null)).isInstanceOf(InvalidWorksheetRequestException.class);
        assertThatThrownBy(() -> worksheetComponent.generateWorksheet(vehicle.getId(),
                WorksheetTemplate.VALVE_CLEARANCE, null, 4)).isInstanceOf(InvalidWorksheetRequestException.class);
    }

    @Test
    void generateWorksheet_valveParametersForAnotherTemplate_areRejected() {
        Vehicle vehicle = ownVehicle(VehicleType.MOTORCYCLE);

        assertThatThrownBy(() -> worksheetComponent.generateWorksheet(vehicle.getId(), WorksheetTemplate.BRAKES,
                2, null)).isInstanceOf(InvalidWorksheetRequestException.class);
        assertThatThrownBy(() -> worksheetComponent.generateWorksheet(vehicle.getId(), WorksheetTemplate.BRAKES,
                null, 4)).isInstanceOf(InvalidWorksheetRequestException.class);
    }

    @Test
    void generateWorksheet_foreignVehicle_isEmptyAndRendersNothing() {
        assertThat(worksheetComponent.generateWorksheet(5L, WorksheetTemplate.BRAKES, null, null)).isEmpty();
        verify(vehicleRepository).findByIdAndUser_Id(5L, USER_ID);
        verifyNoInteractions(worksheetPdfHelper);
    }

    @Test
    void generateWorksheet_valveClearance_prefillsTargetsAndTheShimsOfTheLatestMeasurement() {
        Vehicle vehicle = ownVehicle(VehicleType.MOTORCYCLE);
        VehicleSpecification spec = VehicleSpecification.builder().vehicleId(vehicle.getId()).build();
        when(vehicleSpecificationRepository.findById(vehicle.getId())).thenReturn(Optional.of(spec));
        MaintenanceLog older = MaintenanceLog.builder().id(1L).performedAt(LocalDate.of(2025, 5, 1)).build();
        MaintenanceLog newer = MaintenanceLog.builder().id(2L).performedAt(LocalDate.of(2026, 5, 1)).build();
        when(measurementValveRepository.findAllByLog_Vehicle_Id(vehicle.getId())).thenReturn(List.of(
                valve(older, 1, ValveKind.INTAKE, "1.800", "1.750"),
                valve(newer, 1, ValveKind.INTAKE, "1.750", null),
                valve(newer, 1, ValveKind.EXHAUST, "2.000", "1.975"),
                valve(newer, 2, ValveKind.INTAKE, null, null)));
        ArgumentCaptor<WorksheetPrefill> prefill = ArgumentCaptor.forClass(WorksheetPrefill.class);
        when(worksheetPdfHelper.generate(any(), any(), any(), prefill.capture(), any(), any()))
                .thenReturn(new byte[]{1});

        worksheetComponent.generateWorksheet(vehicle.getId(), WorksheetTemplate.VALVE_CLEARANCE, 2, 2);

        assertThat(prefill.getValue().specification()).isSameAs(spec);
        assertThat(prefill.getValue().installedShims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                new WorksheetPrefill.Valve(1, ValveKind.INTAKE, 1), new BigDecimal("1.750"),
                new WorksheetPrefill.Valve(1, ValveKind.EXHAUST, 1), new BigDecimal("1.975")));
    }

    @Test
    void generateWorksheet_otherTemplate_needsNoShims() {
        Vehicle vehicle = ownVehicle(VehicleType.MOTORCYCLE);
        when(worksheetPdfHelper.generate(any(), any(), any(), any(), any(), any())).thenReturn(new byte[]{1});

        worksheetComponent.generateWorksheet(vehicle.getId(), WorksheetTemplate.BRAKES, null, null);

        verifyNoInteractions(measurementValveRepository);
    }

    private static MeasurementValve valve(MaintenanceLog log, int cylinder, ValveKind kind, String shimOld,
                                          String shimNew) {
        return MeasurementValve.builder().log(log).cylinder(cylinder).valve(kind).valveIndex(1)
                .shimOld(shimOld == null ? null : new BigDecimal(shimOld))
                .shimNew(shimNew == null ? null : new BigDecimal(shimNew))
                .build();
    }

    private Vehicle ownVehicle(VehicleType type) {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        vehicle.setType(type);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        return vehicle;
    }
}
