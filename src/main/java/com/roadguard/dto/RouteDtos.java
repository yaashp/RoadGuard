package com.roadguard.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RouteDtos {
    private RouteDtos() {}

    /** A point in a request. Boxed so that "missing" can be told apart from 0. */
    public record Point(Double lat, Double lng, String label) {}

    public record AnalyzeRequest(Point origin, Point destination, Boolean includeSample) {}

    /** A hazard as loaded from the database for scoring. */
    public record HazardPoint(Long id, String category, String severity, String status,
                              double lat, double lng, String roadName, boolean sample,
                              Instant createdAt, int stillThere, int gone) {}

    public record NearbyHazard(Long id, String category, String severity, String status,
                               double lat, double lng, String roadName,
                               double distanceMeters, double penalty, int reportsMerged, boolean sample,
                               int ageDays, int stillThere, int gone, double recencyFactor, double confidenceFactor) {}

    public record SafetyAssessment(int score, String classification, double totalPenalty, int hazardCount,
                                   int duplicateReportsMerged, double exposurePerKm,
                                   Map<String, Integer> hazardsBySeverity,
                                   List<NearbyHazard> hazards, List<String> explanation) {}

    /** One candidate route. geometry is a simplified [lat, lng] polyline for drawing; scoring uses the full geometry. */
    public record RouteOption(int index, double distanceMeters, double durationSeconds,
                              List<double[]> geometry, SafetyAssessment safety, boolean recommended) {}

    public record ScoringModel(double lowPenalty, double mediumPenalty, double highPenalty,
                               double nearMeters, double bufferMeters, double edgeFactor,
                               double inProgressFactor, double duplicateMeters,
                               int safeMinScore, int moderateMinScore, String description) {}

    /** Links that open the same start and destination in other apps. Those apps choose their own route, which may differ from ours. */
    public record ExternalNavigation(String googleMapsUrl, String openStreetMapUrl, String note) {}

    public record RouteAnalysis(Point origin, Point destination, List<RouteOption> routes, int recommendedIndex,
                                String routingProvider, int hazardsConsidered, int sampleHazardsConsidered,
                                boolean includesSampleData, ScoringModel model, String disclaimer,
                                String recommendationReason, List<String> warnings, ExternalNavigation externalNavigation) {}

    public record GeocodeResult(String label, double lat, double lng, String type) {}
}
