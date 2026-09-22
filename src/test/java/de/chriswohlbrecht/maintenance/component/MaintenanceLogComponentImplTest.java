package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceLogOutcome;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class MaintenanceLogComponentImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MaintenanceLogRepository maintenanceLogRepository;

    @Mock
    private MaintenanceLogTaskRepository maintenanceLogTaskRepository;

    @Mock
    private MaintenanceTaskRepository maintenanceTaskRepository;

    private MaintenanceLogComponentImpl maintenanceLogComponent;

    @BeforeEach
    void setUp() {
        maintenanceLogComponent = new MaintenanceLogComponentImpl(
                vehicleRepository, maintenanceLogRepository, maintenanceLogTaskRepository,
                maintenanceTaskRepository, new MaintenanceLogMapperImpl());
    }

    @Test
    void listMaintenanceLogs_vehicleFound_returnsMappedLogs() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog log = Instancio.create(MaintenanceLog.class);
        log.setVehicle(vehicle);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(log));
        when(maintenanceLogTaskRepository.findAllByLog_Id(log.getId())).thenReturn(List.of());

        Optional<List<MaintenanceLogResponse>> result = maintenanceLogComponent.listMaintenanceLogs(vehicle.getId());

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).getVehicleId()).isEqualTo(vehicle.getId());
    }

    @Test
    void listMaintenanceLogs_vehicleNotFound_returnsEmptyOptional() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

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
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(log.getId(), vehicle.getId())).thenReturn(Optional.of(log));
        when(maintenanceLogTaskRepository.findAllByLog_Id(log.getId())).thenReturn(List.of(link));

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.getMaintenanceLog(vehicle.getId(), log.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPerformedTaskIds()).containsExactly(task.getId());
    }

    @Test
    void getMaintenanceLog_notFound_returnsEmptyOptional() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        Optional<MaintenanceLogResponse> result = maintenanceLogComponent.getMaintenanceLog(vehicle.getId(), 99L);

        assertThat(result).isEmpty();
    }

    @Test
    void createMaintenanceLog_withoutTasks_savesLogAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(List.of());
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class))).thenAnswer(invocation -> {
            MaintenanceLog toSave = invocation.getArgument(0);
            toSave.setId(20L);
            return toSave;
        });
        when(maintenanceLogTaskRepository.findAllByLog_Id(20L)).thenReturn(List.of());

        MaintenanceLogOutcome outcome = maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request);

        assertThat(outcome).isInstanceOf(MaintenanceLogOutcome.Saved.class);
        MaintenanceLogResponse response = ((MaintenanceLogOutcome.Saved) outcome).response();
        assertThat(response.getId()).isEqualTo(20L);
        assertThat(response.getVehicleId()).isEqualTo(vehicle.getId());
        verify(maintenanceLogTaskRepository, never()).save(any());
        verify(maintenanceLogTaskRepository, never()).deleteAllByLog_Id(any());
    }

    @Test
    void createMaintenanceLog_withValidTaskIds_linksTasks() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(List.of(task.getId()));
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(task.getId(), vehicle.getId())).thenReturn(Optional.of(task));
        when(maintenanceLogRepository.save(any(MaintenanceLog.class))).thenAnswer(invocation -> {
            MaintenanceLog toSave = invocation.getArgument(0);
            toSave.setId(20L);
            return toSave;
        });
        when(maintenanceLogTaskRepository.findAllByLog_Id(20L)).thenReturn(List.of());

        MaintenanceLogOutcome outcome = maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request);

        assertThat(outcome).isInstanceOf(MaintenanceLogOutcome.Saved.class);
        verify(maintenanceLogTaskRepository).save(argThat(link ->
                link.getTask().equals(task) && link.getLog().getId().equals(20L)));
    }

    @Test
    void createMaintenanceLog_withUnknownTaskId_returnsInvalidTaskReferenceAndDoesNotSave() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(List.of(999L));
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(999L, vehicle.getId())).thenReturn(Optional.empty());

        MaintenanceLogOutcome outcome = maintenanceLogComponent.createMaintenanceLog(vehicle.getId(), request);

        assertThat(outcome).isInstanceOf(MaintenanceLogOutcome.InvalidTaskReference.class);
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void createMaintenanceLog_vehicleNotFound_returnsNotFoundAndDoesNotSave() {
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class).mileageAtPerformed(12000);
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        MaintenanceLogOutcome outcome = maintenanceLogComponent.createMaintenanceLog(99L, request);

        assertThat(outcome).isInstanceOf(MaintenanceLogOutcome.NotFound.class);
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceLog_found_replacesTaskLinksAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = Instancio.create(MaintenanceLog.class);
        existing.setVehicle(vehicle);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(List.of());
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(existing.getId(), vehicle.getId())).thenReturn(Optional.of(existing));
        when(maintenanceLogRepository.save(existing)).thenReturn(existing);
        when(maintenanceLogTaskRepository.findAllByLog_Id(existing.getId())).thenReturn(List.of());

        MaintenanceLogOutcome outcome =
                maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), existing.getId(), request);

        assertThat(outcome).isInstanceOf(MaintenanceLogOutcome.Saved.class);
        MaintenanceLogResponse response = ((MaintenanceLogOutcome.Saved) outcome).response();
        assertThat(response.getMileageAtPerformed()).isEqualTo(12000);
        verify(maintenanceLogTaskRepository).deleteAllByLog_Id(existing.getId());
    }

    @Test
    void updateMaintenanceLog_notFound_returnsNotFoundAndDoesNotSave() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class).mileageAtPerformed(12000);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        MaintenanceLogOutcome outcome = maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), 99L, request);

        assertThat(outcome).isInstanceOf(MaintenanceLogOutcome.NotFound.class);
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceLog_withUnknownTaskId_returnsInvalidTaskReferenceAndDoesNotSave() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog existing = Instancio.create(MaintenanceLog.class);
        existing.setVehicle(vehicle);
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(List.of(999L));
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(existing.getId(), vehicle.getId())).thenReturn(Optional.of(existing));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(999L, vehicle.getId())).thenReturn(Optional.empty());

        MaintenanceLogOutcome outcome =
                maintenanceLogComponent.updateMaintenanceLog(vehicle.getId(), existing.getId(), request);

        assertThat(outcome).isInstanceOf(MaintenanceLogOutcome.InvalidTaskReference.class);
        verify(maintenanceLogRepository, never()).save(any());
    }

    @Test
    void deleteMaintenanceLog_found_deletesLinksAndLogAndReturnsTrue() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceLog log = Instancio.create(MaintenanceLog.class);
        log.setVehicle(vehicle);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(log.getId(), vehicle.getId())).thenReturn(Optional.of(log));

        boolean result = maintenanceLogComponent.deleteMaintenanceLog(vehicle.getId(), log.getId());

        assertThat(result).isTrue();
        verify(maintenanceLogTaskRepository).deleteAllByLog_Id(log.getId());
        verify(maintenanceLogRepository).deleteById(log.getId());
    }

    @Test
    void deleteMaintenanceLog_notFound_returnsFalseAndDoesNotDelete() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceLogRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        boolean result = maintenanceLogComponent.deleteMaintenanceLog(vehicle.getId(), 99L);

        assertThat(result).isFalse();
        verify(maintenanceLogRepository, never()).deleteById(any());
    }
}
