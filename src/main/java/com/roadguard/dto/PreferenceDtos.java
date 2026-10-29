package com.roadguard.dto;

public final class PreferenceDtos {
    private PreferenceDtos() {}

    /** All fields optional in an update: only the ones sent are changed. */
    public record PreferenceRequest(String theme, Boolean notifyOnStatusChange, Boolean notifyOnSubmission,
                                    Boolean mapShowResolved, Boolean showSampleData, Boolean showOnLeaderboard,
                                    String language) {}

    public record PreferenceView(String theme, boolean notifyOnStatusChange, boolean notifyOnSubmission,
                                 boolean mapShowResolved, boolean showSampleData, boolean showOnLeaderboard,
                                 String language) {}
}
