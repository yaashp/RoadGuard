package com.roadguard.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.Map;

/**
 * Central error handling. Extending ResponseEntityExceptionHandler means every standard Spring MVC
 * failure (wrong method, wrong content type, missing parameter, unknown URL ...) ends up in
 * handleExceptionInternal, where it is converted to the same ApiError JSON as our own exceptions.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidation(ValidationException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req.getRequestURI(), ex.getFieldErrors());
    }

    @ExceptionHandler(DuplicateSuspectedException.class)
    public ResponseEntity<Object> handleDuplicate(DuplicateSuspectedException ex, HttpServletRequest req) {
        ApiError body = new ApiError(Instant.now(), ex.getStatus().value(), ex.getStatus().getReasonPhrase(), ex.getCode(),
                ex.getMessage(), req.getRequestURI(), null, ex.getMatches());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req.getRequestURI(), null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleMultipart(MultipartException ex, HttpServletRequest req) {
        log.warn("Multipart request rejected: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "UPLOAD_FAILED",
                "The upload could not be read. Make sure you sent a photo and try again.", req.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on the server. Please try again.", req.getRequestURI(), null);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String path = request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message;
        String code = status.name();
        if (ex instanceof MaxUploadSizeExceededException) {   // handled by the base class (HTTP 413)
            code = "FILE_TOO_LARGE";
            message = "The uploaded file is too large. Please choose a smaller photo.";
        } else if (ex instanceof HttpMessageNotReadableException) {
            code = "MALFORMED_BODY";
            message = "The request body is missing or malformed (check JSON syntax and number formats).";
        } else if (ex instanceof TypeMismatchException tm) {
            code = "INVALID_PARAMETER";
            message = "Invalid value for '" + tm.getPropertyName() + "'.";
        } else if (ex instanceof MissingServletRequestParameterException mp) {
            code = "MISSING_PARAMETER";
            message = "Missing required parameter '" + mp.getParameterName() + "'.";
        } else if (ex instanceof ErrorResponse er && er.getBody().getDetail() != null) {
            message = er.getBody().getDetail();
        } else {
            message = status.getReasonPhrase();
        }
        return build(status, code, message, path, null);
    }

    private ResponseEntity<Object> build(HttpStatus status, String code, String message, String path,
                                         Map<String, String> fieldErrors) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path,
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors, null);
        return ResponseEntity.status(status).body(body);
    }
}
