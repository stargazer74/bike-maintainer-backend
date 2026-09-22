package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.MaintenanceTasksApi;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.component.IMaintenanceTaskComponent;
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

@WebMvcTest(controllers = MaintenanceTaskController.class)
@ExtendWith(InstancioExtension.class)
class MaintenanceTaskControllerTest {

    private final JsonMapper objectMapper = new JsonMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IMaintenanceTaskComponent maintenanceTaskComponent;

    /**
     * Test case for listMaintenanceTasks.
     * Verifies that all tasks of an existing vehicle are returned with a 200 OK response.
     */
    @Test
    void testListMaintenanceTasksWithKnownVehicleIdShouldReturnOkResponse() throws Exception {
        MaintenanceTaskResponse response = Instancio.create(MaintenanceTaskResponse.class).id(10L).vehicleId(1L);
        when(maintenanceTaskComponent.listMaintenanceTasks(1L)).thenReturn(Optional.of(List.of(response)));

        mockMvc.perform(get(MaintenanceTasksApi.PATH_LIST_MAINTENANCE_TASKS, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].vehicleId").value(1))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1)).listMaintenanceTasks(1L);
    }

    /**
     * Test case for listMaintenanceTasks.
     * Verifies that a 404 NOT FOUND is returned when the vehicle does not exist.
     */
    @Test
    void testListMaintenanceTasksWithUnknownVehicleIdShouldReturnNotFound() throws Exception {
        when(maintenanceTaskComponent.listMaintenanceTasks(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get(MaintenanceTasksApi.PATH_LIST_MAINTENANCE_TASKS, 99L))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1)).listMaintenanceTasks(99L);
    }

    /**
     * Test case for getMaintenanceTask.
     * Verifies that an existing task is returned with a 200 OK response.
     */
    @Test
    void testGetMaintenanceTaskWithKnownIdShouldReturnOkResponse() throws Exception {
        MaintenanceTaskResponse response = Instancio.create(MaintenanceTaskResponse.class).id(10L).vehicleId(1L);
        when(maintenanceTaskComponent.getMaintenanceTask(1L, 10L)).thenReturn(Optional.of(response));

        mockMvc.perform(get(MaintenanceTasksApi.PATH_GET_MAINTENANCE_TASK, 1L, 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1)).getMaintenanceTask(1L, 10L);
    }

    /**
     * Test case for getMaintenanceTask.
     * Verifies that a 404 NOT FOUND is returned when the task does not exist.
     */
    @Test
    void testGetMaintenanceTaskWithUnknownIdShouldReturnNotFound() throws Exception {
        when(maintenanceTaskComponent.getMaintenanceTask(1L, 99L)).thenReturn(Optional.empty());

        mockMvc.perform(get(MaintenanceTasksApi.PATH_GET_MAINTENANCE_TASK, 1L, 99L))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1)).getMaintenanceTask(1L, 99L);
    }

    /**
     * Test case for createMaintenanceTask.
     * Verifies that a new task is created with a 201 CREATED response.
     */
    @Test
    void testCreateMaintenanceTaskWithKnownVehicleIdShouldReturnCreatedResponse() throws Exception {
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        MaintenanceTaskResponse response = Instancio.create(MaintenanceTaskResponse.class).id(10L).vehicleId(1L);
        when(maintenanceTaskComponent.createMaintenanceTask(eq(1L), any(MaintenanceTaskRequest.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(post(MaintenanceTasksApi.PATH_CREATE_MAINTENANCE_TASK, 1L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1))
                .createMaintenanceTask(eq(1L), any(MaintenanceTaskRequest.class));
    }

    /**
     * Test case for createMaintenanceTask.
     * Verifies that a 404 NOT FOUND is returned when the vehicle does not exist.
     */
    @Test
    void testCreateMaintenanceTaskWithUnknownVehicleIdShouldReturnNotFound() throws Exception {
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(maintenanceTaskComponent.createMaintenanceTask(eq(99L), any(MaintenanceTaskRequest.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(MaintenanceTasksApi.PATH_CREATE_MAINTENANCE_TASK, 99L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1))
                .createMaintenanceTask(eq(99L), any(MaintenanceTaskRequest.class));
    }

    /**
     * Test case for updateMaintenanceTask.
     * Verifies that an existing task is updated with a 200 OK response.
     */
    @Test
    void testUpdateMaintenanceTaskWithKnownIdShouldReturnOkResponse() throws Exception {
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        MaintenanceTaskResponse response = Instancio.create(MaintenanceTaskResponse.class).id(10L).vehicleId(1L);
        when(maintenanceTaskComponent.updateMaintenanceTask(eq(1L), eq(10L), any(MaintenanceTaskRequest.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(put(MaintenanceTasksApi.PATH_UPDATE_MAINTENANCE_TASK, 1L, 10L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1))
                .updateMaintenanceTask(eq(1L), eq(10L), any(MaintenanceTaskRequest.class));
    }

    /**
     * Test case for updateMaintenanceTask.
     * Verifies that a 404 NOT FOUND is returned when the task does not exist.
     */
    @Test
    void testUpdateMaintenanceTaskWithUnknownIdShouldReturnNotFound() throws Exception {
        MaintenanceTaskRequest request = Instancio.create(MaintenanceTaskRequest.class);
        when(maintenanceTaskComponent.updateMaintenanceTask(eq(1L), eq(99L), any(MaintenanceTaskRequest.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(put(MaintenanceTasksApi.PATH_UPDATE_MAINTENANCE_TASK, 1L, 99L)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1))
                .updateMaintenanceTask(eq(1L), eq(99L), any(MaintenanceTaskRequest.class));
    }

    /**
     * Test case for deleteMaintenanceTask.
     * Verifies that an existing task is deleted with a 204 NO CONTENT response.
     */
    @Test
    void testDeleteMaintenanceTaskWithKnownIdShouldReturnNoContent() throws Exception {
        when(maintenanceTaskComponent.deleteMaintenanceTask(1L, 10L)).thenReturn(true);

        mockMvc.perform(delete(MaintenanceTasksApi.PATH_DELETE_MAINTENANCE_TASK, 1L, 10L))
                .andExpect(status().isNoContent())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1)).deleteMaintenanceTask(1L, 10L);
    }

    /**
     * Test case for deleteMaintenanceTask.
     * Verifies that a 404 NOT FOUND is returned when the task does not exist.
     */
    @Test
    void testDeleteMaintenanceTaskWithUnknownIdShouldReturnNotFound() throws Exception {
        when(maintenanceTaskComponent.deleteMaintenanceTask(1L, 99L)).thenReturn(false);

        mockMvc.perform(delete(MaintenanceTasksApi.PATH_DELETE_MAINTENANCE_TASK, 1L, 99L))
                .andExpect(status().isNotFound())
                .andDo(MockMvcResultHandlers.print());

        Mockito.verify(maintenanceTaskComponent, Mockito.times(1)).deleteMaintenanceTask(1L, 99L);
    }
}
