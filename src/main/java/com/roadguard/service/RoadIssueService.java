package com.roadguard.service;

import com.roadguard.dto.MapDtos.RoadIssueRequest;
import com.roadguard.dto.MapDtos.RoadIssueView;
import com.roadguard.dto.RouteDtos.HazardPoint;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.Validation;
import com.roadguard.geo.RoadKeys;
import com.roadguard.config.RoadGuardProperties;
import com.roadguard.model.Complaint;
import com.roadguard.model.HazardVerification;
import com.roadguard.repository.HazardVerificationRepository;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.IssueCategory;
import com.roadguard.model.RoadIssue;
import com.roadguard.model.Severity;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.RoadIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Map pins ("road issues"): listing for the public map, admin management, and syncing with complaints. */
@Service
public class RoadIssueService {
    private final RoadIssueRepository issues;
    private final ComplaintRepository complaints;
    private final HazardVerificationRepository votes;
    private final int flagThreshold;

    public RoadIssueService(RoadIssueRepository issues, ComplaintRepository complaints, HazardVerificationRepository votes, RoadGuardProperties props) {
        this.issues = issues;
        this.complaints = complaints;
        this.votes = votes;
        this.flagThreshold = props.getVerification().getFlagThreshold();
    }

    /** Map filters. Null/blank means "no filter". Rejected reports are never shown unless includeRejected. */
    public record Filter(String category, String severity, String status, String q, Integer maxAgeDays,
                         boolean includeSample, boolean includeResolved, boolean includeRejected,
                         Long ownerId) {}

    @Transactional(readOnly = true)
    public List<RoadIssueView> list(Filter f) {
        List<RoadIssue> all = issues.findAll();
        String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
        Set<Long> owned = f.ownerId() == null ? null : ownedComplaintIds(all, f.ownerId());
        List<RoadIssue> kept = all.stream().filter(i -> {
            if (owned != null && (i.getComplaintRef() == null || !owned.contains(i.getComplaintRef()))) return false;
            if (!f.includeSample() && i.sample()) return false;
            ComplaintStatus st = ComplaintStatus.fromLabel(i.getStatus()).orElse(ComplaintStatus.SUBMITTED);
            if (st == ComplaintStatus.RESOLVED && !f.includeResolved()) return false;
            if ((st == ComplaintStatus.REJECTED || st == ComplaintStatus.DUPLICATE) && !f.includeRejected()) return false;
            if (f.maxAgeDays() != null && i.getCreatedAt() != null && i.getCreatedAt().isBefore(Instant.now().minus(Duration.ofDays(f.maxAgeDays())))) return false;
            if (notBlank(f.category()) && !sameCategory(i.getType(), f.category())) return false;
            if (notBlank(f.severity()) && !f.severity().equalsIgnoreCase(i.getSeverity())) return false;
            if (notBlank(f.status()) && !f.status().equalsIgnoreCase(i.getStatus())) return false;
            if (!q.isEmpty()) {
                String hay = ((i.getRoadName() == null ? "" : i.getRoadName()) + " " + i.getType()).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) return false;
            }
            return true;
        }).toList();
        return toViews(kept);
    }

    @Transactional(readOnly = true)
    public RoadIssueView get(Long id) {
        RoadIssue i = issues.findById(id).orElseThrow(() -> ApiException.notFound("Road issue not found."));
        return toViews(List.of(i)).get(0);
    }

    /** Open hazards for route scoring. */
    @Transactional(readOnly = true)
    public List<HazardPoint> openHazards(boolean includeSample) {
        Map<Long, List<HazardVerification>> byIssue = votesByIssue();
        return issues.findAll().stream()
                .filter(i -> includeSample || !i.sample())
                .filter(i -> ComplaintStatus.fromLabel(i.getStatus()).map(ComplaintStatus::isOpen).orElse(true))
                .map(i -> {
                    List<HazardVerification> v = byIssue.getOrDefault(i.getId(), List.of());
                    return new HazardPoint(i.getId(), canonicalCategory(i.getType()), i.getSeverity(), i.getStatus(),
                            i.getLat(), i.getLng(), i.getRoadName(), i.sample(), i.getCreatedAt(),
                            (int) v.stream().filter(x -> HazardVerification.STILL_THERE.equals(x.getVote())).count(),
                            (int) v.stream().filter(x -> HazardVerification.GONE.equals(x.getVote())).count());
                })
                .toList();
    }

    // ---------- keeping pins in step with complaints ----------

    /** Creates or updates the pin that represents this complaint. Call inside the caller's transaction. */
    public RoadIssue syncFromComplaint(Complaint c) {
        RoadIssue pin = issues.findFirstByComplaintRef(c.getId()).orElseGet(RoadIssue::new);
        boolean isNew = pin.getId() == null;
        pin.setType(canonicalCategory(c.getIssueType()));
        pin.setLat(c.getLat());
        pin.setLng(c.getLng());
        pin.setRoadName(RoadKeys.label(c.getRoadName(), c.getAddress(), c.getLat(), c.getLng()));
        pin.setSeverity(c.getSeverity());
        pin.setStatus(c.getStatus());
        pin.setReportCount(1);
        pin.setComplaintRef(c.getId());
        pin.setSource(c.sample() ? RoadIssue.SOURCE_SAMPLE : RoadIssue.SOURCE_REPORTED);
        if (isNew) pin.setCreatedAt(c.getCreatedAt());
        pin.setUpdatedAt(Instant.now());
        return issues.save(pin);
    }

    // ---------- administrator management ----------

    @Transactional
    public RoadIssueView create(RoadIssueRequest req) {
        Validated v = validate(req, true);
        RoadIssue pin = new RoadIssue(v.type, req.lat(), req.lng(), v.road, v.severity, v.status, 1);
        pin.setSource(RoadIssue.SOURCE_ADMIN);
        return toViews(List.of(issues.save(pin))).get(0);
    }

    @Transactional
    public RoadIssueView update(Long id, RoadIssueRequest req) {
        RoadIssue pin = issues.findById(id).orElseThrow(() -> ApiException.notFound("Road issue not found."));
        boolean linked = pin.getComplaintRef() != null;
        Validated v = validate(req, false);
        if (linked && req.status() != null && !req.status().equalsIgnoreCase(pin.getStatus())) {
            throw ApiException.badRequest("This pin belongs to a complaint. Change the complaint's status instead.");
        }
        if (linked && (req.lat() != null || req.lng() != null)) {
            double lat = req.lat() == null ? pin.getLat() : req.lat();
            double lng = req.lng() == null ? pin.getLng() : req.lng();
            if (lat != pin.getLat() || lng != pin.getLng()) {
                throw ApiException.badRequest("The location of a complaint's pin cannot be edited.");
            }
        }
        if (v.type != null) pin.setType(v.type);
        if (v.road != null) pin.setRoadName(v.road);
        if (v.severity != null) pin.setSeverity(v.severity);
        if (!linked && v.status != null) pin.setStatus(v.status);
        if (!linked && req.lat() != null && req.lng() != null) {
            pin.setLat(req.lat());
            pin.setLng(req.lng());
        }
        pin.setUpdatedAt(Instant.now());
        return toViews(List.of(issues.save(pin))).get(0);
    }

    @Transactional
    public void delete(Long id) {
        RoadIssue pin = issues.findById(id).orElseThrow(() -> ApiException.notFound("Road issue not found."));
        if (pin.getComplaintRef() != null && !pin.sample()) {
            throw ApiException.conflict("This pin belongs to a citizen complaint. Reject the complaint instead of deleting its pin.");
        }
        issues.delete(pin);
    }

    // ---------- helpers ----------

    private record Validated(String type, String road, String severity, String status) {}

    private Validated validate(RoadIssueRequest req, boolean creating) {
        Validation v = new Validation();
        String type = null, severity = null, status = null, road = null;
        if (req.type() != null || creating) {
            var cat = IssueCategory.fromLabel(req.type());
            v.check(cat.isPresent(), "type", "Choose a valid issue category.");
            type = cat.map(IssueCategory::label).orElse(null);
        }
        if (req.severity() != null || creating) {
            var sev = Severity.fromLabel(req.severity());
            v.check(sev.isPresent(), "severity", "Choose Low, Medium or High.");
            severity = sev.map(Severity::label).orElse(null);
        }
        if (req.status() != null || creating) {
            var st = ComplaintStatus.fromLabel(req.status() == null ? "Submitted" : req.status());
            v.check(st.isPresent(), "status", "Choose a valid status.");
            status = st.map(ComplaintStatus::label).orElse(null);
        }
        if (req.roadName() != null || creating) {
            road = Inputs.clean(req.roadName());
            v.check(!road.isEmpty() && road.length() <= 120, "roadName", "Road name is required (max 120 characters).");
        }
        if (creating || req.lat() != null || req.lng() != null) {
            if (creating) v.check(Inputs.validLatLng(req.lat(), req.lng()), "location", "Provide a valid latitude and longitude.");
            else if (req.lat() != null || req.lng() != null) {
                v.check(req.lat() != null && req.lng() != null && Inputs.validLatLng(req.lat(), req.lng()), "location",
                        "Provide a valid latitude and longitude.");
            }
        }
        v.throwIfInvalid();
        return new Validated(type, road, severity, status);
    }

    private List<RoadIssueView> toViews(List<RoadIssue> list) {
        Set<Long> refs = new HashSet<>();
        for (RoadIssue i : list) if (i.getComplaintRef() != null) refs.add(i.getComplaintRef());
        Map<Long, Complaint> byId = new HashMap<>();
        if (!refs.isEmpty()) {
            for (Complaint c : complaints.findAllById(refs)) byId.put(c.getId(), c);
        }
        Map<Long, List<HazardVerification>> byIssue = votesByIssue();
        return list.stream().map(i -> {
            Complaint c = i.getComplaintRef() == null ? null : byId.get(i.getComplaintRef());
            List<HazardVerification> v = byIssue.getOrDefault(i.getId(), List.of());
            int still = (int) v.stream().filter(x -> HazardVerification.STILL_THERE.equals(x.getVote())).count();
            Instant last = v.stream().map(HazardVerification::votedAt).max(Comparator.naturalOrder()).orElse(null);
            return new RoadIssueView(i.getId(), canonicalCategory(i.getType()), i.getLat(), i.getLng(),
                    i.getRoadName(), i.getSeverity(), i.getStatus(), i.getReportCount(),
                    i.getSource() == null ? RoadIssue.SOURCE_SAMPLE : i.getSource(), i.sample(), i.getComplaintRef(),
                    c == null ? null : c.getComplaintId(), i.getCreatedAt(), still, v.size() - still, last,
                    c != null && c.hasImage(), c != null && c.getResolutionImagePath() != null,
                    HazardFlags.of(i, v, flagThreshold));
        }).toList();
    }

    Map<Long, List<HazardVerification>> votesByIssue() {
        return votes.findAll().stream().collect(Collectors.groupingBy(v -> v.getRoadIssue().getId()));
    }

    static String canonicalCategory(String type) {
        return IssueCategory.fromLabel(type).map(IssueCategory::label).orElse(IssueCategory.OTHER.label());
    }

    private static boolean sameCategory(String stored, String wanted) {
        return canonicalCategory(stored).equalsIgnoreCase(canonicalCategory(wanted));
    }

    /** Ids of the complaints (among the pins' sources) that belong to this user. Sample and admin pins never match. */
    private Set<Long> ownedComplaintIds(List<RoadIssue> all, Long userId) {
        Set<Long> refs = all.stream().map(RoadIssue::getComplaintRef).filter(Objects::nonNull).collect(Collectors.toSet());
        if (refs.isEmpty()) return Set.of();
        return complaints.findAllById(refs).stream()
                .filter(c -> c.getUser() != null && userId.equals(c.getUser().getId()))
                .map(Complaint::getId)
                .collect(Collectors.toSet());
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
