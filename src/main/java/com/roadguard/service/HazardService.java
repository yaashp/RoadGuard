package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.HazardDtos.HazardDetail;
import com.roadguard.dto.HazardDtos.NearbyHazardView;
import com.roadguard.dto.HazardDtos.PublicTimelineEntry;
import com.roadguard.dto.HazardDtos.VerifyResult;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.ValidationException;
import com.roadguard.geo.GeoUtils;
import com.roadguard.model.Complaint;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.ComplaintStatusHistory;
import com.roadguard.model.HazardVerification;
import com.roadguard.model.IssueCategory;
import com.roadguard.model.Notification;
import com.roadguard.model.RoadIssue;
import com.roadguard.model.User;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.ComplaintStatusHistoryRepository;
import com.roadguard.repository.HazardVerificationRepository;
import com.roadguard.repository.RoadIssueRepository;
import com.roadguard.security.SlidingWindowCounter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public hazard details, duplicate detection and community verification.
 * Votes feed counts, review flags, route-scoring confidence and impact points. They never change an official status.
 */
@Service
public class HazardService {
    private final RoadIssueRepository issues;
    private final HazardVerificationRepository votes;
    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final NotificationService notifications;
    private final FileStorageService files;
    private final RoadGuardProperties.Verification cfg;
    private final SlidingWindowCounter voteLimiter;

    public HazardService(RoadIssueRepository issues, HazardVerificationRepository votes, ComplaintRepository complaints,
                         ComplaintStatusHistoryRepository history, NotificationService notifications, FileStorageService files,
                         RoadGuardProperties props) {
        this.issues = issues;
        this.votes = votes;
        this.complaints = complaints;
        this.history = history;
        this.notifications = notifications;
        this.files = files;
        this.cfg = props.getVerification();
        this.voteLimiter = new SlidingWindowCounter(cfg.getMaxVotesPerHour(), 3_600_000L);
    }

    /** Open, real (not sample) hazards of the same category within the radius and recent enough to be the same problem. */
    @Transactional(readOnly = true)
    public List<NearbyHazardView> nearby(double lat, double lng, String categoryLabel, Double radiusMeters) {
        if (!Inputs.validLatLng(lat, lng)) throw new ValidationException(Map.of("location", "Provide a valid latitude and longitude."));
        double radius = radiusMeters == null ? cfg.getDuplicateRadiusMeters() : Math.min(Math.max(radiusMeters, 5), 500);
        String category = categoryLabel == null || categoryLabel.isBlank() ? null : RoadIssueService.canonicalCategory(categoryLabel);
        Instant oldest = Instant.now().minus(Duration.ofDays(cfg.getDuplicateMaxAgeDays()));
        List<NearbyHazardView> out = new ArrayList<>();
        for (RoadIssue i : issues.findAll()) {
            if (i.sample() || !ComplaintStatus.fromLabel(i.getStatus()).map(ComplaintStatus::isOpen).orElse(true)) continue;
            if (category != null && !RoadIssueService.canonicalCategory(i.getType()).equals(category)) continue;
            if (i.getCreatedAt() != null && i.getCreatedAt().isBefore(oldest)) continue;
            double d = GeoUtils.haversineMeters(lat, lng, i.getLat(), i.getLng());
            if (d > radius) continue;
            List<HazardVerification> v = votes.findByRoadIssue_Id(i.getId());
            int still = (int) v.stream().filter(x -> HazardVerification.STILL_THERE.equals(x.getVote())).count();
            Complaint c = i.getComplaintRef() == null ? null : complaints.findById(i.getComplaintRef()).orElse(null);
            out.add(new NearbyHazardView(i.getId(), RoadIssueService.canonicalCategory(i.getType()), i.getRoadName(), i.getSeverity(), i.getStatus(),
                    Math.round(d * 10) / 10.0, still, v.size() - still, c == null ? null : c.getComplaintId(), i.getCreatedAt()));
        }
        out.sort(Comparator.comparingDouble(NearbyHazardView::distanceMeters));
        return out;
    }

    @Transactional(readOnly = true)
    public HazardDetail detail(Long id, User viewer) {
        RoadIssue pin = publicPin(id);
        Complaint c = pin.getComplaintRef() == null ? null : complaints.findById(pin.getComplaintRef()).orElse(null);
        List<HazardVerification> v = votes.findByRoadIssue_Id(id);
        int still = (int) v.stream().filter(x -> HazardVerification.STILL_THERE.equals(x.getVote())).count();
        Instant last = v.stream().map(HazardVerification::votedAt).max(Comparator.naturalOrder()).orElse(null);

        List<PublicTimelineEntry> timeline = new ArrayList<>();
        if (c != null) {
            for (ComplaintStatusHistory h : history.findByComplaint_IdOrderByCreatedAtAscIdAsc(c.getId())) {
                if (ComplaintStatusHistory.RESOLUTION_EVIDENCE.equals(h.getEventType())) timeline.add(new PublicTimelineEntry("Repair photo added", h.getCreatedAt()));
                else if (List.of(ComplaintStatusHistory.SUBMITTED, ComplaintStatusHistory.STATUS_CHANGE, ComplaintStatusHistory.IMPORTED).contains(h.getEventType())) {
                    timeline.add(new PublicTimelineEntry(h.getToValue(), h.getCreatedAt()));
                }
            }
        }
        String yourVote = viewer == null ? null : votes.findByRoadIssue_IdAndUser_Id(id, viewer.getId()).map(HazardVerification::getVote).orElse(null);
        String blocked = blockedReason(pin, c, viewer);
        return new HazardDetail(id, c != null ? c.getComplaintId() : "PIN-" + id, RoadIssueService.canonicalCategory(pin.getType()), pin.getSeverity(),
                pin.getStatus(), pin.getRoadName(), round4(pin.getLat()), round4(pin.getLng()), c == null ? null : c.getDescription(),
                pin.getCreatedAt(), pin.getUpdatedAt(), still, v.size() - still, last, HazardFlags.of(pin, v, cfg.getFlagThreshold()), pin.sample(),
                c != null && c.hasImage(), c != null && c.getResolutionImagePath() != null, timeline, blocked == null, yourVote, blocked);
    }

    /** "Still there" / "No longer there". One opinion per user per hazard (changeable), never your own report, never sample data. */
    @Transactional
    public VerifyResult verify(User user, Long id, String voteRaw) {
        String vote = voteRaw == null ? "" : voteRaw.trim().toUpperCase(Locale.ROOT);
        if (!HazardVerification.STILL_THERE.equals(vote) && !HazardVerification.GONE.equals(vote)) {
            throw new ValidationException(Map.of("vote", "Vote must be STILL_THERE or GONE."));
        }
        RoadIssue pin = publicPin(id);
        Complaint c = pin.getComplaintRef() == null ? null : complaints.findById(pin.getComplaintRef()).orElse(null);
        String blocked = blockedReason(pin, c, user);
        if (blocked != null) throw ApiException.conflict(blocked);

        List<HazardVerification> before = votes.findByRoadIssue_Id(id);
        String flagBefore = HazardFlags.of(pin, before, cfg.getFlagThreshold());
        HazardVerification mine = votes.findByRoadIssue_IdAndUser_Id(id, user.getId()).orElse(null);
        if (mine == null || !mine.getVote().equals(vote)) {
            if (!voteLimiter.tryAcquire("vote:" + user.getId())) throw ApiException.tooMany("You are confirming hazards too quickly. Please try again later.");
            if (mine == null) votes.save(new HazardVerification(pin, user, vote));
            else { mine.setVote(vote); votes.save(mine); }
        }
        List<HazardVerification> after = votes.findByRoadIssue_Id(id);
        String flag = HazardFlags.of(pin, after, cfg.getFlagThreshold());
        if (flag != null && flagBefore == null) {
            String where = pin.getRoadName() == null ? "a reported hazard" : pin.getRoadName();
            String title = HazardFlags.POSSIBLY_GONE.equals(flag) ? "Hazard may be gone: " + where : "Resolved hazard still reported: " + where;
            notifications.notifyAdmins(Notification.HAZARD_FLAGGED, title,
                    "Several people responded to " + (c == null ? "pin " + id : c.getComplaintId()) + ". Please review it in the admin priority queue.", pin.getComplaintRef(), user.getId());
        }
        int still = (int) after.stream().filter(x -> HazardVerification.STILL_THERE.equals(x.getVote())).count();
        Instant last = after.stream().map(HazardVerification::votedAt).max(Comparator.naturalOrder()).orElse(null);
        return new VerifyResult(still, after.size() - still, last, vote, flag,
                "Thanks. Your answer is recorded as a community signal; only authorised staff can change the official status.");
    }

    public record Photo(String contentType, byte[] bytes) {}

    /** Public "before" photo of a reported hazard (not for rejected/duplicate reports). */
    @Transactional(readOnly = true)
    public Photo photo(Long id, boolean resolution) {
        RoadIssue pin = publicPin(id);
        Complaint c = pin.getComplaintRef() == null ? null : complaints.findById(pin.getComplaintRef()).orElse(null);
        if (c == null) throw ApiException.notFound("This hazard has no photo.");
        String path = resolution ? c.getResolutionImagePath() : c.getImagePath();
        String type = resolution ? c.getResolutionImageContentType() : c.getImageContentType();
        if (path != null) {
            byte[] bytes = files.read(path).orElseThrow(() -> ApiException.notFound("The photo file is missing."));
            return new Photo(type == null ? "application/octet-stream" : type, bytes);
        }
        if (!resolution && c.getImage() != null && c.getImage().startsWith("data:image/")) {   // photos stored before the upgrade
            FileStorageService.DecodedImage d = files.decodeDataUrl(c.getImage());
            String t = d.declaredType().toLowerCase(Locale.ROOT);
            if (t.equals("image/jpeg") || t.equals("image/png") || t.equals("image/webp")) return new Photo(t, d.bytes());
        }
        throw ApiException.notFound("This hazard has no photo.");
    }

    // ------------------------------------------------------------------

    private RoadIssue publicPin(Long id) {
        RoadIssue pin = issues.findById(id).orElseThrow(() -> ApiException.notFound("Hazard not found."));
        ComplaintStatus st = ComplaintStatus.fromLabel(pin.getStatus()).orElse(ComplaintStatus.SUBMITTED);
        if (st == ComplaintStatus.REJECTED || st == ComplaintStatus.DUPLICATE) throw ApiException.notFound("Hazard not found.");
        return pin;
    }

    private static String blockedReason(RoadIssue pin, Complaint c, User viewer) {
        if (viewer == null) return "Log in to confirm or dispute a hazard.";
        if (pin.sample()) return "Sample records cannot be verified.";
        if (c != null && c.getUser() != null && c.getUser().getId().equals(viewer.getId())
                && ComplaintStatus.fromLabel(pin.getStatus()).orElse(ComplaintStatus.SUBMITTED) != ComplaintStatus.RESOLVED) {
            return "You reported this hazard, so you cannot also confirm it while it is open. You can flag it if it comes back after being resolved.";
        }
        return null;
    }

    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }

    static String categoryOf(String type) { return IssueCategory.fromLabel(type).map(IssueCategory::label).orElse(IssueCategory.OTHER.label()); }
}
