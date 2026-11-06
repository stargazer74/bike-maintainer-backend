package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.DeviceLoginRequest;
import de.chriswohlbrecht.maintenance.api.model.DeviceLoginResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceResponse;
import de.chriswohlbrecht.maintenance.api.model.DeviceSessionRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * Apps that stay logged in (portal mode only): a device token, created only with email and password, is exchanged for
 * a normal session whenever the session expired; each exchange returns a new token.
 */
public interface IDeviceLoginComponent {

    /**
     * Verifies the credentials like the login, registers the device and starts a session marked with the device.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException wrong email or password
     * @throws de.chriswohlbrecht.maintenance.exception.AccountUnusableException     deactivated or unverified
     * @throws de.chriswohlbrecht.maintenance.exception.TooManyRequestsException     account temporarily locked
     */
    DeviceLoginResponse login(DeviceLoginRequest request, HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse);

    /**
     * Exchanges the device token for a new session and a new token. A token that was already exchanged removes the
     * device (it was probably copied).
     *
     * @throws de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException for every rejection alike
     */
    DeviceLoginResponse exchange(DeviceSessionRequest request, HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse);

    /**
     * Devices of the logged-in user, most recently used first.
     *
     * @param currentDeviceId device that started the requesting session, or {@code null} (browser session)
     */
    List<DeviceResponse> listDevices(Long currentDeviceId);

    /** Removes a device of the logged-in user and ends the sessions it started; false if unknown or foreign. */
    boolean deleteDevice(Long deviceId);

    /** Removes the device on logout from its session; unknown ids are ignored. */
    void revokeDevice(Long deviceId);

    /** Removes all devices of the user, e.g. after the password was changed or reset. */
    void revokeAllOf(Long userId);

    /** Deletes devices unused for longer than the idle timeout; returns how many. */
    int deleteUnused();
}
