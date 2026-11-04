package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.component.IRateLimitComponent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/auth/login, login",
            "POST, /api/v1/auth/register, registration",
            "POST, /api/v1/auth/verify-email/resend, verification_resend",
            "POST, /api/v1/auth/password-reset/request, password_reset",
            "PUT, /api/v1/auth/password, password_change"
    })
    void passesGroupOfRequest(String method, String path, String group) throws Exception {
        IRateLimitComponent rateLimitComponent = mock(IRateLimitComponent.class);
        when(rateLimitComponent.tryConsume(anyString(), anyString())).thenReturn(Optional.empty());
        RateLimitFilter filter = new RateLimitFilter(rateLimitComponent, new SecurityErrorWriter(new JsonMapper()));
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("203.0.113.7");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimitComponent).tryConsume(eq(group), eq(path + "|203.0.113.7"));
    }
}
