package com.roadguard.service;

import com.roadguard.dto.DashboardDtos.SeverityTrendPoint;
import com.roadguard.dto.DashboardDtos.TrendPoint;
import com.roadguard.geo.RoadKeys;
import com.roadguard.model.Complaint;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.ComplaintStatusHistory;
import com.roadguard.model.IssueCategory;
import com.roadguard.model.Severity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Pure aggregation helpers shared by the dashboard, admin overview and road history. */
final class Analytics {
    private Analytics() {}

    static String category(Complaint c) {
        return IssueCategory.fromLabel(c.getIssueType()).map(IssueCategory::label).orElse(c.getIssueType());
    }

    static String status(Complaint c) {
        return ComplaintStatus.fromLabel(c.getStatus()).map(ComplaintStatus::label).orElse(c.getStatus());
    }

    static String severity(Complaint c) {
        return Severity.fromLabel(c.getSeverity()).map(Severity::label).orElse(c.getSeverity());
    }

    static boolean isOpen(Complaint c) {
        return ComplaintStatus.fromLabel(c.getStatus()).map(ComplaintStatus::isOpen).orElse(true);
    }

    static boolean isStatus(Complaint c, ComplaintStatus s) {
        return ComplaintStatus.fromLabel(c.getStatus()).filter(x -> x == s).isPresent();
    }

    static boolean isHigh(Complaint c) {
        return Severity.fromLabel(c.getSeverity()).filter(x -> x == Severity.HIGH).isPresent();
    }

    static String roadLabel(Complaint c) {
        return RoadKeys.label(c.getRoadName(), c.getAddress(), c.getLat(), c.getLng());
    }

    /** Counts per label, largest first. */
    static Map<String, Long> countBy(Collection<Complaint> list, Function<Complaint, String> key) {
        Map<String, Long> counts = new HashMap<>();
        for (Complaint c : list) counts.merge(key.apply(c), 1L, Long::sum);
        Map<String, Long> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** Every status label appears (with 0 if unused), in lifecycle order. */
    static Map<String, Long> statusCounts(Collection<Complaint> list) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (ComplaintStatus s : ComplaintStatus.values()) out.put(s.label(), 0L);
        for (Complaint c : list) out.merge(status(c), 1L, Long::sum);
        return out;
    }

    static Map<String, Long> severityCounts(Collection<Complaint> list) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Severity s : new Severity[]{Severity.HIGH, Severity.MEDIUM, Severity.LOW}) out.put(s.label(), 0L);
        for (Complaint c : list) out.merge(severity(c), 1L, Long::sum);
        return out;
    }

    /**
     * When each currently-resolved complaint was resolved, taken from real STATUS_CHANGE events only
     * (imported/legacy rows have no such event, so they simply have no resolution time).
     */
    static Map<Long, Instant> resolvedAt(List<ComplaintStatusHistory> events) {
        List<ComplaintStatusHistory> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparing(ComplaintStatusHistory::getCreatedAt).thenComparing(ComplaintStatusHistory::getId));
        Map<Long, Instant> out = new HashMap<>();
        for (ComplaintStatusHistory h : ordered) {
            if (!ComplaintStatusHistory.STATUS_CHANGE.equals(h.getEventType())) continue;
            Long id = h.getComplaint().getId();
            if (ComplaintStatus.RESOLVED.label().equals(h.getToValue())) out.put(id, h.getCreatedAt());
            else out.remove(id);
        }
        return out;
    }

    static Double avgDaysToResolve(Collection<Complaint> list, Map<Long, Instant> resolvedAt) {
        double sum = 0;
        int n = 0;
        for (Complaint c : list) {
            Instant r = resolvedAt.get(c.getId());
            if (r != null && isStatus(c, ComplaintStatus.RESOLVED) && !r.isBefore(c.getCreatedAt())) {
                sum += Duration.between(c.getCreatedAt(), r).toMinutes() / 1440.0;
                n++;
            }
        }
        return n == 0 ? null : Math.round(sum / n * 10.0) / 10.0;
    }

    static Double resolutionRate(long resolved, long open) {
        long denom = resolved + open;
        return denom == 0 ? null : Math.round(resolved * 1000.0 / denom) / 10.0;
    }

    static List<TrendPoint> weekly(Collection<Complaint> list, Map<Long, Instant> resolvedAt, Instant now, ZoneId zone, int weeks) {
        List<LocalDate> starts = TimeBuckets.lastWeeks(now, zone, weeks);
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        for (LocalDate s : starts) buckets.put(s, new long[2]);
        for (Complaint c : list) {
            long[] b = buckets.get(TimeBuckets.weekStart(c.getCreatedAt(), zone));
            if (b != null) b[0]++;
            Instant r = resolvedAt.get(c.getId());
            if (r != null && isStatus(c, ComplaintStatus.RESOLVED)) {
                long[] rb = buckets.get(TimeBuckets.weekStart(r, zone));
                if (rb != null) rb[1]++;
            }
        }
        List<TrendPoint> out = new ArrayList<>();
        buckets.forEach((start, v) -> out.add(new TrendPoint(TimeBuckets.weekLabel(start), start, v[0], v[1])));
        return out;
    }

    static List<TrendPoint> monthly(Collection<Complaint> list, Map<Long, Instant> resolvedAt, Instant now, ZoneId zone, int months) {
        List<YearMonth> list12 = TimeBuckets.lastMonths(now, zone, months);
        Map<YearMonth, long[]> buckets = new LinkedHashMap<>();
        for (YearMonth m : list12) buckets.put(m, new long[2]);
        for (Complaint c : list) {
            long[] b = buckets.get(TimeBuckets.month(c.getCreatedAt(), zone));
            if (b != null) b[0]++;
            Instant r = resolvedAt.get(c.getId());
            if (r != null && isStatus(c, ComplaintStatus.RESOLVED)) {
                long[] rb = buckets.get(TimeBuckets.month(r, zone));
                if (rb != null) rb[1]++;
            }
        }
        List<TrendPoint> out = new ArrayList<>();
        buckets.forEach((m, v) -> out.add(new TrendPoint(TimeBuckets.monthLabel(m), m.atDay(1), v[0], v[1])));
        return out;
    }

    static List<SeverityTrendPoint> severityMonthly(Collection<Complaint> list, Instant now, ZoneId zone, int months) {
        Map<YearMonth, long[]> buckets = new LinkedHashMap<>();
        for (YearMonth m : TimeBuckets.lastMonths(now, zone, months)) buckets.put(m, new long[3]);
        for (Complaint c : list) {
            long[] b = buckets.get(TimeBuckets.month(c.getCreatedAt(), zone));
            if (b == null) continue;
            switch (Severity.fromLabel(c.getSeverity()).orElse(Severity.MEDIUM)) {
                case LOW -> b[0]++;
                case MEDIUM -> b[1]++;
                case HIGH -> b[2]++;
            }
        }
        List<SeverityTrendPoint> out = new ArrayList<>();
        buckets.forEach((m, v) -> out.add(new SeverityTrendPoint(TimeBuckets.monthLabel(m), v[0], v[1], v[2])));
        return out;
    }
}
