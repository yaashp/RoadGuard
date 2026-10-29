package com.roadguard.model;

import java.util.Arrays;
import java.util.Optional;

/** Fixed topics for the community. Stored as the key, shown as the label. */
public enum CommunityCategory {
    ROAD_SAFETY("road-safety", "Road safety"),
    ACCIDENTS("accidents", "Accidents"),
    POTHOLES("potholes", "Potholes"),
    TRAFFIC("traffic", "Traffic"),
    CONSTRUCTION("construction", "Construction and diversions"),
    LIGHTING_SIGNALS("lighting-signals", "Lights and signals"),
    FLOODING("flooding", "Flooding"),
    OTHER("other", "Other");

    private final String key;
    private final String label;

    CommunityCategory(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() { return key; }
    public String label() { return label; }

    public static Optional<CommunityCategory> fromKey(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.key.equals(t) || c.label.equalsIgnoreCase(t)).findFirst();
    }
}
