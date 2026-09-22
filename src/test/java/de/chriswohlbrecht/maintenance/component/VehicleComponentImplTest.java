package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.VehicleRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.mapper.VehicleMapperImpl;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
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
class VehicleComponentImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    private VehicleComponentImpl vehicleComponent;

    @BeforeEach
    void setUp() {
        vehicleComponent = new VehicleComponentImpl(vehicleRepository, new VehicleMapperImpl());
    }

    @Test
    void listVehicles_returnsAllVehiclesMapped() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle));

        List<VehicleResponse> result = vehicleComponent.listVehicles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(vehicle.getId());
        assertThat(result.get(0).getName()).isEqualTo(vehicle.getName());
    }

    @Test
    void getVehicle_found_returnsMappedResponse() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        Optional<VehicleResponse> result = vehicleComponent.getVehicle(vehicle.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(vehicle.getName());
    }

    @Test
    void getVehicle_notFound_returnsEmptyOptional() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<VehicleResponse> result = vehicleComponent.getVehicle(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void createVehicle_savesEntityAndReturnsMappedResponse() {
        VehicleRequest request = Instancio.create(VehicleRequest.class).currentMileage(500);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle toSave = invocation.getArgument(0);
            toSave.setId(1L);
            return toSave;
        });

        VehicleResponse response = vehicleComponent.createVehicle(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo(request.getName());
        assertThat(response.getCurrentMileage()).isEqualTo(500);
    }

    @Test
    void updateVehicle_found_updatesEntityAndReturnsMappedResponse() {
        Vehicle existing = Instancio.create(Vehicle.class);
        VehicleRequest request = Instancio.create(VehicleRequest.class).currentMileage(500);
        when(vehicleRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(vehicleRepository.save(existing)).thenReturn(existing);

        Optional<VehicleResponse> result = vehicleComponent.updateVehicle(existing.getId(), request);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo(request.getName());
        assertThat(existing.getName()).isEqualTo(request.getName());
    }

    @Test
    void updateVehicle_notFound_returnsEmptyOptionalAndDoesNotSave() {
        VehicleRequest request = Instancio.create(VehicleRequest.class).currentMileage(500);
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<VehicleResponse> result = vehicleComponent.updateVehicle(99L, request);

        assertThat(result).isEmpty();
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void deleteVehicle_found_deletesEntityAndReturnsTrue() {
        Vehicle vehicle = Instancio.create(Vehicle.class);
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        boolean result = vehicleComponent.deleteVehicle(vehicle.getId());

        assertThat(result).isTrue();
        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    void deleteVehicle_notFound_returnsFalseAndDoesNotDelete() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        boolean result = vehicleComponent.deleteVehicle(99L);

        assertThat(result).isFalse();
        verify(vehicleRepository, never()).delete(any());
    }
}
