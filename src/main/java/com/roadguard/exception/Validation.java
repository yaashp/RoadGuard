package com.roadguard.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny collector so a service can report every problem with a request at once. */
public final class Validation {
    private final Map<String, String> errors = new LinkedHashMap<>();

    public Validation check(boolean ok, String field, String message) {
        if (!ok) errors.putIfAbsent(field, message);
        return this;
    }

    public Validation fail(String field, String message) {
        errors.putIfAbsent(field, message);
        return this;
    }

    public boolean hasErrors() { return !errors.isEmpty(); }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }
}
