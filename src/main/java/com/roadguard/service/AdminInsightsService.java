package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.InsightDtos.AuditEntry;
import com.roadguard.dto.InsightDtos.Counters;
import com.roadguard.dto.InsightDtos.Hotspot;
import com.roadguard.dto.InsightDtos.Insights;
import com.roadguard.dto.InsightDtos.PriorityItem;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.ValidationException;
import com.roadguard.model.Complaint;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.ComplaintStatusHistory;
import com.roadguard.model.HazardVerification;
import com.roadguard.model.RoadIssue;
import com.roadguard.model.Severity;
import com.roadguard.model.User;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.ComplaintStatusHistoryRepository;
import com.roadguard.repository.HazardVerificationRepository;
import com.roadguard.repository.RoadIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Review aids for administrators. The priority score is a transparent sum of visible factors that helps staff decide what to look at
 * first; it is not an engineering or emergency-service decision and never changes a status by itself.
 */
@Service
public class AdminInsightsService {
    static final String DISCLAIMER = "The priority score only helps staff decide what to review first. It is calculated from severity, community confirmations and age, "
            + "is not an engineering or emergency assessment, and never changes a report's status by itself.";
    private static final double CELL = 0.005;   // grid cell for hotspots, about 550 m

    private final RoadIssueRepository issues;
    private final ComplaintRepository complaints;
    private final HazardVerificationRepository votes;
    private final ComplaintStatusHistoryRepository history;
    private final int flagThreshold;

    public AdminInsightsService(RoadIssueRepository issues, ComplaintRepository complaints, HazardVerificationRepository votes,
                                ComplaintStatusHistoryRepository history, RoadGuardProperties props) {
        this.issues = issues;
        this.complaints = complaints;
        this.votes = votes;
        this.history = history;
        this.flagThreshold = props.getVerification().getFlagThreshold();
    }

    @Transactional(readOnly = true)
    public Insights insights(boolean includeSample, int limit) {
        Map<Long, List<HazardVerification>> byIssue = votes.findAll().stream().collect(Collectors.groupingBy(v -> v.getRoadIssue().getId()));
        Map<Long, Complaint> byId = complaints.findAll().stream().collect(Collectors.toMap(Complaint::getId, c -> c));
        Instant now = Instant.now();

        List<PriorityItem> queue = new ArrayList<>();
        List<RoadIssue> open = new ArrayList<>();
        for (RoadIssue pin : issues.findAll()) {
            if (!includeSample && pin.sample()) continue;
            ComplaintStatus st = ComplaintStatus.fromLabel(pin.getStatus()).orElse(ComplaintStatus.SUBMITTED);
            List<HazardVerification> v = byIssue.getOrDefault(pin.getId(), List.of());
            String flag = HazardFlags.of(pin, v, flagThreshold);
            if (st.isOpen()) open.add(pin);
            if (!st.isOpen() && flag == null) continue;                       // closed and not flagged: nothing to review
            Complaint c = pin.getComplaintRef() == null ? null : byId.get(pin.getComplaintRef());
            int still = (int) v.stream().filter(x -> HazardVerification.STILL_THERE.equals(x.getVote())).count();
            int gone = v.size() - still;
            int age = pin.getCreatedAt() == null ? 0 : (int) Math.max(0, Duration.between(pin.getCreatedAt(), now).toDays());
            List<String> factors = new ArrayList<>();
            int sev = switch (Severity.fromLabel(pin.getSeverity()).orElse(Severity.MEDIUM)) { case HIGH -> 40; case MEDIUM -> 20; case LOW -> 5; };
            factors.add("Severity " + Severity.fromLabel(pin.getSeverity()).orElse(Severity.MEDIUM).label() + ": +" + sev);
            int conf = Math.min(still * 5, 20);
            if (conf > 0) factors.add(still + " 'still there' confirmation(s): +" + conf);
            int malus = Math.min(gone * 10, 20);
            if (malus > 0) factors.add(gone + " 'no longer there' vote(s): -" + malus);
            int ageBonus = Math.min(age / 7, 15);
            if (ageBonus > 0) factors.add("Open for " + age + " days: +" + ageBonus);
            int flagBonus = HazardFlags.STILL_PRESENT.equals(flag) ? 25 : 0;
            if (flagBonus > 0) factors.add("Marked resolved, but people say it is still there: +" + flagBonus);
            if (HazardFlags.POSSIBLY_GONE.equals(flag)) factors.add("Several people say it may be gone: review before spending effort");
            int score = Math.max(0, sev + conf - malus + ageBonus + flagBonus);
            queue.add(new PriorityItem(c == null ? null : c.getId(), c == null ? null : c.getComplaintId(), pin.getId(), pin.getRoadName(),
                    RoadIssueService.canonicalCategory(pin.getType()), pin.getSeverity(), pin.getStatus(), score,
                    score >= 60 ? "High" : score >= 35 ? "Medium" : "Low", factors, still, gone, age, flag, pin.sample()));
        }
        queue.sort(Comparator.comparingInt(PriorityItem::score).reversed().thenComparing(PriorityItem::ageDays, Comparator.reverseOrder()));

        List<Complaint> list = complaints.findAll().stream().filter(c -> includeSample || !c.sample()).toList();
        Counters counters = new Counters(list.size(), open.size(), queue.stream().filter(q -> "High".equals(q.bucket())).count(),
                list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.UNDER_REVIEW)).count(),
                list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.RESOLVED) && c.getResolutionImagePath() != null).count(),
                queue.stream().filter(q -> q.flag() != null).count());
        return new Insights(counters, queue.stream().limit(Math.min(Math.max(limit, 1), 100)).toList(), hotspots(open), DISCLAIMER);
    }

    private List<Hotspot> hotspots(List<RoadIssue> open) {
        Map<String, List<RoadIssue>> cells = new HashMap<>();
        for (RoadIssue p : open) {
            cells.computeIfAbsent(Math.floor(p.getLat() / CELL) + ":" + Math.floor(p.getLng() / CELL), k -> new ArrayList<>()).add(p);
        }
        List<Hotspot> out = new ArrayList<>();
        for (List<RoadIssue> g : cells.values()) {
            if (g.size() < 2) continue;                                     // a hotspot needs at least two open hazards
            double lat = g.stream().mapToDouble(RoadIssue::getLat).average().orElse(0), lng = g.stream().mapToDouble(RoadIssue::getLng).average().orElse(0);
            String top = g.stream().collect(Collectors.groupingBy(p -> RoadIssueService.canonicalCategory(p.getType()), Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
            Set<String> roads = new LinkedHashSet<>();
            g.forEach(p -> { if (p.getRoadName() != null && roads.size() < 3) roads.add(p.getRoadName()); });
            out.add(new Hotspot(Math.round(lat * 10000) / 10000.0, Math.round(lng * 10000) / 10000.0, g.size(),
                    (int) g.stream().filter(p -> "High".equalsIgnoreCase(p.getSeverity())).count(), top, new ArrayList<>(roads)));
        }
        out.sort(Comparator.comparingInt(Hotspot::openHazards).reversed().thenComparing(Comparator.comparingInt(Hotspot::highSeverity).reversed()));
        return out.size() > 15 ? out.subList(0, 15) : out;
    }

    /** An administrator reviewed the community flag and decided no action is needed; older votes stop counting. Recorded in the audit trail. */
    @Transactional
    public void dismissFlag(User admin, Long pinId, String note) {
        RoadIssue pin = issues.findById(pinId).orElseThrow(() -> ApiException.notFound("Hazard not found."));
        String text = Inputs.clean(note);
        if (text.length() < 5 || text.length() > 500) throw new ValidationException(Map.of("note", "Please explain your decision (5-500 characters)."));
        String flag = HazardFlags.of(pin, votes.findByRoadIssue_Id(pinId), flagThreshold);
        if (flag == null) throw ApiException.badRequest("This hazard has no community flag to dismiss.");
        pin.setFlagClearedAt(Instant.now());
        issues.save(pin);
        Complaint c = pin.getComplaintRef() == null ? null : complaints.findById(pin.getComplaintRef()).orElse(null);
        if (c != null) {
            history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.FLAG_DISMISSED, flag, "Flag dismissed", text, admin, Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> audit(int limit) {
        Set<String> adminEvents = Set.of(ComplaintStatusHistory.STATUS_CHANGE, ComplaintStatusHistory.SEVERITY_CHANGE, ComplaintStatusHistory.RESOLUTION_EVIDENCE,
                ComplaintStatusHistory.DUPLICATE_MARKED, ComplaintStatusHistory.FLAG_DISMISSED);
        return history.findAll().stream()
                .filter(h -> adminEvents.contains(h.getEventType()) && h.getChangedBy() != null && h.getChangedBy().admin())
                .sorted(Comparator.comparing(ComplaintStatusHistory::getCreatedAt).reversed().thenComparing(ComplaintStatusHistory::getId, Comparator.reverseOrder()))
                .limit(Math.min(Math.max(limit, 1), 200))
                .map(h -> new AuditEntry(h.getCreatedAt(), h.getChangedBy().getName(), switch (h.getEventType()) {
                    case ComplaintStatusHistory.SEVERITY_CHANGE -> "Severity changed";
                    case ComplaintStatusHistory.RESOLUTION_EVIDENCE -> "Repair photo added";
                    case ComplaintStatusHistory.DUPLICATE_MARKED -> "Marked duplicate";
                    case ComplaintStatusHistory.FLAG_DISMISSED -> "Community flag dismissed";
                    default -> "Status changed";
                }, h.getComplaint().getComplaintId(),
                        (h.getFromValue() == null ? "" : h.getFromValue() + " → ") + h.getToValue() + (h.getNote() == null ? "" : " · " + h.getNote())))
                .toList();
    }
}
