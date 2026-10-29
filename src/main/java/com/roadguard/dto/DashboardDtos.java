package com.roadguard.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record Totals(long totalReportedIssues, long totalComplaints, long openComplaints,
                         long underReview, long resolved, long rejected, long highSeverityOpen) {}

    public record TrendPoint(String label, LocalDate start, long reported, long resolved) {}

    public record SeverityTrendPoint(String label, long low, long medium, long high) {}

    public record RecentReport(String complaintId, String category, String roadName, String severity,
                               String status, boolean sample, Instant createdAt) {}

    public record ActivityItem(String complaintId, String eventType, String description, boolean sample, Instant at) {}

    public record RoadCount(String road, long openIssues, long highSeverity, long totalReports) {}

    public record ConditionStats(Double resolutionRatePercent, Double avgDaysToResolve, long resolvedWithTimeline,
                                 List<RoadCount> roadsNeedingAttention) {}

    /** Public, aggregated numbers only: no names, e-mails or other personal data. */
    public record Summary(Totals totals, Map<String, Long> byStatus, Map<String, Long> byCategory,
                          Map<String, Long> bySeverity, List<TrendPoint> weekly, List<TrendPoint> monthly,
                          List<SeverityTrendPoint> severityMonthly,
                          List<RecentReport> recentReports, List<ActivityItem> recentActivity,
                          ConditionStats conditions, boolean includesSampleData, long sampleComplaintCount,
                          long userComplaintCount, Instant generatedAt) {}

    public record MyDashboard(long total, long open, long resolved, long rejected,
                              Map<String, Long> byStatus, List<ComplaintDtos.ComplaintView> recent,
                              long unreadNotifications, long savedRoutes) {}
}
