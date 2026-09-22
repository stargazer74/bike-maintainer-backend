package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.mapper.MaintenanceTaskMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class MaintenanceTaskComponentImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private MaintenanceTaskRepository maintenanceTaskRepository;

    private MaintenanceTaskComponentImpl maintenanceTaskComponent;

    @BeforeEach
    void setUp() {
        maintenanceTaskComponent = new MaintenanceTaskComponentImpl(
                vehicleRepository, maintenanceTaskRepository, new MaintenanceTaskMapperImpl());
    }

    @Test
    void listMaintenanceTasks_vehicleFound_returnsMappedTasks() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        task.setVehicle(vehicle);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findAllByVehicle_Id(vehicle.getId())).thenReturn(List.of(task));

        Optional<List<MaintenanceTaskResponse>> result = maintenanceTaskComponent.listMaintenanceTasks(vehicle.getId());

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).getVehicleId()).isEqualTo(vehicle.getId());
        assertThat(result.get().get(0).getName()).isEqualTo(task.getName());
    }

    @Test
    void listMaintenanceTasks_vehicleNotFound_returnsEmptyOptional() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<List<MaintenanceTaskResponse>> result = maintenanceTaskComponent.listMaintenanceTasks(99L);

        assertThat(result).isEmpty();
        verify(maintenanceTaskRepository, never()).findAllByVehicle_Id(any());
    }

    @Test
    void getMaintenanceTask_found_returnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask task = Instancio.create(MaintenanceTask.class);
        task.setVehicle(vehicle);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(task.getId(), vehicle.getId())).thenReturn(Optional.of(task));

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.getMaintenanceTask(vehicle.getId(), task.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(task.getId());
    }

    @Test
    void getMaintenanceTask_vehicleNotFound_returnsEmptyOptionalWithoutQueryingTask() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.getMaintenanceTask(99L, 1L);

        assertThat(result).isEmpty();
        verify(maintenanceTaskRepository, never()).findByIdAndVehicle_Id(any(), any());
    }

    @Test
    void getMaintenanceTask_taskNotFound_returnsEmptyOptional() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.getMaintenanceTask(vehicle.getId(), 99L);

        assertThat(result).isEmpty();
    }

    @Test
    void createMaintenanceTask_vehicleFound_savesAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
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
    }

    @Test
    void createMaintenanceTask_vehicleNotFound_returnsEmptyOptionalAndDoesNotSave() {
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<MaintenanceTaskResponse> result = maintenanceTaskComponent.createMaintenanceTask(99L, request);

        assertThat(result).isEmpty();
        verify(maintenanceTaskRepository, never()).save(any());
    }

    @Test
    void updateMaintenanceTask_found_updatesEntityAndReturnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTask existing = Instancio.create(MaintenanceTask.class);
        existing.setVehicle(vehicle);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(existing.getId(), vehicle.getId())).thenReturn(Optional.of(existing));
        when(maintenanceTaskRepository.save(existing)).thenReturn(existing);

        Optional<MaintenanceTaskResponse> result =
                maintenanceTaskComponent.updateMaintenanceTask(vehicle.getId(), existing.getId(), request);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(request.getName());
        assertThat(existing.getName()).isEqualTo(request.getName());
    }

    @Test
    void updateMaintenanceTask_taskNotFound_returnsEmptyOptionalAndDoesNotSave() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
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
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(task.getId(), vehicle.getId())).thenReturn(Optional.of(task));

        boolean result = maintenanceTaskComponent.deleteMaintenanceTask(vehicle.getId(), task.getId());

        assertThat(result).isTrue();
        verify(maintenanceTaskRepository).deleteById(task.getId());
    }

    @Test
    void deleteMaintenanceTask_notFound_returnsFalseAndDoesNotDelete() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceTaskRepository.findByIdAndVehicle_Id(99L, vehicle.getId())).thenReturn(Optional.empty());

        boolean result = maintenanceTaskComponent.deleteMaintenanceTask(vehicle.getId(), 99L);

        assertThat(result).isFalse();
        verify(maintenanceTaskRepository, never()).deleteById(any());
    }
}
