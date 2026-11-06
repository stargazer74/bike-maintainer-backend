package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.AdminApi;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.api.model.UserRole;
import de.chriswohlbrecht.maintenance.component.IAdminUserComponent;
import de.chriswohlbrecht.maintenance.exception.InvalidUserUpdateException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller mapping only; role checks and the security chain are covered by the Cucumber admin feature. */
@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAdminUserComponent adminUserComponent;

    @Test
    void listUsers_returnsUsers() throws Exception {
        when(adminUserComponent.listUsers()).thenReturn(List.of(new UserResponse().id(7L).email("rider@example.org")
                .role(UserRole.USER).active(true)));

        mockMvc.perform(get(AdminApi.PATH_LIST_USERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("rider@example.org"));
    }

    @Test
    void updateUser_returnsUpdatedUser() throws Exception {
        when(adminUserComponent.updateUser(eq(7L), any())).thenReturn(Optional.of(new UserResponse().id(7L)
                .role(UserRole.USER).active(false)));

        mockMvc.perform(patch(AdminApi.PATH_UPDATE_USER, 7L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateUser_unknown_returns404() throws Exception {
        when(adminUserComponent.updateUser(eq(9L), any())).thenReturn(Optional.empty());

        mockMvc.perform(patch(AdminApi.PATH_UPDATE_USER, 9L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_ruleViolation_returns400() throws Exception {
        when(adminUserComponent.updateUser(eq(1L), any()))
                .thenThrow(new InvalidUserUpdateException("The last active admin cannot be demoted or deactivated"));

        mockMvc.perform(patch(AdminApi.PATH_UPDATE_USER, 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"USER\"}"))
                .andExpect(status().isBadRequest());
    }
}
