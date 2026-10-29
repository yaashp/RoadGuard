package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.CivicDtos.*;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.Validation;
import com.roadguard.exception.ValidationException;
import com.roadguard.geo.RoadKeys;
import com.roadguard.model.*;
import com.roadguard.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Four civic features, written so they claim no more than the data holds:
 * an authority loop (letter export + staff-recorded response), repair-proof pages, a "fixed recently" feed,
 * and a personal "your street" summary.
 */
@Service
public class CivicService {
    static final String AUTHORITY_DISCLAIMER = "RoadGuard is not connected to any government or municipal system. "
            + "A report here has not been sent to an authority unless you send the letter yourself. "
            + "The response shown is only what RoadGuard staff have recorded from an authority's reply.";
    static final String PROOF_DISCLAIMER = "Repair proof shows what RoadGuard staff recorded: the report, its photos and its status history. "
            + "It is not an official inspection, and a resolved status can be reopened if people report the problem again.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final HazardVerificationRepository votes;
    private final RoadIssueRepository issues;
    private final ZoneId zone;

    public CivicService(ComplaintRepository complaints, ComplaintStatusHistoryRepository history, HazardVerificationRepository votes,
                        RoadIssueRepository issues, RoadGuardProperties props) {
        this.complaints = complaints;
        this.history = history;
        this.votes = votes;
        this.issues = issues;
        this.zone = ZoneId.of(props.getTimeZone());
    }

    // ------------------------------------------------------------ 1. authority loop

    /** The owner sets which ward or department they believe is responsible. Free text; optional. */
    @Transactional
    public AuthorityView setAuthority(User owner, Long complaintId, AuthorityRequest req) {
        Complaint c = ownedComplaint(owner, complaintId);
        String name = Inputs.clean(req.authorityName());
        if (name.length() > 120) throw new ValidationException(Map.of("authorityName", "At most 120 characters."));
        c.setAuthorityName(name.isEmpty() ? null : name);
        complaints.save(c);
        return view(c);
    }

    /** Plain-text letter the citizen can copy, print or e-mail. Marks the report as "taken forward" for staff. */
    @Transactional
    public LetterView letter(User owner, Long complaintId) {
        Complaint c = ownedComplaint(owner, complaintId);
        c.setLetterExportedAt(Instant.now());
        complaints.save(c);
        history.save(new ComplaintStatusHistory(c, "LETTER_EXPORTED", null, "Letter prepared", "Citizen prepared a letter to the authority.", owner, Instant.now()));
        String to = c.getAuthorityName() == null || c.getAuthorityName().isBlank() ? "The responsible authority (ward or department)" : c.getAuthorityName();
        String loc = (c.getRoadName() == null ? "" : c.getRoadName()) + (c.getAddress() == null || c.getAddress().isBlank() ? "" : ", " + c.getAddress());
        String text = "To: " + to + "\n"
                + "Subject: Request to inspect and repair a road hazard - reference " + c.getComplaintId() + "\n\n"
                + "Dear Sir or Madam,\n\n"
                + "I am writing to report a " + c.getIssueType().toLowerCase(Locale.ROOT) + " with " + c.getSeverity().toLowerCase(Locale.ROOT)
                + " severity at " + (loc.isBlank() ? String.format(Locale.ROOT, "%.5f, %.5f", c.getLat(), c.getLng()) : loc)
                + " (approximately " + String.format(Locale.ROOT, "%.5f, %.5f", c.getLat(), c.getLng()) + ").\n\n"
                + "Description: " + (c.getDescription() == null ? "" : c.getDescription()) + "\n\n"
                + "Reported on " + DATE.format(c.getCreatedAt().atZone(zone)) + " through RoadGuard, reference " + c.getComplaintId()
                + ". Please inspect the location, confirm receipt, and tell me what action will be taken and when.\n\n"
                + "Yours faithfully,\n[Your name]\n[Your contact details]\n";
        return new LetterView(c.getComplaintId(), text, "This letter is only a draft written from your report. You choose whether to send it, and to whom.");
    }

    /** Staff record the authority's reply, or that there was none. Only administrators can call this. */
    @Transactional
    public AuthorityView recordAuthorityResponse(User admin, Long complaintId, AuthorityResponseRequest req) {
        Complaint c = complaints.findById(complaintId).orElseThrow(() -> ApiException.notFound("Complaint not found."));
        String text = Inputs.clean(req.response());
        Validation v = new Validation();
        v.check(text.length() >= 5 && text.length() <= 1000, "response", "Record the authority's response (5-1000 characters).");
        v.throwIfInvalid();
        c.setAuthorityResponse(text);
        c.setAuthorityResponseAt(Instant.now());
        complaints.save(c);
        history.save(new ComplaintStatusHistory(c, "AUTHORITY_RESPONSE", null, "Authority response recorded", text, admin, Instant.now()));
        return view(c);
    }

    /** What the citizen sees about the authority side of their own report. */
    @Transactional(readOnly = true)
    public AuthorityView authorityView(User owner, Long complaintId) {
        return view(ownedComplaint(owner, complaintId));
    }

    private AuthorityView view(Complaint c) {
        return new AuthorityView(c.getAuthorityName(), c.getAuthorityResponse(), c.getAuthorityResponseAt(), c.getLetterExportedAt(),
                c.getStatus(), AUTHORITY_DISCLAIMER);
    }

    private Complaint ownedComplaint(User owner, Long id) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint not found."));
        if (!c.getUser().getId().equals(owner.getId())) throw ApiException.notFound("Complaint not found.");
        return c;
    }

    // ------------------------------------------------------------ 2. repair-proof pages

    @Transactional(readOnly = true)
    public RepairPage repairPage(String reportId) {
        Complaint c = complaints.findByComplaintId(reportId).orElseThrow(() -> ApiException.notFound("No report with that reference."));
        ComplaintStatus st = ComplaintStatus.fromLabel(c.getStatus()).orElse(ComplaintStatus.SUBMITTED);
        if (st == ComplaintStatus.REJECTED || st == ComplaintStatus.DUPLICATE) throw ApiException.notFound("No repair record for this report.");
        if (c.sample()) throw ApiException.notFound("Demonstration records have no repair pages.");
        List<ComplaintStatusHistory> events = history.findByComplaint_IdOrderByCreatedAtAscIdAsc(c.getId());
        Instant resolvedAt = null; boolean reopened = false; boolean seenResolved = false;
        List<TimelineStep> steps = new ArrayList<>();
        for (ComplaintStatusHistory h : events) {
            if (ComplaintStatusHistory.STATUS_CHANGE.equals(h.getEventType())) {
                if (ComplaintStatus.RESOLVED.label().equals(h.getToValue())) { resolvedAt = h.getCreatedAt(); seenResolved = true; }
                else if (seenResolved) reopened = true;
                steps.add(new TimelineStep(h.getToValue(), h.getCreatedAt(), publicNote(h.getNote())));
            } else if (ComplaintStatusHistory.RESOLUTION_EVIDENCE.equals(h.getEventType())) {
                steps.add(new TimelineStep("After photo added", h.getCreatedAt(), null));
            } else if (ComplaintStatusHistory.SUBMITTED.equals(h.getEventType())) {
                steps.add(new TimelineStep("Submitted", h.getCreatedAt(), null));
            } else if ("AUTHORITY_RESPONSE".equals(h.getEventType())) {
                steps.add(new TimelineStep("Authority response recorded", h.getCreatedAt(), publicNote(h.getNote())));
            }
        }
        if (st != ComplaintStatus.RESOLVED) resolvedAt = null;
        Long days = resolvedAt == null ? null : Math.max(0, Duration.between(c.getCreatedAt(), resolvedAt).toDays());
        return new RepairPage(c.getComplaintId(), pinIdFor(c), RoadIssueService.canonicalCategory(c.getIssueType()), c.getSeverity(), c.getRoadName(),
                c.getStatus(), Math.round(c.getLat() * 10000) / 10000.0, Math.round(c.getLng() * 10000) / 10000.0,
                c.getDescription(), c.getCreatedAt(), resolvedAt, days, c.hasImage(), c.getResolutionImagePath() != null, reopened,
                c.getAuthorityResponse(), c.getAuthorityResponseAt(), steps, PROOF_DISCLAIMER);
    }

    /** The map pin id used by the public hazard photo endpoints for this report (null if there is no pin). */
    private Long pinIdFor(Complaint c) {
        return issues.findFirstByComplaintRef(c.getId()).map(RoadIssue::getId).orElse(null);
    }

    /** Internal notes are not published on repair pages: only status changes and the fact of a response appear. */
    private static String publicNote(String note) {
        return null;
    }

    /** Resolved hazards, most recently resolved first. Only real (non-sample) reports with at least a resolution date. */
    @Transactional(readOnly = true)
    public List<FixedCard> fixedRecently(int limit) {
        int n = Math.min(Math.max(limit, 1), 50);
        List<FixedCard> out = new ArrayList<>();
        for (Complaint c : complaints.findAll()) {
            if (c.sample() || !ComplaintStatus.RESOLVED.label().equals(c.getStatus())) continue;
            Instant at = null;
            for (ComplaintStatusHistory h : history.findByComplaint_IdOrderByCreatedAtAscIdAsc(c.getId())) {
                if (ComplaintStatusHistory.STATUS_CHANGE.equals(h.getEventType()) && ComplaintStatus.RESOLVED.label().equals(h.getToValue())) at = h.getCreatedAt();
            }
            if (at == null) continue;
            out.add(new FixedCard(c.getComplaintId(), pinIdFor(c), RoadIssueService.canonicalCategory(c.getIssueType()), c.getRoadName(), at, c.getResolutionImagePath() != null));
        }
        out.sort(Comparator.comparing(FixedCard::resolvedAt).reversed());
        return out.size() > n ? out.subList(0, n) : out;
    }

    // ------------------------------------------------------------ 7. your street

    /** The user's confirmations and reports, and what has happened to the hazards they touched. Computed from real records. */
    @Transactional(readOnly = true)
    public StreetCard street(User user) {
        int confirmations = 0, confirmedResolved = 0, confirmedOpen = 0;
        List<StreetItem> items = new ArrayList<>();
        for (HazardVerification v : votes.findByUser_Id(user.getId())) {
            RoadIssue pin = v.getRoadIssue();
            if (pin.sample()) continue;
            ComplaintStatus st = ComplaintStatus.fromLabel(pin.getStatus()).orElse(ComplaintStatus.SUBMITTED);
            if (st == ComplaintStatus.REJECTED || st == ComplaintStatus.DUPLICATE) continue;
            confirmations++;
            String ref = pin.getComplaintRef() == null ? "PIN-" + pin.getId() : complaints.findById(pin.getComplaintRef()).map(Complaint::getComplaintId).orElse("PIN-" + pin.getId());
            if (st == ComplaintStatus.RESOLVED) confirmedResolved++; else if (st.isOpen()) confirmedOpen++;
            String outcome = st == ComplaintStatus.RESOLVED ? "Marked resolved by staff since you confirmed it"
                    : (st == ComplaintStatus.ASSIGNED || st == ComplaintStatus.IN_PROGRESS) ? "Repair planned or in progress"
                    : "Still open";
            items.add(new StreetItem(ref, RoadIssueService.canonicalCategory(pin.getType()), pin.getRoadName(), outcome, v.votedAt()));
        }
        int myResolved = 0, myOpen = 0;
        for (Complaint c : complaints.findByUserOrderByCreatedAtDesc(user)) {
            if (c.sample()) continue;
            ComplaintStatus st = ComplaintStatus.fromLabel(c.getStatus()).orElse(ComplaintStatus.SUBMITTED);
            if (st == ComplaintStatus.RESOLVED) myResolved++;
            else if (st.isOpen()) myOpen++;
        }
        items.sort(Comparator.comparing(StreetItem::at).reversed());
        return new StreetCard(confirmations, confirmedResolved, confirmedOpen, myResolved, myOpen,
                items.size() > 20 ? items.subList(0, 20) : items,
                "Counts only real reports you have confirmed or submitted. A confirmation does not change the official status; "
                        + "'resolved' means RoadGuard staff recorded it.");
    }

}
