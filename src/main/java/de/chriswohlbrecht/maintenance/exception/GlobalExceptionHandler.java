package de.chriswohlbrecht.maintenance.exception;

import de.chriswohlbrecht.maintenance.api.model.ErrorResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleExport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String IMPORT_PATH = "/vehicles/import";
    private static final int MAX_DETAILS = 100;

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

    @ExceptionHandler(SubscriptionRequiredException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionRequired(SubscriptionRequiredException ex,
                                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponses.of(HttpStatus.FORBIDDEN, SubscriptionRequiredException.CODE, ex.getMessage(),
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

    @ExceptionHandler(WrongPasswordException.class)
    public ResponseEntity<ErrorResponse> handleWrongPassword(WrongPasswordException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponses.of(HttpStatus.FORBIDDEN, WrongPasswordException.CODE, ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(LastAdminException.class)
    public ResponseEntity<ErrorResponse> handleLastAdmin(LastAdminException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponses.of(HttpStatus.CONFLICT, LastAdminException.CODE, ex.getMessage(),
                        request.getRequestURI()));
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
        if (ex.getTarget() instanceof VehicleExport export) {
            return importFileInvalid(ex, export, request);
        }
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

    @ExceptionHandler(ImportInvalidException.class)
    public ResponseEntity<ErrorResponse> handleImportInvalid(ImportInvalidException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(importInvalid(ex.getMessage(), ex.getDetails(), request));
    }

    @ExceptionHandler(ImportFormatUnsupportedException.class)
    public ResponseEntity<ErrorResponse> handleImportFormatUnsupported(ImportFormatUnsupportedException ex,
                                                                         HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(HttpStatus.BAD_REQUEST, ImportFormatUnsupportedException.CODE,
                        ex.getMessage(), request.getRequestURI()));
    }

    /**
     * Unreadable bodies (no JSON, wrong types, unknown enum values) stay a plain 400, except for an import file
     * ({@code IMPORT_INVALID}) and a body over the size limit (413).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex,
                                                             HttpServletRequest request) {
        if (causedByTooLargeBody(ex)) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                    .body(ErrorResponses.of(HttpStatus.CONTENT_TOO_LARGE, null, "Request body too large",
                            request.getRequestURI()));
        }
        String message = "Request body is not readable";
        if (request.getRequestURI().endsWith(IMPORT_PATH)) {
            return ResponseEntity.badRequest().body(importInvalid(message, List.of("file: not a valid export file"),
                    request));
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponses.of(HttpStatus.BAD_REQUEST, null, message, request.getRequestURI()));
    }

    /** A file of an unknown format version is not checked further: a newer format may follow other rules. */
    private ResponseEntity<ErrorResponse> importFileInvalid(MethodArgumentNotValidException ex, VehicleExport export,
                                                             HttpServletRequest request) {
        Integer version = export.getFormatVersion();
        if (version != null && version != ImportFormatUnsupportedException.SUPPORTED_VERSION) {
            return handleImportFormatUnsupported(new ImportFormatUnsupportedException(version), request);
        }
        List<String> details = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage()),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(error -> error.getObjectName() + ": " + error.getDefaultMessage()))
                .sorted()
                .toList();
        return ResponseEntity.badRequest().body(importInvalid("Invalid import file", details, request));
    }

    private static ErrorResponse importInvalid(String message, List<String> details, HttpServletRequest request) {
        return ErrorResponses.of(HttpStatus.BAD_REQUEST, ImportInvalidException.CODE, message, request.getRequestURI())
                .details(details.stream().limit(MAX_DETAILS).toList());
    }

    private static boolean causedByTooLargeBody(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PayloadTooLargeException) {
                return true;
            }
        }
        return false;
    }
}
