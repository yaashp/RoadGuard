package com.roadguard.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Request failed validation; carries one message per offending field. */
public class ValidationException extends ApiException {
    private final Map<String, String> fieldErrors;

    public ValidationException(Map<String, String> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                fieldErrors.size() == 1 ? fieldErrors.values().iterator().next() : "Please fix the highlighted fields.");
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
