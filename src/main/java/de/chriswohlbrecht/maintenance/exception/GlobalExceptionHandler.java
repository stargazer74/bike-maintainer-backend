package de.chriswohlbrecht.maintenance.exception;

import de.chriswohlbrecht.maintenance.api.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(HttpStatus.BAD_REQUEST, null, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException ex,
                                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponses.of(HttpStatus.UNAUTHORIZED, null, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccountUnusableException.class)
    public ResponseEntity<ErrorResponse> handleAccountUnusable(AccountUnusableException ex,
                                                                 HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponses.of(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex,
                                                                 HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ErrorResponses.retryAfterSeconds(ex.getRetryAfter())))
                .body(ErrorResponses.of(HttpStatus.TOO_MANY_REQUESTS, null, ex.getMessage(),
                        request.getRequestURI()));
    }
}
