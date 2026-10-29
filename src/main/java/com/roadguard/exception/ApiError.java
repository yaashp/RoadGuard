package com.roadguard.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** The one JSON shape every error response uses. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String error, String code, String message,
                       String path, Map<String, String> fieldErrors, Object details) {
}
