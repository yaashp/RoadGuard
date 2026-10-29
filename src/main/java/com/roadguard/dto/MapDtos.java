package com.roadguard.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MapDtos {
    private MapDtos() {}

    /** A pin on the map. No personal data. `sample` is true for demo records. */
    public record RoadIssueView(Long id, String type, double lat, double lng, String roadName,
                                String severity, String status, int reportCount,
                                String source, boolean sample, Long complaintRef, String complaintId,
                                Instant createdAt, int stillThere, int gone, Instant lastVerifiedAt,
                                boolean hasPhoto, boolean hasResolutionPhoto, String flag) {}

    public record RoadIssueRequest(String type, Double lat, Double lng, String roadName, String severity, String status) {}

    public record CategoryInfo(String label, String color, String icon) {}

    public record MapConfig(String tileUrl, String attribution, int maxZoom, double centerLat, double centerLng, int zoom) {}

    public record ImageRules(long maxBytes, List<String> allowedTypes) {}

    /** Everything the front end needs to build forms, legends and the map without hard-coding it. */
    public record MetaResponse(List<CategoryInfo> categories, List<String> severities, List<String> statuses,
                               Map<String, List<String>> statusTransitions, MapConfig map, ImageRules image,
                               RouteDtos.ScoringModel scoringModel, String sampleDataNote, String disclaimer) {}
}
