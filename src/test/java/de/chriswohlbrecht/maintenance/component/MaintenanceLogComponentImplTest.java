package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.api.model.CostSummaryEntry;
import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMaterialRequest;
import de.chriswohlbrecht.maintenance.api.model.MaterialUnit;
import de.chriswohlbrecht.maintenance.api.model.NewMaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceCostHelper;
import de.chriswohlbrecht.maintenance.exception.InvalidMaterialException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogMaterial;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogMaterialRepository;
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
import org.instancio.Select;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class MaintenanceLogComponentImplTest {

    private static final Long USER_ID = 99L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);

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
    private MaintenanceLogMaterialRepository maintenanceLogMaterialRepository;

    @Mock
    private MaintenanceReportPdfHelper maintenanceReportPdfHelper;

    private MaintenanceLogComponentImpl maintenanceLogComponent;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserComponent.getCurrentUserId()).thenReturn(USER_ID);
        AppUser user = new AppUser();
        user.setCurrency("CHF");
        lenient().when(currentUserComponent.getCurrentUser()).thenReturn(user);
        maintenanceLogComponent = new MaintenanceLogComponentImpl(
                vehicleRepository, maintenanceLogRepository, maintenanceLogMaterialRepository,
                maintenanceLogTaskRepository, maintenanceTaskRepository, new MaintenanceLogMapperImpl(),
                maintenanceReportPdfHelper, new MaintenanceCostHelper(),
                new UserLocaleComponentImpl(), currentUserComponent, CLOCK);
    }

    private static MaintenanceLog eurLog() {
        return Instancio.of(MaintenanceLog.class).set(Select.field(MaintenanceLog::getCurrency), "EUR").create();
    }

    private static MaintenanceLogRequest requestWithoutExtras() {
        return Instancio.of(MaintenanceLogRequest.class)
                .set(Select.field(MaintenanceLogRequest::getMaterials), List.of())
                .set(Select.field(MaintenanceLogRequest::getLaborCost), null)
                .create();
    }

    private static MaintenanceMaterialRequest material(String name, String price, Long taskId) {
        return new MaintenanceMaterialRequest(name, new BigDecimal("1"), MaterialUnit.PIECE)
                .price(price == null ? null : new BigDecimal(price))
                .taskId(taskId);
    }

    @Test
    void listMaintenanceLogs_vehicleFound_returnsMappedLogs() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog log = eurLog();
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
        MaintenanceLog log = eurLog();
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
        MaintenanceLogRequest request = requestWithoutExtras()
                .newTasks(List.of())
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
    void createMaintenanceLog_withNewTasks_createsThemOneTimeOrRecurringAndLinksThem() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .newTasks(List.of(
                        new NewMaintenanceTaskRequest("Bremsleitung getauscht").description("Stahlflex vorne"),
                        new NewMaintenanceTaskRequest("Kette reinigen").intervalKm(1000),
                        new NewMaintenanceTaskRequest("Luftfilter").intervalMonths(12)));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        List<MaintenanceTask> savedTasks = new ArrayList<>();
        when(maintenanceTaskRepository.save(any(MaintenanceTask.class))).thenAnswer(invocation -> {
            MaintenanceTask toSave = invocation.getArgument(0);
            toSave.setId(100L + savedTasks.size());
            savedTasks.add(toSave);
            return toSave;
        });
        when(maintenanceLogRepository.save(any(MaintenanceLog.class))).thenAnswer(invocation -> {
            MaintenanceLog toSave = invocation.getArgument(0);
            toSave.setId(20L);
            return toSave;
        });
        when(maintenanceLogTaskRepository.findAllByLog_Id(20L)).thenReturn(List.of());

        maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request);

        assertThat(savedTasks).extracting(MaintenanceTask::getName, MaintenanceTask::isOneTime,
                        MaintenanceTask::getIntervalKm, MaintenanceTask::getIntervalMonths, MaintenanceTask::isActive)
                .containsExactly(
                        tuple("Bremsleitung getauscht", true, null, null, true),
                        tuple("Kette reinigen", false, 1000, null, true),
                        tuple("Luftfilter", false, null, 12, true));
        assertThat(savedTasks).allMatch(task -> task.getVehicle() == vehicle);
        assertThat(savedTasks.getFirst().getDescription()).isEqualTo("Stahlflex vorne");
        verify(maintenanceLogTaskRepository, times(3)).save(argThat(link -> link.getLog().getId() == 20L
                && savedTasks.contains(link.getTask())));
    }

    @Test
    void createMaintenanceLog_unknownTaskId_createsNoNewTask() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .performedTaskIds(Set.of(999L))
                .newTasks(List.of(new NewMaintenanceTaskRequest("Spiegel ersetzt")));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(999L, vehicle.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request))
                .isInstanceOf(InvalidTaskReferenceException.class);
        verify(maintenanceTaskRepository, never()).save(any());
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceLog_withNewTasks_createsAndLinksThem() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = eurLog();
        existing.setId(20L);
        existing.setVehicle(vehicle);
        MaintenanceLogRequest request = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .newTasks(List.of(new NewMaintenanceTaskRequest("Spiegel ersetzt")));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(20L, vehicle.getId())).thenReturn(Optional.of(existing));
        when(maintenanceLogRepository.save(existing)).thenReturn(existing);
        when(maintenanceTaskRepository.save(any(MaintenanceTask.class))).thenAnswer(invocation -> {
            MaintenanceTask toSave = invocation.getArgument(0);
            toSave.setId(100L);
            return toSave;
        });
        when(maintenanceLogTaskRepository.findAllByLog_Id(20L)).thenReturn(List.of());

        maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), 20L, request);

        verify(maintenanceTaskRepository).save(argThat(task -> "Spiegel ersetzt".equals(task.getName())
                && task.isOneTime()));
        verify(maintenanceLogTaskRepository).save(argThat(link -> link.getTask().getId() == 100L));
    }

    @Test
    void createMaintenanceLog_withValidTaskIds_linksTasks() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        MaintenanceLogRequest request = requestWithoutExtras()
                .newTasks(List.of())
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
        MaintenanceLogRequest request = requestWithoutExtras()
                .newTasks(List.of())
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
        MaintenanceLogRequest request = requestWithoutExtras()
                .newTasks(List.of()).mileageAtPerformed(12000);
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.createMaintenanceLog(99L, request);

        assertThat(result).isEmpty();
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceLog_found_replacesTaskLinksAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = eurLog();
        existing.setVehicle(vehicle);
        MaintenanceLogRequest request = requestWithoutExtras()
                .newTasks(List.of())
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
        MaintenanceLogRequest request = requestWithoutExtras()
                .newTasks(List.of()).mileageAtPerformed(12000);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), 99L, request);

        assertThat(result).isEmpty();
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceLog_withUnknownTaskId_throwsInvalidTaskReferenceException() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = eurLog();
        existing.setVehicle(vehicle);
        MaintenanceLogRequest request = requestWithoutExtras()
                .newTasks(List.of())
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
        MaintenanceLog log = eurLog();
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
        when(maintenanceReportPdfHelper.generate(eq(vehicle), any(), eq(LocalDate.of(2026, 10, 30)), eq(Locale.UK)))
                .thenReturn(pdfBytes);

        Optional<MaintenanceReport> result = maintenanceLogComponent.generateMaintenanceReport(vehicle.getId(), null);

        assertThat(result).isPresent();
        assertThat(result.get().content()).isEqualTo(pdfBytes);
        assertThat(result.get().fileName()).isEqualTo("maintenance-report-vehicle-" + vehicle.getId() + ".pdf");
        verify(maintenanceReportPdfHelper).generate(eq(vehicle), argThat(entries -> entries.stream()
                .map(MaintenanceReportEntry::mileageAtPerformed).toList().equals(List.of(1000, 2000))),
                eq(LocalDate.of(2026, 10, 30)), eq(Locale.UK));
    }

    @Test
    void generateMaintenanceReport_withSelection_containsOnlySelectedLogsChronologicallyWithoutDuplicates() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog first = log(vehicle, 1L, LocalDate.of(2024, 5, 1), 1000);
        MaintenanceLog second = log(vehicle, 2L, LocalDate.of(2025, 5, 1), 2000);
        MaintenanceLog third = log(vehicle, 3L, LocalDate.of(2026, 5, 1), 3000);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(first, second, third));
        when(maintenanceReportPdfHelper.generate(eq(vehicle), any(), any(), any())).thenReturn(new byte[] {1});

        Optional<MaintenanceReport> result = maintenanceLogComponent.generateMaintenanceReport(vehicle.getId(),
                List.of(3L, 1L, 3L));

        assertThat(result).isPresent();
        assertThat(result.get().fileName()).isEqualTo("maintenance-report-vehicle-" + vehicle.getId() + ".pdf");
        verify(maintenanceReportPdfHelper).generate(eq(vehicle), argThat(entries -> entries.stream()
                .map(MaintenanceReportEntry::mileageAtPerformed).toList().equals(List.of(1000, 3000))), any(), any());
    }

    @Test
    void generateMaintenanceReport_singleLog_fileNameContainsTheDateOfTheMaintenance() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog first = log(vehicle, 1L, LocalDate.of(2024, 5, 1), 1000);
        MaintenanceLog second = log(vehicle, 2L, LocalDate.of(2025, 6, 23), 2000);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(first, second));
        when(maintenanceReportPdfHelper.generate(eq(vehicle), any(), any(), any())).thenReturn(new byte[] {1});

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
        verify(maintenanceReportPdfHelper, never()).generate(any(), any(), any(), any());
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
        verify(maintenanceReportPdfHelper, never()).generate(any(), any(), any(), any());
    }

    private MaintenanceLog log(Vehicle vehicle, Long id, LocalDate performedAt, int mileage) {
        MaintenanceLog log = eurLog();
        log.setId(id);
        log.setVehicle(vehicle);
        log.setPerformedAt(performedAt);
        log.setMileageAtPerformed(mileage);
        lenient().when(maintenanceLogTaskRepository.findAllByLog_Id(id)).thenReturn(List.of());
        return log;
    }

    @Test
    void create_storesMaterialsAndUserCurrency() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        MaintenanceLogRequest request = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .performedTaskIds(Set.of(task.getId()))
                .laborCost(new BigDecimal("30"))
                .materials(List.of(material("Oelfilter", "12.50", task.getId()), material("Dichtring", "7.25", null)));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(task.getId(), vehicle.getId())).thenReturn(Optional.of(task));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class))).thenAnswer(invocation -> {
            MaintenanceLog toSave = invocation.getArgument(0);
            toSave.setId(20L);
            return toSave;
        });
        when(maintenanceLogMaterialRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(maintenanceLogMaterialRepository.findAllByLog_IdOrderByPosition(20L)).thenAnswer(invocation ->
                capturedMaterials());

        MaintenanceLogResponse response = maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request)
                .orElseThrow();

        assertThat(capturedMaterials()).extracting(MaintenanceLogMaterial::getName, MaintenanceLogMaterial::getPosition,
                        material -> material.getTask() == null ? null : material.getTask().getId(),
                        material -> material.getLog().getId())
                .containsExactly(tuple("Oelfilter", 0, task.getId(), 20L), tuple("Dichtring", 1, null, 20L));
        assertThat(response.getCurrency()).isEqualTo(Currency.CHF);
        assertThat(response.getLaborCost()).isEqualByComparingTo("30");
        assertThat(response.getTotalCost()).isEqualByComparingTo("49.75");
        assertThat(response.getMaterials()).extracting(m -> m.getName(), m -> m.getTaskId())
                .containsExactly(tuple("Oelfilter", task.getId()), tuple("Dichtring", null));
    }

    @SuppressWarnings("unchecked")
    private List<MaintenanceLogMaterial> capturedMaterials() {
        org.mockito.ArgumentCaptor<List<MaintenanceLogMaterial>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(maintenanceLogMaterialRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void update_replacesMaterialsAndKeepsCurrency() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = eurLog();
        existing.setId(20L);
        existing.setVehicle(vehicle);
        MaintenanceLogRequest request = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .materials(List.of(material("Kettenspray", null, null)));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(20L, vehicle.getId())).thenReturn(Optional.of(existing));
        when(maintenanceLogRepository.save(existing)).thenReturn(existing);

        MaintenanceLogResponse response = maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), 20L, request)
                .orElseThrow();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(maintenanceLogMaterialRepository);
        order.verify(maintenanceLogMaterialRepository).deleteAllByLog_Id(20L);
        order.verify(maintenanceLogMaterialRepository).saveAll(argThat(list ->
                list.iterator().next().getName().equals("Kettenspray")));
        assertThat(existing.getCurrency()).isEqualTo("EUR");
        assertThat(response.getCurrency()).isEqualTo(Currency.EUR);
    }

    @Test
    void create_materialWithTaskNotPerformed_throws() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .materials(List.of(material("Oelfilter", "12.50", 777L)));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request))
                .isInstanceOf(InvalidMaterialException.class);
        verify(maintenanceLogRepository, never()).save(any());
        verify(maintenanceLogMaterialRepository, never()).saveAll(any());
    }

    @Test
    void create_priceWithThreeDecimals_throws() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .materials(List.of(material("Oelfilter", "12.505", null)));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request))
                .isInstanceOf(InvalidMaterialException.class);
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void create_laborCostWithThreeDecimalsOrQuantityWithFour_throws() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        MaintenanceLogRequest labor = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .laborCost(new BigDecimal("1.001"));
        MaintenanceLogRequest quantity = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .materials(List.of(material("Oel", null, null).quantity(new BigDecimal("1.2345"))));

        assertThatThrownBy(() -> maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), labor))
                .isInstanceOf(InvalidMaterialException.class);
        assertThatThrownBy(() -> maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), quantity))
                .isInstanceOf(InvalidMaterialException.class);
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void create_amountsWithTrailingZerosBeyondScale_areAccepted() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = new MaintenanceLogRequest(LocalDate.of(2026, 3, 1), 8600)
                .materials(List.of(material("Oel", "12.500", null).quantity(new BigDecimal("1.5000"))));
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request);

        verify(maintenanceLogMaterialRepository).saveAll(any());
    }

    @Test
    void deleteMaintenanceLog_deletesMaterialsFirst() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = eurLog();
        existing.setId(20L);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(20L, vehicle.getId())).thenReturn(Optional.of(existing));

        assertThat(maintenanceLogComponent.deleteMaintenanceLog(vehicle.getId(), 20L)).isTrue();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(maintenanceLogMaterialRepository, maintenanceLogRepository);
        order.verify(maintenanceLogMaterialRepository).deleteAllByLog_Id(20L);
        order.verify(maintenanceLogRepository).deleteById(20L);
    }

    private static MaintenanceLog costLog(Long id, String currency, LocalDate performedAt, String laborCost) {
        return Instancio.of(MaintenanceLog.class)
                .set(Select.field(MaintenanceLog::getId), id)
                .set(Select.field(MaintenanceLog::getCurrency), currency)
                .set(Select.field(MaintenanceLog::getPerformedAt), performedAt)
                .set(Select.field(MaintenanceLog::getLaborCost), laborCost == null ? null : new BigDecimal(laborCost))
                .create();
    }

    private static MaintenanceLogMaterial materialOf(MaintenanceLog log, String price) {
        return Instancio.of(MaintenanceLogMaterial.class)
                .set(Select.field(MaintenanceLogMaterial::getLog), log)
                .set(Select.field(MaintenanceLogMaterial::getPrice), price == null ? null : new BigDecimal(price))
                .create();
    }

    @Test
    void getCostSummary_sumsPerCurrencySortedWithCurrentYear() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog eur2025 = costLog(1L, "EUR", LocalDate.of(2025, 12, 31), "60.00");
        MaintenanceLog eur2026 = costLog(2L, "EUR", LocalDate.of(2026, 1, 1), null);
        MaintenanceLog chf2026 = costLog(3L, "CHF", LocalDate.of(2026, 5, 5), "20");
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId()))
                .thenReturn(List.of(eur2025, eur2026, chf2026));
        when(maintenanceLogMaterialRepository.findAllByLog_Vehicle_Id(vehicle.getId())).thenReturn(List.of(
                materialOf(eur2025, "40.00"), materialOf(eur2026, "50.00"), materialOf(eur2026, null)));

        Optional<List<CostSummaryEntry>> result = maintenanceLogComponent.getCostSummary(vehicle.getId());

        assertThat(result).isPresent();
        assertThat(result.get()).extracting(CostSummaryEntry::getCurrency, CostSummaryEntry::getTotal,
                        CostSummaryEntry::getCurrentYear)
                .containsExactly(
                        tuple(Currency.CHF, new BigDecimal("20.00"), new BigDecimal("20.00")),
                        tuple(Currency.EUR, new BigDecimal("150.00"), new BigDecimal("50.00")));
    }

    @Test
    void getCostSummary_noCostsThisYear_currentYearIsZero() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog old = costLog(1L, "EUR", LocalDate.of(2025, 3, 1), "10.00");
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(old));
        when(maintenanceLogMaterialRepository.findAllByLog_Vehicle_Id(vehicle.getId())).thenReturn(List.of());

        List<CostSummaryEntry> result = maintenanceLogComponent.getCostSummary(vehicle.getId()).orElseThrow();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getCurrentYear()).isEqualByComparingTo("0");
        assertThat(result.getFirst().getCurrentYear().scale()).isEqualTo(2);
    }

    @Test
    void getCostSummary_logsWithoutAmounts_areIgnored() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog noCosts = costLog(1L, "EUR", LocalDate.of(2026, 3, 1), null);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(noCosts));
        when(maintenanceLogMaterialRepository.findAllByLog_Vehicle_Id(vehicle.getId()))
                .thenReturn(List.of(materialOf(noCosts, null)));

        assertThat(maintenanceLogComponent.getCostSummary(vehicle.getId())).contains(List.of());
    }

    @Test
    void getCostSummary_foreignVehicle_returnsEmptyOptional() {
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        assertThat(maintenanceLogComponent.getCostSummary(99L)).isEmpty();
        verify(maintenanceLogRepository, never()).findAllByVehicle_Id(any());
    }
}
