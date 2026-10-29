package com.roadguard.dto;

import java.time.Instant;
import java.util.List;

public final class InsightDtos {
    private InsightDtos() {}

    // ---- impact score ----
    public record PointEventView(String type, int points, Instant at, String reference, String description) {}
    public record Badge(String id, String name, String description, boolean earned) {}
    public record ImpactSummary(int totalPoints, int pointsThisMonth, int validatedReports, int resolvedReports, int confirmations,
                                List<Badge> badges, List<PointEventView> events, boolean showOnLeaderboard) {}
    public record LeaderboardRow(int rank, String displayName, int points) {}
    public record Leaderboard(String month, List<LeaderboardRow> rows, String note) {}
    public record ImpactRules(List<String> rules, List<Badge> badges) {}

    // ---- admin insights ----
    public record PriorityItem(Long complaintDbId, String complaintId, Long pinId, String road, String category, String severity, String status,
                               int score, String bucket, List<String> factors, int stillThere, int gone, int ageDays, String flag, boolean sample) {}
    public record Hotspot(double lat, double lng, int openHazards, int highSeverity, String topCategory, List<String> roads) {}
    public record Counters(long totalReports, long activeHazards, long highPriority, long underReview, long verifiedResolutions, long flagged) {}
    public record Insights(Counters counters, List<PriorityItem> priorityQueue, List<Hotspot> hotspots, String disclaimer) {}
    public record AuditEntry(Instant at, String admin, String action, String complaintId, String detail) {}
    public record DismissRequest(String note) {}

    // ---- rule-based suggestions ----
    public record TextRequest(String text) {}
    public record DraftRequest(String category, String severity, String roadName, String landmark) {}
    public record CategorySuggestion(String category, List<String> matchedWords, String confidence, String method, String message) {}
    public record DescriptionDraft(String text, String method) {}
}
