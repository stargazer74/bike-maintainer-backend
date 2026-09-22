package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.MaintenanceLogsApi;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.component.MaintenanceLogComponent;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceLogOutcome;
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

@WebMvcTest(controllers = MaintenanceLogController.class)
@ExtendWith(InstancioExtension.class)
class MaintenanceLogControllerTest {

    private final JsonMapper objectMapper = new JsonMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MaintenanceLogComponent maintenanceLogComponent;

    /**
     * Test case for listMaintenanceLogs.
     * Verifies that all logs of an existing vehicle are returned with a 200 OK response.
     */
    @Test
    void testListMaintenanceLogsWithKnownVehicleIdShouldReturnOkResponse() throws Exception {
        MaintenanceLogResponse response = Instancio.create(MaintenanceLogResponse.class).id(20L).vehicleId(1L);
        when(maintenanceLogComponent.listMaintenanceLogs(1L)).thenReturn(Optional.of(List.of(response)));

        mockMvc.perform(get(MaintenanceLogsApi.PATH_LIST_MAINTENANCE_LOGS, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].vehicleId").value(1))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1)).listMaintenanceLogs(1L);
    }

    /**
     * Test case for listMaintenanceLogs.
     * Verifies that a 404 NOT FOUND is returned when the vehicle does not exist.
     */
    @Test
    void testListMaintenanceLogsWithUnknownVehicleIdShouldReturnNotFound() throws Exception {
        when(maintenanceLogComponent.listMaintenanceLogs(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get(MaintenanceLogsApi.PATH_LIST_MAINTENANCE_LOGS, 99L))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1)).listMaintenanceLogs(99L);
    }

    /**
     * Test case for getMaintenanceLog.
     * Verifies that an existing log is returned with a 200 OK response.
     */
    @Test
    void testGetMaintenanceLogWithKnownIdShouldReturnOkResponse() throws Exception {
        MaintenanceLogResponse response = Instancio.create(MaintenanceLogResponse.class)
                .id(20L)
                .vehicleId(1L)
                .performedTaskIds(List.of(10L));
        when(maintenanceLogComponent.getMaintenanceLog(1L, 20L)).thenReturn(Optional.of(response));

        mockMvc.perform(get(MaintenanceLogsApi.PATH_GET_MAINTENANCE_LOG, 1L, 20L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.performedTaskIds[0]").value(10))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1)).getMaintenanceLog(1L, 20L);
    }

    /**
     * Test case for getMaintenanceLog.
     * Verifies that a 404 NOT FOUND is returned when the log does not exist.
     */
    @Test
    void testGetMaintenanceLogWithUnknownIdShouldReturnNotFound() throws Exception {
        when(maintenanceLogComponent.getMaintenanceLog(1L, 99L)).thenReturn(Optional.empty());

        mockMvc.perform(get(MaintenanceLogsApi.PATH_GET_MAINTENANCE_LOG, 1L, 99L))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1)).getMaintenanceLog(1L, 99L);
    }

    /**
     * Test case for createMaintenanceLog.
     * Verifies that a new log is created with a 201 CREATED response.
     */
    @Test
    void testCreateMaintenanceLogWithKnownVehicleIdShouldReturnCreatedResponse() throws Exception {
        // mileageAtPerformed is overridden because of the @Min(0) constraint; Instancio's default
        // Integer range may otherwise produce a negative value and fail bean validation.
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class).mileageAtPerformed(12000);
        MaintenanceLogResponse response = Instancio.create(MaintenanceLogResponse.class).id(20L).vehicleId(1L);
        when(maintenanceLogComponent.createMaintenanceLog(eq(1L), any(MaintenanceLogRequest.class)))
                .thenReturn(new MaintenanceLogOutcome.Saved(response));

        mockMvc.perform(post(MaintenanceLogsApi.PATH_CREATE_MAINTENANCE_LOG, 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1))
                .createMaintenanceLog(eq(1L), any(MaintenanceLogRequest.class));
    }

    /**
     * Test case for createMaintenanceLog.
     * Verifies that a 404 NOT FOUND is returned when the vehicle does not exist.
     */
    @Test
    void testCreateMaintenanceLogWithUnknownVehicleIdShouldReturnNotFound() throws Exception {
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class).mileageAtPerformed(12000);
        when(maintenanceLogComponent.createMaintenanceLog(eq(99L), any(MaintenanceLogRequest.class)))
                .thenReturn(new MaintenanceLogOutcome.NotFound());

        mockMvc.perform(post(MaintenanceLogsApi.PATH_CREATE_MAINTENANCE_LOG, 99L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1))
                .createMaintenanceLog(eq(99L), any(MaintenanceLogRequest.class));
    }

    /**
     * Test case for createMaintenanceLog.
     * Verifies that a 400 BAD REQUEST is returned when a referenced task id is invalid.
     */
    @Test
    void testCreateMaintenanceLogWithInvalidTaskIdShouldReturnBadRequest() throws Exception {
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class)
                .mileageAtPerformed(12000)
                .performedTaskIds(List.of(999L));
        when(maintenanceLogComponent.createMaintenanceLog(eq(1L), any(MaintenanceLogRequest.class)))
                .thenReturn(new MaintenanceLogOutcome.InvalidTaskReference());

        mockMvc.perform(post(MaintenanceLogsApi.PATH_CREATE_MAINTENANCE_LOG, 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1))
                .createMaintenanceLog(eq(1L), any(MaintenanceLogRequest.class));
    }

    /**
     * Test case for updateMaintenanceLog.
     * Verifies that an existing log is updated with a 200 OK response.
     */
    @Test
    void testUpdateMaintenanceLogWithKnownIdShouldReturnOkResponse() throws Exception {
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class).mileageAtPerformed(12000);
        MaintenanceLogResponse response = Instancio.create(MaintenanceLogResponse.class).id(20L).vehicleId(1L);
        when(maintenanceLogComponent.updateMaintenanceLog(eq(1L), eq(20L), any(MaintenanceLogRequest.class)))
                .thenReturn(new MaintenanceLogOutcome.Saved(response));

        mockMvc.perform(put(MaintenanceLogsApi.PATH_UPDATE_MAINTENANCE_LOG, 1L, 20L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1))
                .updateMaintenanceLog(eq(1L), eq(20L), any(MaintenanceLogRequest.class));
    }

    /**
     * Test case for updateMaintenanceLog.
     * Verifies that a 404 NOT FOUND is returned when the log does not exist.
     */
    @Test
    void testUpdateMaintenanceLogWithUnknownIdShouldReturnNotFound() throws Exception {
        MaintenanceLogRequest request = Instancio.create(MaintenanceLogRequest.class).mileageAtPerformed(12000);
        when(maintenanceLogComponent.updateMaintenanceLog(eq(1L), eq(99L), any(MaintenanceLogRequest.class)))
                .thenReturn(new MaintenanceLogOutcome.NotFound());

        mockMvc.perform(put(MaintenanceLogsApi.PATH_UPDATE_MAINTENANCE_LOG, 1L, 99L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1))
                .updateMaintenanceLog(eq(1L), eq(99L), any(MaintenanceLogRequest.class));
    }

    /**
     * Test case for deleteMaintenanceLog.
     * Verifies that an existing log is deleted with a 204 NO CONTENT response.
     */
    @Test
    void testDeleteMaintenanceLogWithKnownIdShouldReturnNoContent() throws Exception {
        when(maintenanceLogComponent.deleteMaintenanceLog(1L, 20L)).thenReturn(true);

        mockMvc.perform(delete(MaintenanceLogsApi.PATH_DELETE_MAINTENANCE_LOG, 1L, 20L))
                .andExpect(status().isNoContent())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1)).deleteMaintenanceLog(1L, 20L);
    }

    /**
     * Test case for deleteMaintenanceLog.
     * Verifies that a 404 NOT FOUND is returned when the log does not exist.
     */
    @Test
    void testDeleteMaintenanceLogWithUnknownIdShouldReturnNotFound() throws Exception {
        when(maintenanceLogComponent.deleteMaintenanceLog(1L, 99L)).thenReturn(false);

        mockMvc.perform(delete(MaintenanceLogsApi.PATH_DELETE_MAINTENANCE_LOG, 1L, 99L))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceLogComponent, Mockito.times(1)).deleteMaintenanceLog(1L, 99L);
    }
}
