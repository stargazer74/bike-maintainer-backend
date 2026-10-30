package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceReportEntry;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceReportPdfHelper;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceReport;
import de.chriswohlbrecht.maintenance.exception.InvalidLogReferenceException;
import de.chriswohlbrecht.maintenance.exception.InvalidTaskReferenceException;
import de.chriswohlbrecht.maintenance.mapper.MaintenanceLogMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTask;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTaskId;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class MaintenanceLogComponentImplTest {

    private static final Long USER_ID = 99L;

    @Mock
    private ICurrentUserComponent currentUserComponent;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MaintenanceLogRepository maintenanceLogRepository;

    @Mock
    private MaintenanceLogTaskRepository maintenanceLogTaskRepository;

    @Mock
    private MaintenanceTaskRepository maintenanceTaskRepository;

    @Mock
    private MaintenanceReportPdfHelper maintenanceReportPdfHelper;

    private MaintenanceLogComponentImpl maintenanceLogComponent;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserComponent.getCurrentUserId()).thenReturn(USER_ID);
        maintenanceLogComponent = new MaintenanceLogComponentImpl(
                vehicleRepository, maintenanceLogRepository, maintenanceLogTaskRepository,
                maintenanceTaskRepository, new MaintenanceLogMapperImpl(), maintenanceReportPdfHelper,
                new UserLocaleComponentImpl(), currentUserComponent);
    }

    @Test
    void listMaintenanceLogs_vehicleFound_returnsMappedLogs() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog log = Instancio.create(MaintenanceLog.class);
        log.setVehicle(vehicle);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(log));
        when(maintenanceLogTaskRepository.findAllByLog_Id(log.getId())).thenReturn(List.of());

        Optional<List<MaintenanceLogResponse>> result = maintenanceLogComponent.listMaintenanceLogs(vehicle.getId());

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).getVehicleId()).isEqualTo(vehicle.getId());
    }

    @Test
    void listMaintenanceLogs_vehicleNotFound_returnsEmptyOptional() {
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        Optional<List<MaintenanceLogResponse>> result = maintenanceLogComponent.listMaintenanceLogs(99L);

        assertThat(result).isEmpty();
        verify(maintenanceLogRepository, never()).findAllByVehicle_Id(any());
    }

    @Test
    void getMaintenanceLog_found_returnsMappedResponseWithPerformedTaskIds() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog log = Instancio.create(MaintenanceLog.class);
        log.setVehicle(vehicle);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        MaintenanceLogTask link = MaintenanceLogTask.builder()
                .id(new MaintenanceLogTaskId(log.getId(), task.getId()))
                .log(log)
                .task(task)
                .build();
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(log.getId(), vehicle.getId())).thenReturn(Optional.of(log));
        when(maintenanceLogTaskRepository.findAllByLog_Id(log.getId())).thenReturn(List.of(link));

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.getMaintenanceLog(vehicle.getId(), log.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPerformedTaskIds()).containsExactly(task.getId());
    }

    @Test
    void getMaintenanceLog_notFound_returnsEmptyOptional() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.getMaintenanceLog(vehicle.getId(), 99L);

        assertThat(result).isEmpty();
    }

    @Test
    void createMaintenanceLog_withoutTasks_savesLogAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(Set.of());
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class))).thenAnswer(invocation -> {
            MaintenanceLog toSave = invocation.getArgument(0);
            toSave.setId(20L);
            return toSave;
        });
        when(maintenanceLogTaskRepository.findAllByLog_Id(20L)).thenReturn(List.of());

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(20L);
        assertThat(result.get().getVehicleId()).isEqualTo(vehicle.getId());
        verify(maintenanceLogTaskRepository, never()).save(any());
        verify(maintenanceLogTaskRepository, never()).deleteAllByLog_Id(any());
    }

    @Test
    void createMaintenanceLog_withValidTaskIds_linksTasks() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(Set.of(task.getId()));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(task.getId(), vehicle.getId())).thenReturn(Optional.of(task));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class))).thenAnswer(invocation -> {
            MaintenanceLog toSave = invocation.getArgument(0);
            toSave.setId(20L);
            return toSave;
        });
        when(maintenanceLogTaskRepository.findAllByLog_Id(20L)).thenReturn(List.of());

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request);

        assertThat(result).isPresent();
        verify(maintenanceLogTaskRepository).save(argThat(link ->
                link.getTask().equals(task) && link.getLog().getId().equals(20L)));
    }

    @Test
    void createMaintenanceLog_withUnknownTaskId_throwsInvalidTaskReferenceException() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(Set.of(999L));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(999L, vehicle.getId())).thenReturn(Optional.empty());

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidTaskReferenceException.class,
                () -> maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request)
        )).hasMessageContaining("Task with id 999 not found");

        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void createMaintenanceLog_vehicleNotFound_returnsEmptyAndDoesNotSave() {
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class).mileageAtPerformed(12000);
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.createMaintenanceLog(99L, request);

        assertThat(result).isEmpty();
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceLog_found_replacesTaskLinksAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = Instancio.create(MaintenanceLog.class);
        existing.setVehicle(vehicle);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(Set.of());
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(existing.getId(), vehicle.getId())).thenReturn(Optional.of(existing));
        when(maintenanceLogRepository.save(existing)).thenReturn(existing);
        when(maintenanceLogTaskRepository.findAllByLog_Id(existing.getId())).thenReturn(List.of());

        Optional<MaintenanceLogResponse> result =
                maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), existing.getId(), request);

        assertThat(result).isPresent();
        assertThat(result.get().getMileageAtPerformed()).isEqualTo(12000);
        verify(maintenanceLogTaskRepository).deleteAllByLog_Id(existing.getId());
    }

    @Test
    void updateMaintenanceLog_notFound_returnsEmptyAndDoesNotSave() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class).mileageAtPerformed(12000);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), 99L, request);

        assertThat(result).isEmpty();
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceLog_withUnknownTaskId_throwsInvalidTaskReferenceException() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = Instancio.create(MaintenanceLog.class);
        existing.setVehicle(vehicle);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(Set.of(999L));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(existing.getId(), vehicle.getId())).thenReturn(Optional.of(existing));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(999L, vehicle.getId())).thenReturn(Optional.empty());

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidTaskReferenceException.class,
                () -> maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), existing.getId(), request)
        )).hasMessageContaining("Task with id 999 not found");

        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void deleteMaintenanceLog_found_deletesLinksAndLogAndReturnsTrue() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog log = Instancio.create(MaintenanceLog.class);
        log.setVehicle(vehicle);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(log.getId(), vehicle.getId())).thenReturn(Optional.of(log));

        boolean result = maintenanceLogComponent.deleteMaintenanceLog(vehicle.getId(), log.getId());

        assertThat(result).isTrue();
        verify(maintenanceLogTaskRepository).deleteAllByLog_Id(log.getId());
        verify(maintenanceLogRepository).deleteById(log.getId());
    }

    @Test
    void deleteMaintenanceLog_notFound_returnsFalseAndDoesNotDelete() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        boolean result = maintenanceLogComponent.deleteMaintenanceLog(vehicle.getId(), 99L);

        assertThat(result).isFalse();
        verify(maintenanceLogRepository, never()).deleteById(any());
    }

    @Test
    void generateMaintenanceReport_withoutSelection_containsAllLogsInChronologicalOrderInOwnersLanguage() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        vehicle.getUser().setLanguage("en");
        MaintenanceLog newer = log(vehicle, 2L, LocalDate.of(2026, 5, 1), 2000);
        MaintenanceLog older = log(vehicle, 1L, LocalDate.of(2025, 5, 1), 1000);
        byte[] pdfBytes = {1, 2, 3};
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(newer, older));
        when(maintenanceReportPdfHelper.generate(eq(vehicle), any(), eq(Locale.UK))).thenReturn(pdfBytes);

        Optional<MaintenanceReport> result = maintenanceLogComponent.generateMaintenanceReport(vehicle.getId(), null);

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo(pdfBytes);
        assertThat(result.get().fileName()).isEqualTo("maintenance-report-vehicle-" + vehicle.getId() + ".pdf");
        verify(maintenanceReportPdfHelper).generate(eq(vehicle), argThat(entries -> entries.stream()
                .map(MaintenanceReportEntry::mileageAtPerformed).toList().equals(List.of(1000, 2000))),
                eq(Locale.UK));
    }

    @Test
    void generateMaintenanceReport_withSelection_containsOnlySelectedLogsChronologicallyWithoutDuplicates() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog first = log(vehicle, 1L, LocalDate.of(2024, 5, 1), 1000);
        MaintenanceLog second = log(vehicle, 2L, LocalDate.of(2025, 5, 1), 2000);
        MaintenanceLog third = log(vehicle, 3L, LocalDate.of(2026, 5, 1), 3000);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(first, second, third));
        when(maintenanceReportPdfHelper.generate(eq(vehicle), any(), any())).thenReturn(new byte[] {1});

        Optional<MaintenanceReport> result = maintenanceLogComponent.generateMaintenanceReport(vehicle.getId(),
                List.of(3L, 1L, 3L));

        assertThat(result).isPresent();
        assertThat(result.get().fileName()).isEqualTo("maintenance-report-vehicle-" + vehicle.getId() + ".pdf");
        verify(maintenanceReportPdfHelper).generate(eq(vehicle), argThat(entries -> entries.stream()
                .map(MaintenanceReportEntry::mileageAtPerformed).toList().equals(List.of(1000, 3000))), any());
    }

    @Test
    void generateMaintenanceReport_singleLog_fileNameContainsTheDateOfTheMaintenance() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog first = log(vehicle, 1L, LocalDate.of(2024, 5, 1), 1000);
        MaintenanceLog second = log(vehicle, 2L, LocalDate.of(2025, 6, 23), 2000);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(first, second));
        when(maintenanceReportPdfHelper.generate(eq(vehicle), any(), any())).thenReturn(new byte[] {1});

        Optional<MaintenanceReport> result = maintenanceLogComponent.generateMaintenanceReport(vehicle.getId(),
                List.of(2L, 2L));

        assertThat(result).isPresent();
        assertThat(result.get().fileName())
                .isEqualTo("maintenance-report-vehicle-" + vehicle.getId() + "-2025-06-23.pdf");
    }

    @Test
    void generateMaintenanceReport_logOfAnotherVehicle_throwsInvalidLogReference() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog own = log(vehicle, 1L, LocalDate.of(2024, 5, 1), 1000);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(own));

        assertThatThrownBy(() -> maintenanceLogComponent.generateMaintenanceReport(vehicle.getId(),
                List.of(1L, 77L))).isInstanceOf(InvalidLogReferenceException.class);
        verify(maintenanceReportPdfHelper, never()).generate(any(), any(), any());
    }

    @Test
    void generateMaintenanceReport_emptySelection_throwsInvalidLogReference() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> maintenanceLogComponent.generateMaintenanceReport(vehicle.getId(), List.of()))
                .isInstanceOf(InvalidLogReferenceException.class);
    }

    @Test
    void generateMaintenanceReport_vehicleNotFound_returnsEmptyOptional() {
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        Optional<MaintenanceReport> result = maintenanceLogComponent.generateMaintenanceReport(99L, List.of(1L));

        assertThat(result).isEmpty();
        verify(maintenanceReportPdfHelper, never()).generate(any(), any(), any());
    }

    private MaintenanceLog log(Vehicle vehicle, Long id, LocalDate performedAt, int mileage) {
        MaintenanceLog log = Instancio.create(MaintenanceLog.class);
        log.setId(id);
        log.setVehicle(vehicle);
        log.setPerformedAt(performedAt);
        log.setMileageAtPerformed(mileage);
        lenient().when(maintenanceLogTaskRepository.findAllByLog_Id(id)).thenReturn(List.of());
        return log;
    }
}
