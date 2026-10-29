package com.roadguard.model;

/**
 * Whether a record was created by a real person using the app (USER) or inserted by the demo seeder (SAMPLE).
 * Rows created before this column existed have no value: complaints default to USER (the seeder re-labels
 * its own known demo rows), road issues default to SAMPLE (only the seeder ever created them).
 */
public enum DataOrigin {
    USER, SAMPLE;

    public static boolean isSample(String stored, DataOrigin whenMissing) {
        if (stored == null || stored.isBlank()) return whenMissing == SAMPLE;
        return SAMPLE.name().equalsIgnoreCase(stored);
    }
}
