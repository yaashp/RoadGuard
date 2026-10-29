package com.roadguard.dto;

import java.time.Instant;
import java.util.List;

public final class HazardDtos {
    private HazardDtos() {}

    public record NearbyHazardView(Long id, String type, String roadName, String severity, String status, double distanceMeters,
                                   int stillThere, int gone, String reportId, Instant reportedAt) {}

    public record VerifyRequest(String vote) {}

    public record VerifyResult(int stillThere, int gone, Instant lastVerifiedAt, String yourVote, String flag, String message) {}

    public record PublicTimelineEntry(String stage, Instant at) {}

    /** Public hazard detail. Coordinates are rounded (about 10 m); no reporter identity, e-mail or admin names are included. */
    public record HazardDetail(Long id, String reportId, String type, String severity, String status, String roadName,
                               double approxLat, double approxLng, String description, Instant reportedAt, Instant lastUpdatedAt,
                               int stillThere, int gone, Instant lastVerifiedAt, String flag, boolean sample,
                               boolean hasPhoto, boolean hasResolutionPhoto, List<PublicTimelineEntry> timeline,
                               boolean canVerify, String yourVote, String verifyBlockedReason) {}
}
