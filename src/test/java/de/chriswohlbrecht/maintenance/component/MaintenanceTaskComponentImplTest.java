package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskStatus;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceChecklistEntry;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceChecklistPdfHelper;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceTaskStatusHelper;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceTaskStatusResult;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceChecklist;
import de.chriswohlbrecht.maintenance.exception.ConflictException;
import de.chriswohlbrecht.maintenance.exception.InvalidMaintenanceIntervalException;
import de.chriswohlbrecht.maintenance.exception.InvalidTaskReferenceException;
import de.chriswohlbrecht.maintenance.mapper.MaintenanceTaskMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTask;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class MaintenanceTaskComponentImplTest {

    private static final Long USER_ID = 99L;

    @Mock
    private ICurrentUserComponent currentUserComponent;

    private static final MaintenanceTaskStatusResult DUMMY_STATUS =
            new MaintenanceTaskStatusResult(MaintenanceTaskStatus.OK, null, null, null, null, null, null);

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MaintenanceTaskRepository maintenanceTaskRepository;

    @Mock
    private MaintenanceLogTaskRepository maintenanceLogTaskRepository;

    @Mock
    private MaintenanceTaskStatusHelper maintenanceTaskStatusHelper;

    @Mock
    private MaintenanceChecklistPdfHelper maintenanceChecklistPdfHelper;

    @Mock
    private IUserLocaleComponent userLocaleComponent;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneId.of("Europe/Berlin"));

    private MaintenanceTaskComponentImpl maintenanceTaskComponent;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserComponent.getCurrentUserId()).thenReturn(USER_ID);
        maintenanceTaskComponent = new MaintenanceTaskComponentImpl(
                vehicleRepository, maintenanceTaskRepository, maintenanceLogTaskRepository,
                new MaintenanceTaskMapperImpl(), maintenanceTaskStatusHelper, currentUserComponent,
                maintenanceChecklistPdfHelper, userLocaleComponent, CLOCK);
        lenient().when(maintenanceLogTaskRepository.findAllByLog_Vehicle_Id(any())).thenReturn(List.<MaintenanceLogTask>of());
        lenient().when(maintenanceTaskStatusHelper.compute(any(), anyInt(), any(), any())).thenReturn(DUMMY_STATUS);
    }

    @Test
    void listMaintenanceTasks_vehicleFound_returnsMappedTasks() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        task.setVehicle(vehicle);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(task));

        Optional<List<MaintenanceTaskResponse>> result = maintenanceTaskComponent.listMaintenanceTasks(vehicle.getId());

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).getVehicleId()).isEqualTo(vehicle.getId());
        assertThat(result.get().get(0).getName()).isEqualTo(task.getName());
        assertThat(result.get().get(0).getStatus()).isEqualTo(MaintenanceTaskStatus.OK);
    }

    @Test
    void listMaintenanceTasks_vehicleNotFound_returnsEmptyOptional() {
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        Optional<List<MaintenanceTaskResponse>> result = maintenanceTaskComponent.listMaintenanceTasks(99L);

        assertThat(result).isEmpty();
        verify(maintenanceTaskRepository, never()).findAllByVehicle_Id(any());
    }

    @Test
    void getMaintenanceTask_found_returnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        task.setVehicle(vehicle);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(task.getId(), vehicle.getId())).thenReturn(Optional.of(task));

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.getMaintenanceTask(vehicle.getId(), task.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(task.getId());
        assertThat(result.get().getStatus()).isEqualTo(MaintenanceTaskStatus.OK);
    }

    @Test
    void getMaintenanceTask_vehicleNotFound_returnsEmptyOptionalWithoutQueryingTask() {
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.getMaintenanceTask(99L, 1L);

        assertThat(result).isEmpty();
        verify(maintenanceTaskRepository, never()).findByIdAndVehicle_Id(any(), any());
    }

    @Test
    void getMaintenanceTask_taskNotFound_returnsEmptyOptional() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.getMaintenanceTask(vehicle.getId(), 99L);

        assertThat(result).isEmpty();
    }

    @Test
    void createMaintenanceTask_vehicleFound_savesAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.save(any(MaintenanceTask.class))).thenAnswer(invocation -> {
            MaintenanceTask toSave = invocation.getArgument(0);
            toSave.setId(10L);
            return toSave;
        });

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.createMaintenanceTask(vehicle.getId(), request);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(10L);
        assertThat(result.get().getVehicleId()).isEqualTo(vehicle.getId());
        assertThat(result.get().getName()).isEqualTo(request.getName());
        assertThat(result.get().getStatus()).isEqualTo(MaintenanceTaskStatus.OK);
    }

    @Test
    void createMaintenanceTask_vehicleNotFound_returnsEmptyOptionalAndDoesNotSave() {
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.createMaintenanceTask(99L, request);

        assertThat(result).isEmpty();
        verify(maintenanceTaskRepository, never()).save(any());
    }

    @Test
    void createMaintenanceTask_withoutIntervalAndNotOneTime_throwsInvalidMaintenanceIntervalException() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class)
                .intervalKm(null)
                .intervalMonths(null)
                .oneTime(false);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidMaintenanceIntervalException.class,
                () -> maintenanceTaskComponent.createMaintenanceTask(vehicle.getId(), request)
        )).hasMessageContaining("intervalKm or intervalMonths");

        verify(maintenanceTaskRepository, never()).save(any());
    }

    @Test
    void createMaintenanceTask_oneTimeWithoutInterval_savesAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class)
                .intervalKm(null)
                .intervalMonths(null)
                .oneTime(true);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.save(any(MaintenanceTask.class))).thenAnswer(invocation -> {
            MaintenanceTask toSave = invocation.getArgument(0);
            toSave.setId(10L);
            return toSave;
        });

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.createMaintenanceTask(vehicle.getId(), request);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(10L);
    }

    @Test
    void updateMaintenanceTask_found_updatesEntityAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask existing = Instancio.create(MaintenanceTask.class);
        existing.setVehicle(vehicle);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(existing.getId(), vehicle.getId())).thenReturn(Optional.of(existing));
        when(maintenanceTaskRepository.save(existing)).thenReturn(existing);

        Optional<MaintenanceTaskResponse> result =
                maintenanceTaskComponent.updateMaintenanceTask(vehicle.getId(), existing.getId(), request);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(request.getName());
        assertThat(existing.getName()).isEqualTo(request.getName());
    }

    @Test
    void updateMaintenanceTask_withoutIntervalAndNotOneTime_throwsInvalidMaintenanceIntervalException() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask existing = Instancio.create(MaintenanceTask.class);
        existing.setVehicle(vehicle);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class)
                .intervalKm(null)
                .intervalMonths(null)
                .oneTime(false);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(existing.getId(), vehicle.getId())).thenReturn(Optional.of(existing));

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                InvalidMaintenanceIntervalException.class,
                () -> maintenanceTaskComponent.updateMaintenanceTask(vehicle.getId(), existing.getId(), request)
        )).hasMessageContaining("intervalKm or intervalMonths");

        verify(maintenanceTaskRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceTask_taskNotFound_returnsEmptyOptionalAndDoesNotSave() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.updateMaintenanceTask(vehicle.getId(), 99L, request);

        assertThat(result).isEmpty();
        verify(maintenanceTaskRepository, never()).save(any());
    }

    @Test
    void deleteMaintenanceTask_found_deletesTaskAndReturnsTrue() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        task.setVehicle(vehicle);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(task.getId(), vehicle.getId())).thenReturn(Optional.of(task));

        boolean result = maintenanceTaskComponent.deleteMaintenanceTask(vehicle.getId(), task.getId());

        assertThat(result).isTrue();
        verify(maintenanceTaskRepository).deleteById(task.getId());
    }

    @Test
    void deleteMaintenanceTask_alreadyPerformed_throwsConflictAndDoesNotDelete() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        task.setVehicle(vehicle);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(task.getId(), vehicle.getId())).thenReturn(Optional.of(task));
        when(maintenanceLogTaskRepository.existsByTask_Id(task.getId())).thenReturn(true);

        assertThatThrownBy(() -> maintenanceTaskComponent.deleteMaintenanceTask(vehicle.getId(), task.getId()))
                .isInstanceOf(ConflictException.class);
        verify(maintenanceTaskRepository, never()).deleteById(any());
    }

    @Test
    void deleteMaintenanceTask_notFound_returnsFalseAndDoesNotDelete() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        boolean result = maintenanceTaskComponent.deleteMaintenanceTask(vehicle.getId(), 99L);

        assertThat(result).isFalse();
        verify(maintenanceTaskRepository, never()).deleteById(any());
    }

    @Test
    void generateMaintenanceChecklist_ordersByUrgency_mostUrgentFirst() {
        Vehicle vehicle = ownVehicle();
        MaintenanceTask ok = task(1L, vehicle, "ok");
        MaintenanceTask overdue = task(2L, vehicle, "overdue");
        MaintenanceTask soonLater = task(3L, vehicle, "soonLater");
        MaintenanceTask soonFirst = task(4L, vehicle, "soonFirst");
        MaintenanceTask noFraction = task(5L, vehicle, "noFraction");
        when(maintenanceTaskRepository.findAllByVehicle_Id(vehicle.getId()))
                .thenReturn(List.of(ok, overdue, soonLater, soonFirst, noFraction));
        status(ok, MaintenanceTaskStatus.OK, 0.5);
        status(overdue, MaintenanceTaskStatus.OVERDUE, -0.1);
        status(soonLater, MaintenanceTaskStatus.DUE_SOON, 0.08);
        status(soonFirst, MaintenanceTaskStatus.DUE_SOON, 0.02);
        status(noFraction, MaintenanceTaskStatus.OK, null);
        when(userLocaleComponent.localeOf(vehicle.getUser())).thenReturn(Locale.GERMANY);
        when(maintenanceChecklistPdfHelper.generate(eq(vehicle), any(), eq(LocalDate.of(2026, 10, 29)),
                eq(Locale.GERMANY))).thenReturn(new byte[]{1});
        when(maintenanceChecklistPdfHelper.fileName(vehicle, LocalDate.of(2026, 10, 29), Locale.GERMANY))
                .thenReturn("checkliste.pdf");

        Optional<MaintenanceChecklist> result =
                maintenanceTaskComponent.generateMaintenanceChecklist(vehicle.getId(), List.of(1L, 5L, 3L, 2L, 4L));

        assertThat(result).isPresent();
        assertThat(result.get().fileName()).isEqualTo("checkliste.pdf");
        assertThat(result.get().content()).containsExactly(1);
        assertThat(capturedEntries(vehicle)).extracting(MaintenanceChecklistEntry::name)
                .containsExactly("overdue", "soonFirst", "soonLater", "ok", "noFraction");
    }

    @Test
    void generateMaintenanceChecklist_duplicateIds_listTaskOnce() {
        Vehicle vehicle = ownVehicle();
        MaintenanceTask task = task(1L, vehicle, "only");
        when(maintenanceTaskRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(task));
        when(maintenanceChecklistPdfHelper.generate(any(), any(), any(), any())).thenReturn(new byte[]{1});

        maintenanceTaskComponent.generateMaintenanceChecklist(vehicle.getId(), List.of(1L, 1L));

        assertThat(capturedEntries(vehicle)).hasSize(1);
    }

    @Test
    void generateMaintenanceChecklist_taskOfOtherVehicle_isRejected() {
        Vehicle vehicle = ownVehicle();
        when(maintenanceTaskRepository.findAllByVehicle_Id(vehicle.getId()))
                .thenReturn(List.of(task(1L, vehicle, "own")));

        assertThatThrownBy(() -> maintenanceTaskComponent.generateMaintenanceChecklist(vehicle.getId(),
                List.of(1L, 77L))).isInstanceOf(InvalidTaskReferenceException.class);
    }

    @Test
    void generateMaintenanceChecklist_inactiveTask_isRejected() {
        Vehicle vehicle = ownVehicle();
        MaintenanceTask inactive = task(1L, vehicle, "inactive");
        inactive.setActive(false);
        when(maintenanceTaskRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(inactive));

        assertThatThrownBy(() -> maintenanceTaskComponent.generateMaintenanceChecklist(vehicle.getId(),
                List.of(1L))).isInstanceOf(InvalidTaskReferenceException.class);
    }

    @Test
    void generateMaintenanceChecklist_doneOneTimeTask_isRejected() {
        Vehicle vehicle = ownVehicle();
        MaintenanceTask done = task(1L, vehicle, "done");
        done.setOneTime(true);
        when(maintenanceTaskRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(done));
        status(done, MaintenanceTaskStatus.DONE, null);

        assertThatThrownBy(() -> maintenanceTaskComponent.generateMaintenanceChecklist(vehicle.getId(),
                List.of(1L))).isInstanceOf(InvalidTaskReferenceException.class);
    }

    @Test
    void generateMaintenanceChecklist_emptySelection_isRejected() {
        Vehicle vehicle = ownVehicle();

        assertThatThrownBy(() -> maintenanceTaskComponent.generateMaintenanceChecklist(vehicle.getId(), List.of()))
                .isInstanceOf(InvalidTaskReferenceException.class);
    }

    @Test
    void generateMaintenanceChecklist_vehicleNotFound_returnsEmptyOptional() {
        when(vehicleRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

        assertThat(maintenanceTaskComponent.generateMaintenanceChecklist(99L, List.of(1L))).isEmpty();
    }

    private Vehicle ownVehicle() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findByIdAndUser_Id(vehicle.getId(), USER_ID)).thenReturn(Optional.of(vehicle));
        return vehicle;
    }

    private static MaintenanceTask task(Long id, Vehicle vehicle, String name) {
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        task.setId(id);
        task.setVehicle(vehicle);
        task.setName(name);
        task.setActive(true);
        task.setOneTime(false);
        return task;
    }

    private void status(MaintenanceTask task, MaintenanceTaskStatus status, Double remainingFraction) {
        lenient().when(maintenanceTaskStatusHelper.compute(same(task), anyInt(), any(), any()))
                .thenReturn(new MaintenanceTaskStatusResult(status, null, remainingFraction, null, null, null, null));
    }

    @SuppressWarnings("unchecked")
    private List<MaintenanceChecklistEntry> capturedEntries(Vehicle vehicle) {
        ArgumentCaptor<List<MaintenanceChecklistEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(maintenanceChecklistPdfHelper).generate(eq(vehicle), entries.capture(), any(), any());
        return entries.getValue();
    }
}
