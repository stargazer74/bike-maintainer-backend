package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateResponse;
import de.chriswohlbrecht.maintenance.component.IVehicleTemplateComponent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VehicleTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class VehicleTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IVehicleTemplateComponent vehicleTemplateComponent;

    @Test
    void search_returnsTheSummaries() throws Exception {
        when(vehicleTemplateComponent.search(eq("honda"), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vehicle-templates").param("make", "honda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void search_withoutMake_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/vehicle-templates"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vehicleTemplateComponent);
    }

    @Test
    void search_withOneCharacterMake_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/vehicle-templates").param("make", "K"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vehicleTemplateComponent);
    }

    @Test
    void get_withUppercaseTemplateId_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/vehicle-templates/Honda-CB500F"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vehicleTemplateComponent);
    }

    @Test
    void get_ofAnUnknownTemplate_isNotFound() throws Exception {
        when(vehicleTemplateComponent.get("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/vehicle-templates/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void apply_returnsTheCounts() throws Exception {
        when(vehicleTemplateComponent.apply(eq(7L), any()))
                .thenReturn(Optional.of(new ApplyTemplateResponse().created(1).skipped(0)));

        mockMvc.perform(post("/api/v1/vehicles/7/template").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"honda-cb500f-2019\",\"tasks\":[{\"key\":\"brake-fluid\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    @Test
    void apply_withEmptyTasks_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles/7/template").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"honda-cb500f-2019\",\"tasks\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vehicleTemplateComponent);
    }

    @Test
    void apply_toAnUnknownVehicle_isNotFound() throws Exception {
        when(vehicleTemplateComponent.apply(eq(7L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/vehicles/7/template").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"honda-cb500f-2019\",\"tasks\":[{\"key\":\"brake-fluid\"}]}"))
                .andExpect(status().isNotFound());
    }
}
