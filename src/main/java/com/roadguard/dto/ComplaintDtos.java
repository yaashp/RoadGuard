package com.roadguard.dto;

import java.time.Instant;
import java.util.List;

public final class ComplaintDtos {
    private ComplaintDtos() {}

    /** JSON body of POST /api/complaints. `image` (optional) is a base64 data URL, kept for backward compatibility. */
    public record CreateRequest(String issueType, String description, String image, Double lat, Double lng,
                                String address, String roadName, String severity,
                                String clientRequestId, Boolean allowDuplicate) {}

    public record DuplicateRequest(String duplicateOf, String note) {}

    public record StatusRequest(String status, String note) {}
    public record SeverityRequest(String severity, String note) {}

    public record ComplaintView(Long id, String complaintId, Long userId, String userName,
                                String issueType, String description,
                                double lat, double lng, String address, String roadName,
                                String severity, String status,
                                boolean hasImage, String imageUrl, boolean hasResolutionPhoto, String resolutionPhotoUrl,
                                String duplicateOf, boolean sample,
                                Instant createdAt, Instant updatedAt,
                                List<String> allowedNextStatuses) {}

    public record TimelineEntry(Long id, String eventType, String from, String to, String note,
                                String actor, Instant at) {}

    public record ComplaintDetail(ComplaintView complaint, List<TimelineEntry> timeline) {}
}
