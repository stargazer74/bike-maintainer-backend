package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.AuthApi;
import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.DeviceLoginResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceResponse;
import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.PasswordResetConfirmRequest;
import de.chriswohlbrecht.maintenance.api.model.RegisterRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.api.model.UserRole;
import de.chriswohlbrecht.maintenance.component.IAccountDeletionComponent;
import de.chriswohlbrecht.maintenance.component.DeviceLoginComponentImpl;
import de.chriswohlbrecht.maintenance.component.IAuthComponent;
import de.chriswohlbrecht.maintenance.component.IDeviceLoginComponent;
import de.chriswohlbrecht.maintenance.component.IPasswordComponent;
import de.chriswohlbrecht.maintenance.component.IRegistrationComponent;
import de.chriswohlbrecht.maintenance.exception.AccountUnusableException;
import de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException;
import de.chriswohlbrecht.maintenance.exception.InvalidUserUpdateException;
import de.chriswohlbrecht.maintenance.exception.LastAdminException;
import de.chriswohlbrecht.maintenance.exception.TooManyRequestsException;
import de.chriswohlbrecht.maintenance.exception.WrongPasswordException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @MockitoBean
    private IRegistrationComponent registrationComponent;

    @MockitoBean
    private IPasswordComponent passwordComponent;

    @MockitoBean
    private IAccountDeletionComponent accountDeletionComponent;

    @MockitoBean
    private IDeviceLoginComponent deviceLoginComponent;

    @Test
    void deviceLogin_returnsUserAndToken() throws Exception {
        when(deviceLoginComponent.login(any(), any(), any())).thenReturn(new DeviceLoginResponse()
                .user(new UserResponse().email("rider@example.org").role(UserRole.USER))
                .deviceToken("t".repeat(43)));

        mockMvc.perform(post(AuthApi.PATH_DEVICE_LOGIN).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"email\": \"rider@example.org\", \"password\": \"secret-password\", \"deviceName\": \"Pixel 8\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("rider@example.org"))
                .andExpect(jsonPath("$.deviceToken").value("t".repeat(43)));
    }

    @Test
    void deviceLogin_blankDeviceName_returns400() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_DEVICE_LOGIN).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"email\": \"rider@example.org\", \"password\": \"secret-password\", \"deviceName\": \"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deviceLoginComponent);
    }

    @Test
    void deviceLogin_nameWithEmoji_isAccepted() throws Exception {
        when(deviceLoginComponent.login(any(), any(), any())).thenReturn(new DeviceLoginResponse()
                .user(new UserResponse().email("rider@example.org")).deviceToken("t".repeat(43)));

        mockMvc.perform(post(AuthApi.PATH_DEVICE_LOGIN).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"email\": \"rider@example.org\", \"password\": \"secret-password\", \"deviceName\": \"Pixel 🏍\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deviceSession_invalidToken_returns401() throws Exception {
        when(deviceLoginComponent.exchange(any(), any(), any()))
                .thenThrow(new AuthenticationFailedException("Invalid device token"));

        mockMvc.perform(post(AuthApi.PATH_CREATE_DEVICE_SESSION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceToken\": \"" + "t".repeat(43) + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deviceSession_tokenOfWrongLength_returns400() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_CREATE_DEVICE_SESSION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceToken\": \"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deviceLoginComponent);
    }

    @Test
    void listDevices_passesTheDeviceOfTheSession() throws Exception {
        when(deviceLoginComponent.listDevices(3L)).thenReturn(List.of(new DeviceResponse().id(3L).name("Pixel 8")
                .current(true)));

        mockMvc.perform(get(AuthApi.PATH_LIST_DEVICES)
                        .sessionAttr(DeviceLoginComponentImpl.SESSION_ATTRIBUTE, 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Pixel 8"))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void listDevices_fromBrowserSession_passesNull() throws Exception {
        when(deviceLoginComponent.listDevices(null)).thenReturn(List.of());

        mockMvc.perform(get(AuthApi.PATH_LIST_DEVICES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteDevice_returns204OrNotFound() throws Exception {
        when(deviceLoginComponent.deleteDevice(3L)).thenReturn(true);

        mockMvc.perform(delete(AuthApi.PATH_DELETE_DEVICE, 3L)).andExpect(status().isNoContent());
        mockMvc.perform(delete(AuthApi.PATH_DELETE_DEVICE, 9L)).andExpect(status().isNotFound());
    }

    @Test
    void deleteCurrentUser_deletesAndLogsOut() throws Exception {
        mockMvc.perform(delete(AuthApi.PATH_DELETE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"secret-password\"}"))
                .andExpect(status().isNoContent());

        verify(accountDeletionComponent).deleteCurrentUser(any(), any());
        verify(authComponent).logout(any(), any());
    }

    @Test
    void deleteCurrentUser_wrongPassword_returns403WithCodeAndStaysLoggedIn() throws Exception {
        doThrow(new WrongPasswordException("Password is wrong"))
                .when(accountDeletionComponent).deleteCurrentUser(any(), any());

        mockMvc.perform(delete(AuthApi.PATH_DELETE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"wrong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

        verifyNoInteractions(authComponent);
    }

    @Test
    void deleteCurrentUser_lastAdmin_returns409WithCode() throws Exception {
        doThrow(new LastAdminException("last admin"))
                .when(accountDeletionComponent).deleteCurrentUser(any(), any());

        mockMvc.perform(delete(AuthApi.PATH_DELETE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"secret-password\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void deleteCurrentUser_missingPassword_returns400() throws Exception {
        mockMvc.perform(delete(AuthApi.PATH_DELETE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountDeletionComponent);
    }

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
    void updateCurrentUser_returnsUpdatedUser() throws Exception {
        when(authComponent.updateCurrentUser(any())).thenReturn(user().language(Language.EN));

        mockMvc.perform(patch(AuthApi.PATH_UPDATE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\": \"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"));
    }

    @Test
    void updateCurrentUser_currencyOnly_returnsUpdatedUser() throws Exception {
        when(authComponent.updateCurrentUser(any())).thenReturn(user().currency(Currency.CHF));

        mockMvc.perform(patch(AuthApi.PATH_UPDATE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\": \"CHF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CHF"));
    }

    @Test
    void updateCurrentUser_unsupportedCurrency_returns400() throws Exception {
        mockMvc.perform(patch(AuthApi.PATH_UPDATE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\": \"JPY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCurrentUser_unsupportedLanguage_returns400() throws Exception {
        mockMvc.perform(patch(AuthApi.PATH_UPDATE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\": \"fr\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCurrentUser_emptyBody_returns400() throws Exception {
        when(authComponent.updateCurrentUser(any())).thenThrow(new InvalidUserUpdateException("empty"));

        mockMvc.perform(patch(AuthApi.PATH_UPDATE_CURRENT_USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_delegatesWithAcceptLanguage_returns202() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .content("{\"email\": \"new@example.org\", \"password\": \"long-enough-password\", "
                                + "\"altcha\": \"solved\"}"))
                .andExpect(status().isAccepted());

        verify(registrationComponent).register(any(RegisterRequest.class), eq("en-US"));
    }

    @Test
    void register_missingAltcha_returns400CaptchaInvalid() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"new@example.org\", \"password\": \"long-enough-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
    }

    @Test
    void register_invalidEmail_returns400WithoutCode() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"long-enough-password\", "
                                + "\"altcha\": \"solved\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void getAuthChallenge_passesLibraryJsonOn_withNoStore() throws Exception {
        when(registrationComponent.createChallengeJson())
                .thenReturn("{\"parameters\":{\"algorithm\":\"PBKDF2/SHA-256\",\"expiresAt\":1761660000},"
                        + "\"signature\":\"abc\"}");

        mockMvc.perform(get(AuthApi.PATH_GET_AUTH_CHALLENGE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.signature").value("abc"))
                .andExpect(jsonPath("$.parameters.algorithm").value("PBKDF2/SHA-256"))
                .andExpect(jsonPath("$.parameters.expiresAt").value(1761660000));
    }

    @Test
    void confirmPasswordReset_delegatesAndReturns204() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_CONFIRM_PASSWORD_RESET).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"some-token\", \"newPassword\": \"long-enough-password\"}"))
                .andExpect(status().isNoContent());

        verify(passwordComponent).confirmReset(new PasswordResetConfirmRequest()
                .token("some-token").newPassword("long-enough-password"));
    }

    @Test
    void confirmPasswordReset_tooShortPassword_returns400() throws Exception {
        mockMvc.perform(post(AuthApi.PATH_CONFIRM_PASSWORD_RESET).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"some-token\", \"newPassword\": \"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordComponent);
    }

    private static UserResponse user() {
        return new UserResponse()
                .id(1L)
                .email("rider@example.org")
                .role(UserRole.USER)
                .active(true)
                .emailVerified(true)
                .language(Language.DE)
                .currency(Currency.EUR)
                .createdAt(OffsetDateTime.parse("2026-10-09T12:00:00+02:00"));
    }
}
