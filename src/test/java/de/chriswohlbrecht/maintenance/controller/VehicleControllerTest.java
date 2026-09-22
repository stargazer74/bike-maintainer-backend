package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.VehiclesApi;
import de.chriswohlbrecht.maintenance.api.model.VehicleRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.component.VehicleComponent;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VehicleController.class)
@ExtendWith(InstancioExtension.class)
class VehicleControllerTest {

    private final JsonMapper objectMapper = new JsonMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleComponent vehicleComponent;

    /**
     * Test case for listVehicles.
     * Verifies that all vehicles are returned with a 200 OK response.
     */
    @Test
    void testListVehiclesWithDataShouldReturnOkResponse() throws Exception {
        VehicleResponse response = Instancio.create(VehicleResponse.class).id(1L).name("Bike");
        when(vehicleComponent.listVehicles()).thenReturn(List.of(response));

        mockMvc.perform(get(VehiclesApi.PATH_LIST_VEHICLES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Bike"))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(vehicleComponent, Mockito.times(1)).listVehicles();
    }

    /**
     * Test case for getVehicle.
     * Verifies that an existing vehicle is returned with a 200 OK response.
     */
    @Test
    void testGetVehicleWithKnownIdShouldReturnOkResponse() throws Exception {
        VehicleResponse response = Instancio.create(VehicleResponse.class).id(1L).name("Bike");
        when(vehicleComponent.getVehicle(1L)).thenReturn(Optional.of(response));

        mockMvc.perform(get(VehiclesApi.PATH_GET_VEHICLE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Bike"))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(vehicleComponent, Mockito.times(1)).getVehicle(1L);
    }

    /**
     * Test case for getVehicle.
     * Verifies that a 404 NOT FOUND is returned when the vehicle does not exist.
     */
    @Test
    void testGetVehicleWithUnknownIdShouldReturnNotFound() throws Exception {
        when(vehicleComponent.getVehicle(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get(VehiclesApi.PATH_GET_VEHICLE, 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/vehicles/99"))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(vehicleComponent, Mockito.times(1)).getVehicle(99L);
    }

    /**
     * Test case for createVehicle.
     * Verifies that a new vehicle is created with a 201 CREATED response.
     */
    @Test
    void testCreateVehicleWithValidDataShouldReturnCreatedResponse() throws Exception {
        // currentMileage is overridden because of the @Min(0) constraint; Instancio's default
        // Integer range may otherwise produce a negative value and fail bean validation.
        VehicleRequest request = Instancio.create(VehicleRequest.class).currentMileage(500);
        VehicleResponse response = Instancio.create(VehicleResponse.class).id(1L);
        when(vehicleComponent.createVehicle(any(VehicleRequest.class))).thenReturn(response);

        mockMvc.perform(post(VehiclesApi.PATH_CREATE_VEHICLE)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(vehicleComponent, Mockito.times(1)).createVehicle(any(VehicleRequest.class));
    }

    /**
     * Test case for createVehicle.
     * Verifies that a 400 BAD REQUEST is returned when required fields are missing.
     */
    @Test
    void testCreateVehicleWithMissingRequiredFieldsShouldReturnBadRequest() throws Exception {
        VehicleRequest invalidRequest = new VehicleRequest();

        mockMvc.perform(post(VehiclesApi.PATH_CREATE_VEHICLE)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Mockito.verify(vehicleComponent, Mockito.times(0)).createVehicle(any(VehicleRequest.class));
    }

    /**
     * Test case for updateVehicle.
     * Verifies that an existing vehicle is updated with a 200 OK response.
     */
    @Test
    void testUpdateVehicleWithKnownIdShouldReturnOkResponse() throws Exception {
        VehicleRequest request = Instancio.create(VehicleRequest.class).currentMileage(500);
        VehicleResponse response = Instancio.create(VehicleResponse.class).id(1L);
        when(vehicleComponent.updateVehicle(eq(1L), any(VehicleRequest.class))).thenReturn(Optional.of(response));

        mockMvc.perform(put(VehiclesApi.PATH_UPDATE_VEHICLE, 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(vehicleComponent, Mockito.times(1)).updateVehicle(eq(1L), any(VehicleRequest.class));
    }

    /**
     * Test case for updateVehicle.
     * Verifies that a 404 NOT FOUND is returned when the vehicle does not exist.
     */
    @Test
    void testUpdateVehicleWithUnknownIdShouldReturnNotFound() throws Exception {
        VehicleRequest request = Instancio.create(VehicleRequest.class).currentMileage(500);
        when(vehicleComponent.updateVehicle(eq(99L), any(VehicleRequest.class))).thenReturn(Optional.empty());

        mockMvc.perform(put(VehiclesApi.PATH_UPDATE_VEHICLE, 99L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(vehicleComponent, Mockito.times(1)).updateVehicle(eq(99L), any(VehicleRequest.class));
    }

    /**
     * Test case for deleteVehicle.
     * Verifies that an existing vehicle is deleted with a 204 NO CONTENT response.
     */
    @Test
    void testDeleteVehicleWithKnownIdShouldReturnNoContent() throws Exception {
        when(vehicleComponent.deleteVehicle(1L)).thenReturn(true);

        mockMvc.perform(delete(VehiclesApi.PATH_DELETE_VEHICLE, 1L))
                .andExpect(status().isNoContent())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(vehicleComponent, Mockito.times(1)).deleteVehicle(1L);
    }

    /**
     * Test case for deleteVehicle.
     * Verifies that a 404 NOT FOUND is returned when the vehicle does not exist.
     */
    @Test
    void testDeleteVehicleWithUnknownIdShouldReturnNotFound() throws Exception {
        when(vehicleComponent.deleteVehicle(99L)).thenReturn(false);

        mockMvc.perform(delete(VehiclesApi.PATH_DELETE_VEHICLE, 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(vehicleComponent, Mockito.times(1)).deleteVehicle(99L);
    }
}
