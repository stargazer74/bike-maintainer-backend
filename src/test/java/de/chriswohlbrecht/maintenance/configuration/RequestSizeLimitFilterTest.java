package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.exception.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestSizeLimitFilterTest {

    private static final String IMPORT = "/api/v1/vehicles/import";
    private static final int LIMIT = (int) RequestSizeLimitFilter.IMPORT_LIMIT;

    private final RequestSizeLimitFilter filter =
            new RequestSizeLimitFilter(new SecurityErrorWriter(new JsonMapper()));

    @Test
    void declaredLengthAboveTheLimit_isRejectedBeforeTheBodyIsRead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", IMPORT);
        request.setContent(new byte[LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("Request body too large");
        assertThat(calls).hasValue(0);
    }

    @Test
    void bodyWithoutDeclaredLength_failsOnceMoreThanTheLimitIsRead() {
        MockHttpServletRequest request = chunked(new byte[LIMIT + 1]);
        FilterChain readAll = (req, res) -> req.getInputStream().readAllBytes();

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), readAll))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void bodyUpToTheLimit_isPassedOn() throws Exception {
        MockHttpServletRequest request = chunked(new byte[LIMIT]);
        AtomicInteger read = new AtomicInteger();
        FilterChain readAll = (req, res) -> {
            ServletInputStream stream = req.getInputStream();
            assertThat(req.getInputStream()).isSameAs(stream);
            read.set(stream.readAllBytes().length);
        };

        filter.doFilter(request, new MockHttpServletResponse(), readAll);

        assertThat(read).hasValue(LIMIT);
    }

    /** The webhook of the payment provider is public, so its body is limited far below the import. */
    @Test
    void webhookAboveItsLimit_isRejected() throws Exception {
        int webhookLimit = (int) RequestSizeLimitFilter.WEBHOOK_LIMIT;
        MockHttpServletRequest tooLarge = new MockHttpServletRequest("POST", "/api/v1/webhooks/paddle");
        tooLarge.setContent(new byte[webhookLimit + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(tooLarge, response, (req, res) -> calls.incrementAndGet());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(calls).hasValue(0);
        FilterChain readAll = (req, res) -> req.getInputStream().readAllBytes();
        assertThatThrownBy(() -> filter.doFilter(chunked("/api/v1/webhooks/paddle", new byte[webhookLimit + 1]),
                new MockHttpServletResponse(), readAll)).isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void otherRequests_areNotLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/vehicles");
        request.setContent(new byte[LIMIT + 1]);
        AtomicInteger read = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> read.set(req.getInputStream().readAllBytes().length));

        assertThat(read).hasValue(LIMIT + 1);
    }

    @Test
    void limitedStream_countsSingleBytesAndDelegatesTheRest() throws IOException {
        ServletInputStream delegate = mock(ServletInputStream.class);
        when(delegate.read()).thenReturn(7, 8, -1);
        when(delegate.isFinished()).thenReturn(true);
        when(delegate.isReady()).thenReturn(true);
        ReadListener listener = mock(ReadListener.class);
        RequestSizeLimitFilter.LimitedInputStream stream = new RequestSizeLimitFilter.LimitedInputStream(delegate, 1);

        assertThat(stream.read()).isEqualTo(7);
        assertThatThrownBy(stream::read).isInstanceOf(PayloadTooLargeException.class);
        assertThat(stream.isFinished()).isTrue();
        assertThat(stream.isReady()).isTrue();
        stream.setReadListener(listener);
        verify(delegate).setReadListener(listener);
    }

    @Test
    void limitedStream_passesTheEndOfTheBodyOn() throws IOException {
        ServletInputStream delegate = mock(ServletInputStream.class);
        when(delegate.read()).thenReturn(-1);
        when(delegate.read(new byte[4], 0, 4)).thenReturn(-1);
        RequestSizeLimitFilter.LimitedInputStream stream = new RequestSizeLimitFilter.LimitedInputStream(delegate, 1);

        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.read(new byte[4], 0, 4)).isEqualTo(-1);
    }

    /** As a client that sends the body chunked: the length is unknown up front. */
    private static MockHttpServletRequest chunked(byte[] body) {
        return chunked(IMPORT, body);
    }

    private static MockHttpServletRequest chunked(String path, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }
}
