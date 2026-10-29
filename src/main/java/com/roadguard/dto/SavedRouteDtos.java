package com.roadguard.dto;

import java.time.Instant;

public final class SavedRouteDtos {
    private SavedRouteDtos() {}

    public record SaveRequest(String name, RouteDtos.Point origin, RouteDtos.Point destination) {}
    public record RenameRequest(String name) {}

    public record SavedRouteView(Long id, String name,
                                 String originLabel, double originLat, double originLng,
                                 String destinationLabel, double destinationLat, double destinationLng,
                                 Instant createdAt,
                                 Integer lastScore, String lastClassification, Integer lastHazardCount,
                                 Double lastDistanceMeters, Double lastDurationSeconds,
                                 Instant lastAnalyzedAt, Integer previousScore) {}

    /** analysisAvailable=false means the routing service could not be reached when the route was saved. */
    public record SaveResponse(SavedRouteView route, boolean analysisAvailable, String analysisMessage) {}

    public record ReanalyzeResponse(SavedRouteView route, RouteDtos.RouteAnalysis analysis,
                                    Integer previousScore, int latestScore, Integer change) {}
}
