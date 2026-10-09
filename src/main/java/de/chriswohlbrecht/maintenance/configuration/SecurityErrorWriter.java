package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.exception.ErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** Writes JSON {@code ErrorResponse} bodies from security filters, where no controller advice applies. */
class SecurityErrorWriter {

    private final JsonMapper jsonMapper;

    SecurityErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(),
                ErrorResponses.of(status, null, message, request.getRequestURI()));
    }
}
