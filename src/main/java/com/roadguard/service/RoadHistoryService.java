package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.HistoryDtos.Hotspot;
import com.roadguard.dto.HistoryDtos.HistoryRecord;
import com.roadguard.dto.HistoryDtos.PeriodComparison;
import com.roadguard.dto.HistoryDtos.PeriodStats;
import com.roadguard.dto.HistoryDtos.RepeatedIssue;
import com.roadguard.dto.HistoryDtos.ResolutionStats;
import com.roadguard.dto.HistoryDtos.RoadHistory;
import com.roadguard.dto.HistoryDtos.RoadSummary;
import com.roadguard.dto.HistoryDtos.VolumeChange;
import com.roadguard.exception.ApiException;
import com.roadguard.geo.RoadKeys;
import com.roadguard.model.Complaint;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.ComplaintStatusHistoryRepository;
import com.roadguard.model.ComplaintStatusHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Road History: every figure is derived from complaints (and their status history) stored in RoadGuard.
 * These are citizen reports, not official government records, and records flagged as sample data are
 * counted only when the caller asks for them.
 */
@Service
public class RoadHistoryService {
    static final String DATA_NOTE = "Figures are built from complaints submitted through RoadGuard. They are citizen reports, "
            + "not official government records or measurements of road condition.";

    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final ZoneId zone;

    public RoadHistoryService(ComplaintRepository complaints, ComplaintStatusHistoryRepository history, RoadGuardProperties props) {
        this.complaints = complaints;
        this.history = history;
        this.zone = ZoneId.of(props.getTimeZone());
    }

    /** Roads whose name contains the query (all roads when blank), most-reported first. */
    @Transactional(readOnly = true)
    public List<RoadSummary> search(String query, boolean includeSample, int limit, Long ownerId) {
        String q = RoadKeys.key(query == null ? "" : query);
        Map<String, List<Complaint>> groups = group(visible(includeSample, ownerId));
        List<RoadSummary> out = new ArrayList<>();
        for (Map.Entry<String, List<Complaint>> e : groups.entrySet()) {
            if (!q.isEmpty() && !e.getKey().contains(q)) continue;
            out.add(summarize(e.getValue()));
        }
        out.sort(Comparator.comparingLong(RoadSummary::totalComplaints).reversed().thenComparing(RoadSummary::road));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    @Transactional(readOnly = true)
    public RoadHistory history(String road, boolean includeSample, Long ownerId) {
        String key = RoadKeys.key(road == null ? "" : road);
        if (key.isEmpty()) throw ApiException.badRequest("Provide a road name.");
        List<Complaint> list = group(visible(includeSample, ownerId)).get(key);
        if (list == null || list.isEmpty()) throw ApiException.notFound("No complaints have been reported for that road yet.");

        Instant now = Instant.now();
        Map<Long, Instant> resolvedAt = Analytics.resolvedAt(history.findByEventType(ComplaintStatusHistory.STATUS_CHANGE));
        long open = list.stream().filter(Analytics::isOpen).count();
        long resolved = list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.RESOLVED)).count();
        long rejected = list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.REJECTED)).count();

        List<HistoryRecord> records = list.stream()
                .sorted(Comparator.comparing(Complaint::getCreatedAt).reversed())
                .limit(200)
                .map(c -> new HistoryRecord(c.getComplaintId(), Analytics.category(c), Analytics.severity(c), Analytics.status(c),
                        c.getAddress(), c.getCreatedAt(),
                        Analytics.isStatus(c, ComplaintStatus.RESOLVED) ? resolvedAt.get(c.getId()) : null, c.sample()))
                .toList();

        return new RoadHistory(summarize(list), Analytics.countBy(list, Analytics::category), Analytics.severityCounts(list),
                Analytics.weekly(list, resolvedAt, now, zone, 12), Analytics.monthly(list, resolvedAt, now, zone, 12),
                Analytics.severityMonthly(list, now, zone, 12),
                new ResolutionStats(resolved, rejected, open, Analytics.resolutionRate(resolved, open), Analytics.avgDaysToResolve(list, resolvedAt)),
                volumeChange(list, now, 30), repeated(list), records, includeSample, DATA_NOTE);
    }

    /** Roads with the most reports, plus how many are still open. */
    @Transactional(readOnly = true)
    public List<Hotspot> hotspots(int limit, boolean includeSample, Long ownerId) {
        List<Hotspot> out = new ArrayList<>();
        for (List<Complaint> list : group(visible(includeSample, ownerId)).values()) {
            RoadSummary s = summarize(list);
            String top = Analytics.countBy(list, Analytics::category).keySet().stream().findFirst().orElse("");
            out.add(new Hotspot(s.road(), s.totalComplaints(), s.openComplaints(), s.highSeverity(), top, s.lastReportedAt()));
        }
        out.sort(Comparator.comparingLong(Hotspot::totalComplaints).reversed()
                .thenComparing(Comparator.comparingLong(Hotspot::openComplaints).reversed()).thenComparing(Hotspot::road));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    /** Last N days compared with the N days before that, for one road or for all roads when `road` is blank. */
    @Transactional(readOnly = true)
    public PeriodComparison compare(String road, int periodDays, boolean includeSample, Long ownerId) {
        if (periodDays < 7 || periodDays > 365) throw ApiException.badRequest("periodDays must be between 7 and 365.");
        List<Complaint> list = visible(includeSample, ownerId);
        String label = "All roads";
        if (road != null && !road.isBlank()) {
            String key = RoadKeys.key(road);
            List<Complaint> g = group(list).get(key);
            if (g == null) throw ApiException.notFound("No complaints have been reported for that road yet.");
            list = g;
            label = RoadKeys.label(g.get(0).getRoadName(), g.get(0).getAddress(), g.get(0).getLat(), g.get(0).getLng());
        }
        Instant now = Instant.now();
        Instant mid = now.minus(periodDays, ChronoUnit.DAYS);
        Instant start = now.minus(2L * periodDays, ChronoUnit.DAYS);
        Map<Long, Instant> resolvedAt = Analytics.resolvedAt(history.findByEventType(ComplaintStatusHistory.STATUS_CHANGE));
        PeriodStats cur = period(list, mid, now.plusSeconds(1), resolvedAt);
        PeriodStats prev = period(list, start, mid, resolvedAt);
        Double pct = prev.complaints() == 0 ? null : Math.round((cur.complaints() - prev.complaints()) * 1000.0 / prev.complaints()) / 10.0;
        return new PeriodComparison(label, periodDays, cur, prev, pct, trend(cur.complaints(), prev.complaints()));
    }

    // ---------------- helpers ----------------

    /** Complaints in scope: everything for ownerId == null (admin), otherwise only that user's complaints. */
    private List<Complaint> visible(boolean includeSample, Long ownerId) {
        return complaints.findAll().stream()
                .filter(c -> includeSample || !c.sample())
                .filter(c -> ownerId == null || (c.getUser() != null && ownerId.equals(c.getUser().getId())))
                .toList();
    }

    private Map<String, List<Complaint>> group(List<Complaint> list) {
        Map<String, List<Complaint>> groups = new LinkedHashMap<>();
        for (Complaint c : list) groups.computeIfAbsent(RoadKeys.key(Analytics.roadLabel(c)), k -> new ArrayList<>()).add(c);
        return groups;
    }

    private RoadSummary summarize(List<Complaint> list) {
        Complaint newest = list.stream().max(Comparator.comparing(Complaint::getCreatedAt)).orElseThrow();
        // display the most common spelling of the road name
        String label = Analytics.countBy(list, Analytics::roadLabel).keySet().stream().findFirst().orElse(Analytics.roadLabel(newest));
        return new RoadSummary(label, list.size(), list.stream().filter(Analytics::isOpen).count(),
                list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.RESOLVED)).count(),
                list.stream().filter(Analytics::isHigh).count(), newest.getCreatedAt(),
                list.stream().anyMatch(Complaint::sample));
    }

    private PeriodStats period(List<Complaint> list, Instant from, Instant to, Map<Long, Instant> resolvedAt) {
        List<Complaint> in = list.stream().filter(c -> !c.getCreatedAt().isBefore(from) && c.getCreatedAt().isBefore(to)).toList();
        long resolved = list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.RESOLVED))
                .filter(c -> { Instant r = resolvedAt.get(c.getId()); return r != null && !r.isBefore(from) && r.isBefore(to); }).count();
        return new PeriodStats(from, to, in.size(), in.stream().filter(Analytics::isHigh).count(), resolved);
    }

    private VolumeChange volumeChange(List<Complaint> list, Instant now, int days) {
        Instant mid = now.minus(days, ChronoUnit.DAYS);
        Instant start = now.minus(2L * days, ChronoUnit.DAYS);
        long cur = list.stream().filter(c -> !c.getCreatedAt().isBefore(mid)).count();
        long prev = list.stream().filter(c -> !c.getCreatedAt().isBefore(start) && c.getCreatedAt().isBefore(mid)).count();
        Double pct = prev == 0 ? null : Math.round((cur - prev) * 1000.0 / prev) / 10.0;
        return new VolumeChange(days, cur, prev, pct, trend(cur, prev));
    }

    private static String trend(long current, long previous) {
        if (previous == 0) return current == 0 ? "flat" : "new";
        if (current > previous) return "up";
        if (current < previous) return "down";
        return "flat";
    }

    /** Categories reported at least twice on the same road. */
    private List<RepeatedIssue> repeated(List<Complaint> list) {
        Map<String, List<Complaint>> byCat = new LinkedHashMap<>();
        for (Complaint c : list) byCat.computeIfAbsent(Analytics.category(c).toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(c);
        List<RepeatedIssue> out = new ArrayList<>();
        for (List<Complaint> g : byCat.values()) {
            if (g.size() < 2) continue;
            Instant first = g.stream().map(Complaint::getCreatedAt).min(Comparator.naturalOrder()).orElseThrow();
            Instant last = g.stream().map(Complaint::getCreatedAt).max(Comparator.naturalOrder()).orElseThrow();
            out.add(new RepeatedIssue(Analytics.category(g.get(0)), g.size(), first, last));
        }
        out.sort(Comparator.comparingLong(RepeatedIssue::count).reversed().thenComparing(RepeatedIssue::category));
        return out;
    }

    /** Whole days between two instants (used by tests/tools). */
    static long days(Instant a, Instant b) { return Duration.between(a, b).toDays(); }
}
