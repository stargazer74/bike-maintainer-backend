package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceLog;
import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceMaterial;
import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceTask;
import de.chriswohlbrecht.maintenance.api.model.ExportedVehicle;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMaterialResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.api.model.MaterialUnit;
import de.chriswohlbrecht.maintenance.api.model.VehicleExport;
import de.chriswohlbrecht.maintenance.api.model.VehicleRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleSpecifications;
import de.chriswohlbrecht.maintenance.api.model.VehicleType;
import de.chriswohlbrecht.maintenance.component.model.VehicleExportFile;
import de.chriswohlbrecht.maintenance.exception.ImportFormatUnsupportedException;
import de.chriswohlbrecht.maintenance.exception.ImportInvalidException;
import de.chriswohlbrecht.maintenance.exception.InvalidMaintenanceIntervalException;
import de.chriswohlbrecht.maintenance.exception.InvalidMaterialException;
import de.chriswohlbrecht.maintenance.mapper.VehicleExportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleExportComponentImplTest {

    private static final Long VEHICLE_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneId.of("Europe/Berlin"));

    @Mock
    private IVehicleComponent vehicleComponent;

    @Mock
    private IMaintenanceTaskComponent maintenanceTaskComponent;

    @Mock
    private IMaintenanceLogComponent maintenanceLogComponent;

    @Mock
    private IMeasurementComponent measurementComponent;

    private VehicleExportComponentImpl component;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment().withProperty("info.app.version", "0.15.0");
        component = new VehicleExportComponentImpl(vehicleComponent, maintenanceTaskComponent, maintenanceLogComponent,
                measurementComponent, Mappers.getMapper(VehicleExportMapper.class), environment, CLOCK);
    }

    @Test
    void exportVehicle_ofAnUnknownVehicle_isEmpty() {
        when(vehicleComponent.getVehicle(VEHICLE_ID)).thenReturn(Optional.empty());

        assertThat(component.exportVehicle(VEHICLE_ID)).isEmpty();
        verifyNoInteractions(maintenanceTaskComponent, maintenanceLogComponent);
    }

    @Test
    void exportVehicle_replacesIdsByRefsAndOrdersEverything() {
        when(vehicleComponent.getVehicle(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));
        when(maintenanceTaskComponent.listMaintenanceTasks(VEHICLE_ID)).thenReturn(Optional.of(List.of(
                new MaintenanceTaskResponse().id(30L).name("Kette").intervalKm(1000).active(true),
                new MaintenanceTaskResponse().id(12L).name("Oel").intervalMonths(12).active(false))));
        MaintenanceLogResponse later = new MaintenanceLogResponse().id(1L).performedAt(LocalDate.of(2026, 8, 1))
                .mileageAtPerformed(9000).currency(Currency.CHF)
                .performedTaskIds(List.of(30L, 12L))
                .materials(List.of(
                        new MaintenanceMaterialResponse().name("Filter").quantity(BigDecimal.ONE)
                                .unit(MaterialUnit.PIECE).taskId(12L),
                        new MaintenanceMaterialResponse().name("Lappen").quantity(BigDecimal.TEN)
                                .unit(MaterialUnit.PIECE)));
        MaintenanceLogResponse sameDaySecond = new MaintenanceLogResponse().id(5L)
                .performedAt(LocalDate.of(2026, 3, 1)).mileageAtPerformed(8000).currency(Currency.EUR);
        MaintenanceLogResponse sameDayFirst = new MaintenanceLogResponse().id(4L)
                .performedAt(LocalDate.of(2026, 3, 1)).mileageAtPerformed(8000).currency(Currency.EUR);
        when(maintenanceLogComponent.listMaintenanceLogs(VEHICLE_ID))
                .thenReturn(Optional.of(List.of(later, sameDaySecond, sameDayFirst)));
        when(measurementComponent.getVehicleSpecifications(VEHICLE_ID))
                .thenReturn(Optional.of(new VehicleSpecifications().cylinders(2)));

        VehicleExportFile file = component.exportVehicle(VEHICLE_ID).orElseThrow();

        assertThat(file.fileName()).isEqualTo("vehicle-die-rote-2026-11-11.json");
        VehicleExport export = file.content();
        assertThat(export.getFormatVersion()).isEqualTo(1);
        assertThat(export.getAppVersion()).isEqualTo("0.15.0");
        assertThat(export.getExportedAt()).isEqualTo(OffsetDateTime.now(CLOCK));
        ExportedVehicle exported = export.getVehicle();
        assertThat(exported.getName()).isEqualTo("Die Rote");
        assertThat(exported.getSpecifications().getCylinders()).isEqualTo(2);
        assertThat(exported.getMaintenanceTasks()).extracting(ExportedMaintenanceTask::getRef,
                        ExportedMaintenanceTask::getName, ExportedMaintenanceTask::getActive)
                .containsExactly(tuple("t1", "Oel", false),
                        tuple("t2", "Kette", true));
        assertThat(exported.getMaintenanceLogs()).extracting(ExportedMaintenanceLog::getMileageAtPerformed)
                .containsExactly(8000, 8000, 9000);
        ExportedMaintenanceLog last = exported.getMaintenanceLogs().get(2);
        assertThat(last.getCurrency()).isEqualTo(Currency.CHF);
        assertThat(last.getPerformedTaskRefs()).containsExactly("t1", "t2");
        assertThat(last.getMaterials()).extracting(ExportedMaintenanceMaterial::getTaskRef)
                .containsExactly("t1", null);
        assertThat(exported.getMaintenanceLogs().get(0).getPerformedTaskRefs()).isEmpty();
    }

    @Test
    void exportVehicle_furtherWorkIsReferencedByTheLogThatCreatedIt() {
        when(vehicleComponent.getVehicle(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));
        when(maintenanceTaskComponent.listMaintenanceTasks(VEHICLE_ID)).thenReturn(Optional.of(List.of(
                new MaintenanceTaskResponse().id(7L).name("Spiegel ersetzt").oneTime(true).createdByLogId(3L),
                new MaintenanceTaskResponse().id(8L).name("Geplant").oneTime(true))));
        when(maintenanceLogComponent.listMaintenanceLogs(VEHICLE_ID)).thenReturn(Optional.of(List.of(
                new MaintenanceLogResponse().id(3L).performedAt(LocalDate.of(2026, 3, 1)).mileageAtPerformed(8000)
                        .currency(Currency.EUR).performedTaskIds(List.of(7L, 8L)),
                new MaintenanceLogResponse().id(4L).performedAt(LocalDate.of(2026, 4, 1)).mileageAtPerformed(9000)
                        .currency(Currency.EUR))));
        when(measurementComponent.getVehicleSpecifications(VEHICLE_ID)).thenReturn(Optional.empty());

        ExportedVehicle exported = component.exportVehicle(VEHICLE_ID).orElseThrow().content().getVehicle();

        assertThat(exported.getMaintenanceLogs()).extracting(ExportedMaintenanceLog::getCreatedTaskRefs)
                .containsExactly(Set.of("t1"), null);
    }

    @Test
    void exportVehicle_withoutTargetValues_leavesThemOut() {
        when(vehicleComponent.getVehicle(VEHICLE_ID)).thenReturn(Optional.of(vehicle()));
        when(maintenanceTaskComponent.listMaintenanceTasks(VEHICLE_ID)).thenReturn(Optional.of(List.of()));
        when(maintenanceLogComponent.listMaintenanceLogs(VEHICLE_ID)).thenReturn(Optional.of(List.of()));
        when(measurementComponent.getVehicleSpecifications(VEHICLE_ID))
                .thenReturn(Optional.of(new VehicleSpecifications()));

        ExportedVehicle exported = component.exportVehicle(VEHICLE_ID).orElseThrow().content().getVehicle();

        assertThat(exported.getSpecifications()).isNull();
        assertThat(exported.getMaintenanceTasks()).isEmpty();
    }

    @Test
    void importVehicle_ofAnotherFormatVersion_createsNothing() {
        VehicleExport file = file(exportedVehicle()).formatVersion(2);

        assertThatThrownBy(() -> component.importVehicle(file))
                .isInstanceOf(ImportFormatUnsupportedException.class)
                .hasMessageContaining("formatVersion 2");
        verifyNoInteractions(vehicleComponent);
    }

    @Test
    void importVehicle_createsEverythingWithNewIdsAndTheFileCurrency() {
        ExportedVehicle vehicle = exportedVehicle()
                .specifications(new VehicleSpecifications().cylinders(4))
                .maintenanceTasks(List.of(
                        new ExportedMaintenanceTask().ref("oil").name("Oel").intervalKm(6000),
                        new ExportedMaintenanceTask().ref("chain").name("Kette").intervalKm(1000)))
                .maintenanceLogs(List.of(new ExportedMaintenanceLog()
                        .performedAt(LocalDate.of(2026, 4, 1)).mileageAtPerformed(18000).currency(Currency.CHF)
                        .performedTaskRefs(new LinkedHashSet<>(List.of("chain", "oil")))
                        .materials(List.of(
                                new ExportedMaintenanceMaterial().name("Filter").quantity(BigDecimal.ONE)
                                        .unit(MaterialUnit.PIECE).taskRef("oil"),
                                new ExportedMaintenanceMaterial().name("Lappen").quantity(BigDecimal.ONE)
                                        .unit(MaterialUnit.PIECE)))));
        when(vehicleComponent.createVehicle(any())).thenReturn(new VehicleResponse().id(VEHICLE_ID).name("Die Rote"));
        when(maintenanceTaskComponent.createMaintenanceTask(eq(VEHICLE_ID), any()))
                .thenReturn(Optional.of(new MaintenanceTaskResponse().id(101L)))
                .thenReturn(Optional.of(new MaintenanceTaskResponse().id(102L)));

        VehicleResponse created = component.importVehicle(file(vehicle));

        assertThat(created.getId()).isEqualTo(VEHICLE_ID);
        ArgumentCaptor<VehicleRequest> vehicleRequest = ArgumentCaptor.forClass(VehicleRequest.class);
        verify(vehicleComponent).createVehicle(vehicleRequest.capture());
        assertThat(vehicleRequest.getValue().getName()).isEqualTo("Die Rote");
        verify(measurementComponent).updateVehicleSpecifications(VEHICLE_ID, new VehicleSpecifications().cylinders(4));
        ArgumentCaptor<MaintenanceTaskRequest> taskRequests = ArgumentCaptor.forClass(MaintenanceTaskRequest.class);
        verify(maintenanceTaskComponent, times(2))
                .createMaintenanceTask(eq(VEHICLE_ID), taskRequests.capture());
        assertThat(taskRequests.getAllValues()).extracting(MaintenanceTaskRequest::getName)
                .containsExactly("Oel", "Kette");
        ArgumentCaptor<MaintenanceLogRequest> logRequest = ArgumentCaptor.forClass(MaintenanceLogRequest.class);
        verify(maintenanceLogComponent).createMaintenanceLog(eq(VEHICLE_ID), logRequest.capture(), eq(Currency.CHF),
                eq(Set.of()));
        assertThat(logRequest.getValue().getPerformedTaskIds()).containsExactly(102L, 101L);
        assertThat(logRequest.getValue().getMaterials()).extracting(material -> material.getTaskId())
                .containsExactly(101L, null);
    }

    @Test
    void importVehicle_furtherWorkIsPassedAsCreatedByItsLog() {
        ExportedVehicle vehicle = exportedVehicle()
                .maintenanceTasks(List.of(
                        new ExportedMaintenanceTask().ref("mirror").name("Spiegel ersetzt").oneTime(true),
                        new ExportedMaintenanceTask().ref("planned").name("Geplant").oneTime(true)))
                .maintenanceLogs(List.of(new ExportedMaintenanceLog()
                        .performedAt(LocalDate.of(2026, 4, 1)).mileageAtPerformed(18000).currency(Currency.EUR)
                        .performedTaskRefs(new LinkedHashSet<>(List.of("mirror", "planned")))
                        .createdTaskRefs(Set.of("mirror"))));
        when(vehicleComponent.createVehicle(any())).thenReturn(new VehicleResponse().id(VEHICLE_ID));
        when(maintenanceTaskComponent.createMaintenanceTask(eq(VEHICLE_ID), any()))
                .thenReturn(Optional.of(new MaintenanceTaskResponse().id(101L)))
                .thenReturn(Optional.of(new MaintenanceTaskResponse().id(102L)));

        component.importVehicle(file(vehicle));

        verify(maintenanceLogComponent).createMaintenanceLog(eq(VEHICLE_ID), any(), eq(Currency.EUR),
                eq(Set.of(101L)));
    }

    @Test
    void importVehicle_withBrokenFurtherWork_reportsAllOfThemAndCreatesNothing() {
        ExportedVehicle vehicle = exportedVehicle()
                .maintenanceTasks(List.of(
                        new ExportedMaintenanceTask().ref("mirror").name("Spiegel ersetzt").oneTime(true),
                        new ExportedMaintenanceTask().ref("chain").name("Kette").intervalKm(1000)))
                .maintenanceLogs(List.of(
                        new ExportedMaintenanceLog().performedAt(LocalDate.of(2026, 1, 1)).mileageAtPerformed(50)
                                .currency(Currency.EUR)
                                .performedTaskRefs(new LinkedHashSet<>(List.of("mirror", "chain")))
                                .createdTaskRefs(new LinkedHashSet<>(List.of("mirror", "chain"))),
                        new ExportedMaintenanceLog().performedAt(LocalDate.of(2026, 2, 1)).mileageAtPerformed(60)
                                .currency(Currency.EUR)
                                .performedTaskRefs(Set.of("mirror"))
                                .createdTaskRefs(new LinkedHashSet<>(List.of("mirror", "other")))));

        assertThatThrownBy(() -> component.importVehicle(file(vehicle)))
                .isInstanceOfSatisfying(ImportInvalidException.class, e -> assertThat(e.getDetails()).containsExactly(
                        "vehicle.maintenanceLogs[0].createdTaskRefs: task \"chain\" is not a one-time task",
                        "vehicle.maintenanceLogs[1].createdTaskRefs: task \"mirror\" is already created by another log",
                        "vehicle.maintenanceLogs[1].createdTaskRefs: task \"other\" is not performed by this log"));
        verifyNoInteractions(vehicleComponent);
    }

    @Test
    void importVehicle_withoutTasksLogsAndTargetValues_createsOnlyTheVehicle() {
        when(vehicleComponent.createVehicle(any())).thenReturn(new VehicleResponse().id(VEHICLE_ID));

        component.importVehicle(file(exportedVehicle()));

        verifyNoInteractions(maintenanceTaskComponent, maintenanceLogComponent, measurementComponent);
    }

    @Test
    void importVehicle_withBrokenReferences_reportsAllOfThemAndCreatesNothing() {
        ExportedVehicle vehicle = exportedVehicle()
                .maintenanceTasks(List.of(
                        new ExportedMaintenanceTask().ref("a").name("Kette").intervalKm(1000),
                        new ExportedMaintenanceTask().ref("a").name("Oel").intervalKm(5000)))
                .maintenanceLogs(List.of(new ExportedMaintenanceLog()
                        .performedAt(LocalDate.of(2026, 1, 1)).mileageAtPerformed(50).currency(Currency.EUR)
                        .performedTaskRefs(Set.of("b"))
                        .materials(List.of(new ExportedMaintenanceMaterial().name("Oel").quantity(BigDecimal.ONE)
                                .unit(MaterialUnit.LITER).taskRef("a")))));

        assertThatThrownBy(() -> component.importVehicle(file(vehicle)))
                .isInstanceOfSatisfying(ImportInvalidException.class, e -> assertThat(e.getDetails()).containsExactly(
                        "vehicle.maintenanceTasks[1].ref: duplicate ref \"a\"",
                        "vehicle.maintenanceLogs[0].performedTaskRefs: unknown task ref \"b\"",
                        "vehicle.maintenanceLogs[0].materials[0].taskRef: task \"a\" is not performed by this log"));
        verifyNoInteractions(vehicleComponent);
    }

    @Test
    void importVehicle_aRuleBrokenByATask_isReportedWithItsLocation() {
        ExportedVehicle vehicle = exportedVehicle().maintenanceTasks(List.of(
                new ExportedMaintenanceTask().ref("a").name("Ohne Intervall")));
        when(vehicleComponent.createVehicle(any())).thenReturn(new VehicleResponse().id(VEHICLE_ID));
        when(maintenanceTaskComponent.createMaintenanceTask(eq(VEHICLE_ID), any()))
                .thenThrow(new InvalidMaintenanceIntervalException("no interval"));

        assertThatThrownBy(() -> component.importVehicle(file(vehicle)))
                .isInstanceOfSatisfying(ImportInvalidException.class, e -> assertThat(e.getDetails())
                        .containsExactly("vehicle.maintenanceTasks[0]: no interval"));
        verify(maintenanceLogComponent, never()).createMaintenanceLog(any(), any(), any(), any());
    }

    @Test
    void importVehicle_aRuleBrokenByALog_isReportedWithItsLocation() {
        ExportedVehicle vehicle = exportedVehicle().maintenanceLogs(List.of(
                new ExportedMaintenanceLog().performedAt(LocalDate.of(2026, 1, 1)).mileageAtPerformed(5)
                        .currency(Currency.EUR)));
        when(vehicleComponent.createVehicle(any())).thenReturn(new VehicleResponse().id(VEHICLE_ID));
        when(maintenanceLogComponent.createMaintenanceLog(eq(VEHICLE_ID), any(), eq(Currency.EUR), any()))
                .thenThrow(new InvalidMaterialException("price must not have more than 2 decimal places"));

        assertThatThrownBy(() -> component.importVehicle(file(vehicle)))
                .isInstanceOfSatisfying(ImportInvalidException.class, e -> assertThat(e.getDetails())
                        .containsExactly("vehicle.maintenanceLogs[0]: price must not have more than 2 decimal places"));
    }

    private static VehicleResponse vehicle() {
        return new VehicleResponse().id(VEHICLE_ID).name("Die Rote").type(VehicleType.MOTORCYCLE).modelYear(2019)
                .firstRegistrationDate(LocalDate.of(2019, 5, 1)).currentMileage(21000);
    }

    private static ExportedVehicle exportedVehicle() {
        return new ExportedVehicle().name("Die Rote").type(VehicleType.MOTORCYCLE).modelYear(2019)
                .firstRegistrationDate(LocalDate.of(2019, 5, 1)).currentMileage(21000);
    }

    private static VehicleExport file(ExportedVehicle vehicle) {
        return new VehicleExport().formatVersion(1).exportedAt(OffsetDateTime.now(CLOCK)).vehicle(vehicle);
    }
}
