package de.chriswohlbrecht.maintenance.exception;

import de.chriswohlbrecht.maintenance.api.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

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

    @ExceptionHandler(MailQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleMailQuotaExceeded(MailQuotaExceededException ex,
                                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ErrorResponses.retryAfterSeconds(ex.getRetryAfter())))
                .body(ErrorResponses.of(HttpStatus.TOO_MANY_REQUESTS, MailQuotaExceededException.CODE,
                        ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CaptchaInvalidException.class)
    public ResponseEntity<ErrorResponse> handleCaptchaInvalid(CaptchaInvalidException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(HttpStatus.BAD_REQUEST, CaptchaInvalidException.CODE, ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException ex,
                                                                 HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(HttpStatus.BAD_REQUEST, null, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(HttpStatus.BAD_REQUEST, null, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Invalid request bodies. A missing or malformed {@code altcha} field is reported as {@code CAPTCHA_INVALID} like
     * an invalid solution, so clients handle all captcha problems the same way.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                        HttpServletRequest request) {
        boolean captchaInvalid = ex.getBindingResult().getFieldErrors().stream()
                .anyMatch(error -> "altcha".equals(error.getField()));
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(HttpStatus.BAD_REQUEST, captchaInvalid ? CaptchaInvalidException.CODE : null,
                        message, request.getRequestURI()));
    }
}
