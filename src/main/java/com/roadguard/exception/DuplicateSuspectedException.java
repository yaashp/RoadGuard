package com.roadguard.exception;

import org.springframework.http.HttpStatus;

/** A very similar open report already exists nearby. Carries the matches so the client can offer "confirm that one instead". */
public class DuplicateSuspectedException extends ApiException {
    private final Object matches;

    public DuplicateSuspectedException(Object matches) {
        super(HttpStatus.CONFLICT, "POSSIBLE_DUPLICATE",
                "A similar hazard was already reported very close to this spot. Please check whether it is the same one before submitting a new report.");
        this.matches = matches;
    }

    public Object getMatches() { return matches; }
}
