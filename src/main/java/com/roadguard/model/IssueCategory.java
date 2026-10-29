package com.roadguard.model;

import java.util.Arrays;
import java.util.Optional;

/** Report categories. The colour and icon are presentation hints sent to the map via /api/meta. */
public enum IssueCategory {
    POTHOLE("Pothole", "#C2410C", "🕳️"),
    ROAD_DAMAGE("Road Damage", "#92400E", "🛣️"),
    ACCIDENT("Accident", "#B91C1C", "🚨"),
    WATERLOGGING("Waterlogging", "#0369A1", "🌊"),
    STREETLIGHT("Broken Streetlight", "#A16207", "💡"),
    TRAFFIC_SIGNAL("Damaged Traffic Signal", "#6D28D9", "🚦"),
    CONSTRUCTION("Road Construction", "#CA8A04", "🚧"),
    DEBRIS("Debris on Road", "#B45309", "🧱"),
    OBSTRUCTION("Road Obstruction / Fallen Tree", "#166534", "🌳"),
    TRAFFIC_HAZARD("Traffic Hazard", "#9D174D", "⚠️"),
    OTHER("Other", "#57534E", "📍");

    private final String label;
    private final String color;
    private final String icon;

    IssueCategory(String label, String color, String icon) {
        this.label = label;
        this.color = color;
        this.icon = icon;
    }

    public String label() { return label; }
    public String color() { return color; }
    public String icon() { return icon; }

    /** Accepts current labels plus the short names used by the original sample data. */
    public static Optional<IssueCategory> fromLabel(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim();
        if (t.equalsIgnoreCase("Construction")) return Optional.of(CONSTRUCTION);
        if (t.equalsIgnoreCase("Hazard")) return Optional.of(TRAFFIC_HAZARD);
        if (t.equalsIgnoreCase("Fallen Tree / Obstruction")) return Optional.of(OBSTRUCTION);
        return Arrays.stream(values()).filter(c -> c.label.equalsIgnoreCase(t)).findFirst();
    }
}
