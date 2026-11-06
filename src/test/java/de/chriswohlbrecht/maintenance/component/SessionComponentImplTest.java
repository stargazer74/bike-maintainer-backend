package de.chriswohlbrecht.maintenance.component;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionComponentImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void invalidateSessionsOfDeviceKeepsOtherSessions() {
        FindByIndexNameSessionRepository<MapSession> repository = mock(FindByIndexNameSessionRepository.class);
        MapSession deviceSession = session("device", 3L);
        MapSession otherDevice = session("other-device", 4L);
        MapSession browser = session("browser", null);
        when(repository.findByPrincipalName("7")).thenReturn(Map.of(
                "device", deviceSession, "other-device", otherDevice, "browser", browser));
        SessionComponentImpl component = new SessionComponentImpl(null, null, repository);

        component.invalidateSessionsOfDevice(7L, 3L);

        verify(repository).deleteById("device");
        verify(repository, never()).deleteById("other-device");
        verify(repository, never()).deleteById("browser");
    }

    private static MapSession session(String id, Long deviceId) {
        MapSession session = new MapSession(id);
        if (deviceId != null) {
            session.setAttribute(DeviceLoginComponentImpl.SESSION_ATTRIBUTE, deviceId);
        }
        return session;
    }
}
