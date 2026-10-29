package com.roadguard.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class HistoryDtos {
    private HistoryDtos() {}

    public record RoadSummary(String road, long totalComplaints, long openComplaints, long resolvedComplaints,
                              long highSeverity, Instant lastReportedAt, boolean includesSample) {}

    public record HistoryRecord(String complaintId, String category, String severity, String status,
                                String address, Instant createdAt, Instant resolvedAt, boolean sample) {}

    public record ResolutionStats(long resolved, long rejected, long open, Double resolutionRatePercent,
                                  Double avgDaysToResolve) {}

    public record VolumeChange(int periodDays, long current, long previous, Double changePercent, String trend) {}

    public record RepeatedIssue(String category, long count, Instant firstReported, Instant lastReported) {}

    public record RoadHistory(RoadSummary summary, Map<String, Long> byCategory, Map<String, Long> bySeverity,
                              List<DashboardDtos.TrendPoint> weekly, List<DashboardDtos.TrendPoint> monthly,
                              List<DashboardDtos.SeverityTrendPoint> severityMonthly,
                              ResolutionStats resolution, VolumeChange recentChange,
                              List<RepeatedIssue> repeatedIssues, List<HistoryRecord> records,
                              boolean includesSampleData, String dataNote) {}

    public record Hotspot(String road, long totalComplaints, long openComplaints, long highSeverity,
                          String topCategory, Instant lastReportedAt) {}

    public record PeriodStats(Instant from, Instant to, long complaints, long highSeverity, long resolved) {}

    public record PeriodComparison(String road, int periodDays, PeriodStats current, PeriodStats previous,
                                   Double changePercent, String trend) {}
}
