package com.roadguard.model;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lifecycle of a complaint. The database stores the human-readable label (e.g. "Under Review"),
 * which keeps the pre-existing rows valid. All transition rules live here so the API, the admin UI
 * and the tests share one definition.
 */
public enum ComplaintStatus {
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under Review"),
    ASSIGNED("Action Planned"),
    IN_PROGRESS("Repair in Progress"),
    RESOLVED("Resolved"),
    REJECTED("Rejected"),
    DUPLICATE("Duplicate");

    private static final Map<ComplaintStatus, List<ComplaintStatus>> NEXT = new EnumMap<>(ComplaintStatus.class);

    static {
        NEXT.put(SUBMITTED, List.of(UNDER_REVIEW, REJECTED, DUPLICATE));
        NEXT.put(UNDER_REVIEW, List.of(ASSIGNED, IN_PROGRESS, REJECTED, DUPLICATE));
        NEXT.put(ASSIGNED, List.of(IN_PROGRESS, UNDER_REVIEW, REJECTED));
        NEXT.put(IN_PROGRESS, List.of(RESOLVED, ASSIGNED, UNDER_REVIEW));
        NEXT.put(RESOLVED, List.of(IN_PROGRESS, UNDER_REVIEW));   // re-open after the community reports it still there
        NEXT.put(REJECTED, List.of(UNDER_REVIEW));         // reconsider
        NEXT.put(DUPLICATE, List.of(UNDER_REVIEW));        // un-merge
    }

    private final String label;

    ComplaintStatus(String label) { this.label = label; }

    public String label() { return label; }

    public List<ComplaintStatus> allowedNext() { return NEXT.get(this); }

    public boolean canMoveTo(ComplaintStatus target) { return allowedNext().contains(target); }

    /** Open = still a live problem on the road (counts on the map and in route scoring). */
    public boolean isOpen() { return this != RESOLVED && this != REJECTED && this != DUPLICATE; }

    /** Needs a written explanation from the administrator. */
    public boolean needsExplanation() { return this == REJECTED || this == DUPLICATE; }

    public static Optional<ComplaintStatus> fromLabel(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim();
        // "Assigned" / "In Progress" are the labels used before the lifecycle was renamed; still accepted
        if (t.equalsIgnoreCase("Assigned")) return Optional.of(ASSIGNED);
        if (t.equalsIgnoreCase("In Progress")) return Optional.of(IN_PROGRESS);
        return Arrays.stream(values()).filter(s -> s.label.equalsIgnoreCase(t) || s.name().equalsIgnoreCase(t)).findFirst();
    }
}
