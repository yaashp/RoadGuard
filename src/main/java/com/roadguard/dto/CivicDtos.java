package com.roadguard.dto;

import java.time.Instant;
import java.util.List;

public final class CivicDtos {
    private CivicDtos() {}

    /** Authority loop: what the citizen sees. The response is shown only when staff have recorded one. */
    public record AuthorityView(String authorityName, String authorityResponse, Instant authorityResponseAt,
                                Instant letterExportedAt, String status, String disclaimer) {}

    public record AuthorityRequest(String authorityName) {}
    public record AuthorityResponseRequest(String response) {}

    /** Public repair-proof page. No reporter identity, no e-mail, coordinates rounded. */
    public record RepairPage(String reportId, Long hazardId, String hazardType, String severity, String roadName, String status,
                             double approxLat, double approxLng, String description, Instant reportedAt, Instant resolvedAt,
                             Long daysToResolve, boolean hasBeforePhoto, boolean hasAfterPhoto, boolean reopenedAfterResolution,
                             String authorityResponse, Instant authorityResponseAt, List<TimelineStep> timeline, String disclaimer) {}

    public record TimelineStep(String stage, Instant at, String note) {}

    public record FixedCard(String reportId, Long hazardId, String hazardType, String roadName, Instant resolvedAt, boolean hasAfterPhoto) {}

    /** "Your street": what the user's confirmations and reports have led to. */
    public record StreetCard(int confirmations, int confirmationsOnHazardsNowResolved, int confirmationsOnHazardsStillOpen,
                             int yourReportsResolved, int yourReportsOpen, List<StreetItem> items, String note) {}

    public record StreetItem(String reportId, String hazardType, String roadName, String outcome, Instant at) {}

    public record LetterView(String reportId, String text, String disclaimer) {}
}
