package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.AuthApi;
import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.api.model.UserRole;
import de.chriswohlbrecht.maintenance.component.IAuthComponent;
import de.chriswohlbrecht.maintenance.exception.AccountUnusableException;
import de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException;
import de.chriswohlbrecht.maintenance.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller mapping only; the security filter chain itself is covered by the Cucumber auth feature. */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String LOGIN_BODY = "{\"email\": \"rider@example.org\", \"password\": \"secret-password\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthComponent authComponent;

    @Test
    void login_success_returnsUser() throws Exception {
        when(authComponent.login(any(), any(), any())).thenReturn(user());

        mockMvc.perform(post(AuthApi.PATH_LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rider@example.org"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.language").value("de"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authComponent.login(any(), any(), any()))
                .thenThrow(new AuthenticationFailedException("Invalid email or password"));

        mockMvc.perform(post(AuthApi.PATH_LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(AuthApi.PATH_LOGIN));
    }

    @Test
    void login_unverified_returns403WithCode() throws Exception {
        when(authComponent.login(any(), any(), any())).thenThrow(new AccountUnusableException(
                AccountUnusableException.EMAIL_NOT_VERIFIED, "Email address is not verified"));

        mockMvc.perform(post(AuthApi.PATH_LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void login_locked_returns429WithRetryAfterRoundedUp() throws Exception {
        when(authComponent.login(any(), any(), any()))
                .thenThrow(new TooManyRequestsException(Duration.ofMillis(90_500)));

        mockMvc.perform(post(AuthApi.PATH_LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "91"));
    }

    @Test
    void login_invalidBody_returns400() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_returns204() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_LOGOUT))
                .andExpect(status().isNoContent());
        verify(authComponent).logout(any(), any());
    }

    @Test
    void getCurrentUser_returnsUser() throws Exception {
        when(authComponent.getCurrentUser()).thenReturn(user());

        mockMvc.perform(get(AuthApi.PATH_GET_CURRENT_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void register_notImplementedYet_returns501() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"new@example.org\", \"password\": \"long-enough-password\"}"))
                .andExpect(status().isNotImplemented());
    }

    private static UserResponse user() {
        return new UserResponse()
                .id(1L)
                .email("rider@example.org")
                .role(UserRole.USER)
                .active(true)
                .emailVerified(true)
                .language(Language.DE)
                .createdAt(OffsetDateTime.parse("2026-10-09T12:00:00+02:00"));
    }
}
