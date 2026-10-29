package com.roadguard.model;

import java.util.Arrays;
import java.util.Optional;

public enum Severity {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3);

    private final String label;
    private final int rank;

    Severity(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() { return label; }
    public int rank() { return rank; }

    public static Optional<Severity> fromLabel(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim();
        return Arrays.stream(values()).filter(s -> s.label.equalsIgnoreCase(t)).findFirst();
    }
}
