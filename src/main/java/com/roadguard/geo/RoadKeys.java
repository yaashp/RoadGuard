package com.roadguard.geo;

import java.util.Locale;

/**
 * Turns the free-text road name / address of a complaint into a stable label and grouping key, so
 * "Marine Drive, Mumbai" and "marine drive" count as the same road in Road History.
 * Grouping is by name only (the first comma-separated part); it is a practical approximation, not a road database.
 */
public final class RoadKeys {
    private RoadKeys() {}

    /** Display label: roadName if given, else the first part of the address, else "Unnamed location near lat, lng". */
    public static String label(String roadName, String address, double lat, double lng) {
        String candidate = firstPart(roadName);
        if (candidate.isEmpty()) candidate = firstPart(address);
        if (candidate.isEmpty() || candidate.matches("^-?\\d+([.,]\\d+)?$")) {
            return String.format(Locale.ROOT, "Unnamed location near %.3f, %.3f", lat, lng);
        }
        return candidate;
    }

    /** Case/punctuation-insensitive key used for grouping and searching. */
    public static String key(String label) {
        if (label == null) return "";
        return label.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String firstPart(String text) {
        if (text == null) return "";
        String t = text.trim();
        int comma = t.indexOf(',');
        if (comma >= 0) t = t.substring(0, comma).trim();
        return t;
    }
}
