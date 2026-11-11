package de.chriswohlbrecht.maintenance.exception;

import de.chriswohlbrecht.maintenance.api.model.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The parts of the handler that a web slice cannot reach easily. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aBodyOverTheSizeLimit_isAnswered413_evenIfWrapped() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("I/O error",
                new UncheckedIOException(new PayloadTooLargeException(5)), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> response = handler.handleNotReadable(ex, request("/api/v1/vehicles/import"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().getStatus()).isEqualTo(413);
    }

    @Test
    void anUnreadableBodyOfAnotherEndpoint_staysAPlain400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad",
                new IOException("bad"), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> response = handler.handleNotReadable(ex, request("/api/v1/vehicles"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isNull();
        assertThat(response.getBody().getDetails()).isNullOrEmpty();
    }

    @Test
    void importDetails_areCutAt100() {
        List<String> details = IntStream.range(0, 150).mapToObj(i -> "problem " + i).toList();

        ResponseEntity<ErrorResponse> response = handler.handleImportInvalid(new ImportInvalidException(details),
                request("/api/v1/vehicles/import"));

        assertThat(response.getBody().getDetails()).hasSize(100).startsWith("problem 0");
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        return request;
    }
}
