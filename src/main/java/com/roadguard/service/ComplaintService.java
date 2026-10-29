package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.ComplaintDtos.ComplaintDetail;
import com.roadguard.dto.ComplaintDtos.ComplaintView;
import com.roadguard.dto.ComplaintDtos.CreateRequest;
import com.roadguard.dto.ComplaintDtos.DuplicateRequest;
import com.roadguard.dto.ComplaintDtos.SeverityRequest;
import com.roadguard.dto.ComplaintDtos.StatusRequest;
import com.roadguard.dto.ComplaintDtos.TimelineEntry;
import com.roadguard.dto.PageResult;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.DuplicateSuspectedException;
import com.roadguard.security.SlidingWindowCounter;
import com.roadguard.exception.Validation;
import com.roadguard.exception.ValidationException;
import com.roadguard.geo.RoadKeys;
import com.roadguard.model.Complaint;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.ComplaintStatusHistory;
import com.roadguard.model.IssueCategory;
import com.roadguard.model.Notification;
import com.roadguard.model.Severity;
import com.roadguard.model.User;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.ComplaintStatusHistoryRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ComplaintService {
    /** A photo that arrived with a request (multipart file or decoded data URL). */
    public record ImageUpload(byte[] bytes, String fileName, String contentType) {}

    /** List filters. Null/blank = no filter. */
    public record Filters(Long userId, String status, String severity, String category, String q,
                          Instant from, Instant to, boolean includeSample, boolean oldestFirst) {}

    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final RoadIssueService roadIssues;
    private final FileStorageService files;
    private final NotificationService notifications;
    private final ZoneId zone;
    private final HazardService hazards;
    private final SlidingWindowCounter reportLimiter;

    public ComplaintService(ComplaintRepository complaints, ComplaintStatusHistoryRepository history,
                            RoadIssueService roadIssues, FileStorageService files, NotificationService notifications,
                            RoadGuardProperties props, HazardService hazards) {
        this.zone = ZoneId.of(props.getTimeZone());
        this.hazards = hazards;
        this.reportLimiter = new SlidingWindowCounter(props.getVerification().getMaxReportsPerHour(), 3_600_000L);
        this.complaints = complaints;
        this.history = history;
        this.roadIssues = roadIssues;
        this.files = files;
        this.notifications = notifications;
    }

    /** Builds list filters; dates are interpreted in the configured time zone (`to` is inclusive). */
    public Filters filters(Long userId, String status, String severity, String category, String q, LocalDate from, LocalDate to,
                           boolean includeSample, String sort) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return new Filters(userId, status, severity, category, q, fromInstant, toInstant, includeSample, "oldest".equalsIgnoreCase(sort));
    }

    // ------------------------------------------------------------------ create

    /** Create from the JSON body (an optional base64 data-URL photo is decoded and checked like any upload). */
    @Transactional
    public ComplaintView create(User user, CreateRequest req) {
        ImageUpload upload = null;
        if (req.image() != null && !req.image().isBlank()) {
            FileStorageService.DecodedImage decoded = files.decodeDataUrl(req.image().trim());
            upload = new ImageUpload(decoded.bytes(), null, decoded.declaredType());
        }
        return create(user, req, upload);
    }

    @Transactional
    public ComplaintView create(User user, CreateRequest req, ImageUpload upload) {
        // ---- a retried upload (same key) returns the report it already created instead of making a second one ----
        String requestId = req.clientRequestId() == null ? "" : req.clientRequestId().trim();
        if (!requestId.isEmpty()) {
            if (!requestId.matches("[A-Za-z0-9_-]{8,64}")) throw new ValidationException(Map.of("clientRequestId", "Invalid submission key."));
            var existing = complaints.findByUser_IdAndClientRequestId(user.getId(), requestId);
            if (existing.isPresent()) return toView(existing.get(), user.admin());
        }

        // ---- validate everything before touching the database or the disk ----
        Validation v = new Validation();
        var category = IssueCategory.fromLabel(req.issueType());
        var severity = Severity.fromLabel(req.severity());
        String description = Inputs.clean(req.description());
        String address = Inputs.clean(req.address());
        String roadName = Inputs.clean(req.roadName());
        v.check(category.isPresent(), "issueType", "Choose a valid issue category.");
        v.check(severity.isPresent(), "severity", "Choose Low, Medium or High.");
        v.check(Inputs.validLatLng(req.lat(), req.lng()), "location", "Use your current location or click the map to drop a pin.");
        v.check(description.length() >= 10, "description", "Please describe the problem in at least 10 characters.");
        v.check(description.length() <= 2000, "description", "Description is too long (max 2000 characters).");
        v.check(address.length() <= 200, "address", "Address is too long (max 200 characters).");
        v.check(roadName.length() <= 120, "roadName", "Road name is too long (max 120 characters).");
        ImageInspector.ImageInfo imageInfo = null;
        if (upload != null) {
            try {
                imageInfo = files.inspect(upload.bytes(), upload.fileName(), upload.contentType());
            } catch (ValidationException e) {
                v.fail("image", e.getFieldErrors().getOrDefault("image", e.getMessage()));
            }
        }
        v.throwIfInvalid();

        // ---- possible duplicate? Show what is already there before creating another report (the user may choose to submit anyway) ----
        if (!Boolean.TRUE.equals(req.allowDuplicate())) {
            var similar = hazards.nearby(req.lat(), req.lng(), category.get().label(), null);
            if (!similar.isEmpty()) throw new DuplicateSuspectedException(similar);
        }
        if (!reportLimiter.tryAcquire("report:" + user.getId())) {
            throw ApiException.tooMany("You have submitted a lot of reports in the last hour. Please wait a while before sending more.");
        }

        // ---- persist ----
        Complaint c = new Complaint();
        if (!requestId.isEmpty()) c.setClientRequestId(requestId);
        c.setUser(user);
        c.setIssueType(category.get().label());
        c.setDescription(description);
        c.setLat(req.lat());
        c.setLng(req.lng());
        c.setAddress(address.isEmpty() ? String.format(Locale.ROOT, "%.4f, %.4f", req.lat(), req.lng()) : address);
        c.setRoadName(roadName.isEmpty() ? null : roadName);
        c.setSeverity(severity.get().label());
        c.setStatus(ComplaintStatus.SUBMITTED.label());
        c.setDataOrigin("USER");
        c = complaints.save(c);   // gets its numeric id here

        // Friendly reference, e.g. RG-2026-00424 (the offset keeps it above the original demo numbers)
        c.setComplaintId(String.format(Locale.ROOT, "RG-%d-%05d", Year.now().getValue(), 420 + c.getId()));
        history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.SUBMITTED, null,
                ComplaintStatus.SUBMITTED.label(), "Complaint submitted.", user, c.getCreatedAt()));
        roadIssues.syncFromComplaint(c);

        if (upload != null) {   // last step: if the database work above fails, no file is left behind
            c.setImagePath(files.store(upload.bytes(), imageInfo.extension()));
            c.setImageContentType(imageInfo.contentType());
        }
        c = complaints.save(c);

        notifications.notifyUser(user, Notification.COMPLAINT_SUBMITTED, "Complaint submitted",
                "Your " + c.getIssueType().toLowerCase(Locale.ROOT) + " report " + c.getComplaintId() + " was received.", c.getId());
        notifications.notifyAdmins(Notification.ADMIN_NEW_COMPLAINT, "New complaint " + c.getComplaintId(),
                c.getIssueType() + " (" + c.getSeverity() + ") reported on " + RoadKeys.label(c.getRoadName(), c.getAddress(), c.getLat(), c.getLng()) + ".",
                c.getId(), user.getId());
        return toView(c, user.admin());
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public PageResult<ComplaintView> search(Filters f, int page, int size, boolean forAdmin) {
        Page<Complaint> result = complaints.findAll(spec(f), PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                f.oldestFirst() ? Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
                        : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResult.of(result, c -> toView(c, forAdmin));
    }

    /** All matches without paging, newest first (used by /mine and the legacy list). Capped at 1000. */
    @Transactional(readOnly = true)
    public List<ComplaintView> list(Filters f, boolean forAdmin) {
        return complaints.findAll(spec(f), PageRequest.of(0, 1000, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .getContent().stream().map(c -> toView(c, forAdmin)).toList();
    }

    /** The complaint if the caller owns it or is an administrator; otherwise "not found" (so ids cannot be probed). */
    @Transactional(readOnly = true)
    public Complaint load(User caller, Long id) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint not found."));
        if (!caller.admin() && !c.getUser().getId().equals(caller.getId())) throw ApiException.notFound("Complaint not found.");
        return c;
    }

    @Transactional(readOnly = true)
    public ComplaintDetail detail(User caller, Long id) {
        Complaint c = load(caller, id);
        return new ComplaintDetail(toView(c, caller.admin()), timeline(c, caller.admin()));
    }

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(Long id, User caller) {
        return timeline(load(caller, id), caller.admin());
    }

    List<TimelineEntry> timeline(Complaint c, boolean forAdmin) {
        List<TimelineEntry> out = new ArrayList<>();
        for (ComplaintStatusHistory h : history.findByComplaint_IdOrderByCreatedAtAscIdAsc(c.getId())) {
            out.add(new TimelineEntry(h.getId(), h.getEventType(), h.getFromValue(), h.getToValue(), h.getNote(),
                    actor(h, c, forAdmin), h.getCreatedAt()));
        }
        return out;
    }

    private static String actor(ComplaintStatusHistory h, Complaint c, boolean forAdmin) {
        User by = h.getChangedBy();
        if (by == null) return "System";
        if (forAdmin) return by.getName() + (by.admin() ? " (admin)" : "");
        if (by.getId().equals(c.getUser().getId())) return "You";
        return "RoadGuard staff";
    }

    public record ImageData(String contentType, byte[] bytes) {}

    @Transactional(readOnly = true)
    public ImageData image(User caller, Long id) {
        Complaint c = load(caller, id);
        if (c.getImagePath() != null) {
            byte[] bytes = files.read(c.getImagePath()).orElseThrow(() -> ApiException.notFound("The photo file is missing."));
            return new ImageData(c.getImageContentType() == null ? "application/octet-stream" : c.getImageContentType(), bytes);
        }
        String legacy = c.getImage();   // pre-upgrade rows stored the photo as a data URL
        if (legacy != null && legacy.startsWith("data:image/")) {
            try {
                FileStorageService.DecodedImage d = files.decodeDataUrl(legacy);
                String type = d.declaredType().toLowerCase(Locale.ROOT);
                if (type.equals("image/jpeg") || type.equals("image/png") || type.equals("image/webp")) return new ImageData(type, d.bytes());
            } catch (ApiException ignored) {
                // fall through to 404
            }
        }
        throw ApiException.notFound("This complaint has no photo.");
    }

    @Transactional
    public ComplaintView attachImage(User caller, Long id, ImageUpload upload) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint not found."));
        if (!c.getUser().getId().equals(caller.getId())) throw ApiException.notFound("Complaint not found.");
        if (upload == null) throw new ValidationException(Map.of("image", "Choose a photo to upload."));
        ImageInspector.ImageInfo info = files.inspect(upload.bytes(), upload.fileName(), upload.contentType());
        String old = c.getImagePath();
        c.setImagePath(files.store(upload.bytes(), info.extension()));
        c.setImageContentType(info.contentType());
        c.setUpdatedAt(Instant.now());
        c = complaints.save(c);
        files.deleteQuietly(old);
        return toView(c, caller.admin());
    }

    // ------------------------------------------------------------------ administrator changes

    @Transactional
    public ComplaintView changeStatus(User admin, Long id, StatusRequest req) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint not found."));
        var target = ComplaintStatus.fromLabel(req.status());
        if (target.isEmpty()) throw new ValidationException(Map.of("status", "Choose a valid status."));
        ComplaintStatus from = ComplaintStatus.fromLabel(c.getStatus()).orElse(ComplaintStatus.SUBMITTED);
        String note = Inputs.clean(req.note());

        Validation v = new Validation();
        v.check(note.length() <= 500, "note", "Note is too long (max 500 characters).");
        v.check(!target.get().needsExplanation() || note.length() >= 5, "note", "Please give a reason (at least 5 characters) when rejecting a complaint.");
        if (target.get() == ComplaintStatus.DUPLICATE) v.fail("status", "Use \"Mark as duplicate\" and name the original report.");
        v.throwIfInvalid();
        if (target.get() == from) throw new ValidationException(Map.of("status", "This complaint is already \"" + from.label() + "\"."));
        if (!from.canMoveTo(target.get())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_TRANSITION",
                    "A complaint cannot move from \"" + from.label() + "\" to \"" + target.get().label() + "\". Allowed next steps: "
                            + String.join(", ", from.allowedNext().stream().map(ComplaintStatus::label).toList()) + ".");
        }

        c.setStatus(target.get().label());
        if (from == ComplaintStatus.DUPLICATE) c.setDuplicateOfId(null);
        c.setUpdatedAt(Instant.now());
        c = complaints.save(c);
        history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.STATUS_CHANGE, from.label(), target.get().label(),
                note.isEmpty() ? null : note, admin, Instant.now()));
        roadIssues.syncFromComplaint(c);

        if (!c.getUser().getId().equals(admin.getId())) {
            notifications.notifyUser(c.getUser(), Notification.STATUS_CHANGED, "Complaint " + c.getComplaintId() + ": " + target.get().label(),
                    "Status changed from " + from.label() + " to " + target.get().label() + "."
                            + (note.isEmpty() ? "" : " Note: " + note), c.getId());
        }
        return toView(c, true);
    }

    @Transactional
    public ComplaintView changeSeverity(User admin, Long id, SeverityRequest req) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint not found."));
        var target = Severity.fromLabel(req.severity());
        if (target.isEmpty()) throw new ValidationException(Map.of("severity", "Choose Low, Medium or High."));
        String note = Inputs.clean(req.note());
        if (note.length() > 500) throw new ValidationException(Map.of("note", "Note is too long (max 500 characters)."));
        String from = Severity.fromLabel(c.getSeverity()).map(Severity::label).orElse(c.getSeverity());
        if (from.equals(target.get().label())) throw new ValidationException(Map.of("severity", "Severity is already " + from + "."));

        c.setSeverity(target.get().label());
        c.setUpdatedAt(Instant.now());
        c = complaints.save(c);
        history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.SEVERITY_CHANGE, from, target.get().label(),
                note.isEmpty() ? null : note, admin, Instant.now()));
        roadIssues.syncFromComplaint(c);
        if (!c.getUser().getId().equals(admin.getId())) {
            notifications.notifyUser(c.getUser(), Notification.SEVERITY_CHANGED, "Complaint " + c.getComplaintId() + " severity updated",
                    "Severity changed from " + from + " to " + target.get().label() + ".", c.getId());
        }
        return toView(c, true);
    }

    /** Administrator marks a report as a duplicate of an existing valid report. The report is kept (never deleted) and can be un-merged. */
    @Transactional
    public ComplaintView markDuplicate(User admin, Long id, DuplicateRequest req) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint not found."));
        String ref = Inputs.clean(req.duplicateOf());
        String note = Inputs.clean(req.note());
        Validation v = new Validation();
        v.check(!ref.isEmpty(), "duplicateOf", "Enter the reference number of the original report.");
        v.check(note.length() >= 5 && note.length() <= 500, "note", "Please explain why this is a duplicate (5-500 characters).");
        v.throwIfInvalid();
        Complaint original = complaints.findByComplaintId(ref).orElse(null);
        if (original == null) throw new ValidationException(Map.of("duplicateOf", "No report has that reference number."));
        if (original.getId().equals(c.getId())) throw new ValidationException(Map.of("duplicateOf", "A report cannot be a duplicate of itself."));
        ComplaintStatus originalStatus = ComplaintStatus.fromLabel(original.getStatus()).orElse(ComplaintStatus.SUBMITTED);
        if (originalStatus == ComplaintStatus.DUPLICATE || originalStatus == ComplaintStatus.REJECTED) {
            throw new ValidationException(Map.of("duplicateOf", "Choose an original report that is still valid."));
        }
        ComplaintStatus from = ComplaintStatus.fromLabel(c.getStatus()).orElse(ComplaintStatus.SUBMITTED);
        if (!from.canMoveTo(ComplaintStatus.DUPLICATE)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_TRANSITION",
                    "A complaint cannot move from \"" + from.label() + "\" to \"Duplicate\". Allowed next steps: "
                            + String.join(", ", from.allowedNext().stream().map(ComplaintStatus::label).toList()) + ".");
        }
        c.setStatus(ComplaintStatus.DUPLICATE.label());
        c.setDuplicateOfId(original.getId());
        c.setUpdatedAt(Instant.now());
        c = complaints.save(c);
        history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.DUPLICATE_MARKED, from.label(), ComplaintStatus.DUPLICATE.label(),
                "Duplicate of " + original.getComplaintId() + ". " + note, admin, Instant.now()));
        roadIssues.syncFromComplaint(c);
        if (!c.getUser().getId().equals(admin.getId())) {
            notifications.notifyUser(c.getUser(), Notification.STATUS_CHANGED, "Complaint " + c.getComplaintId() + ": marked as duplicate",
                    "It matches " + original.getComplaintId() + ", which is already being tracked. " + note, c.getId());
        }
        return toView(c, true);
    }

    /** Optional "after" photo uploaded by staff as repair evidence. The original photo and all earlier history are kept. */
    @Transactional
    public ComplaintView attachResolutionPhoto(User admin, Long id, ImageUpload upload) {
        Complaint c = complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint not found."));
        ComplaintStatus st = ComplaintStatus.fromLabel(c.getStatus()).orElse(ComplaintStatus.SUBMITTED);
        if (st != ComplaintStatus.IN_PROGRESS && st != ComplaintStatus.RESOLVED) {
            throw new ValidationException(Map.of("image", "Repair evidence can be added once the repair is in progress or resolved."));
        }
        if (upload == null) throw new ValidationException(Map.of("image", "Choose a photo to upload."));
        ImageInspector.ImageInfo info = files.inspect(upload.bytes(), upload.fileName(), upload.contentType());
        String old = c.getResolutionImagePath();
        c.setResolutionImagePath(files.store(upload.bytes(), info.extension()));
        c.setResolutionImageContentType(info.contentType());
        c.setUpdatedAt(Instant.now());
        c = complaints.save(c);
        files.deleteQuietly(old);
        history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.RESOLUTION_EVIDENCE, null, "After photo", "Repair evidence photo added.", admin, Instant.now()));
        if (!c.getUser().getId().equals(admin.getId())) {
            notifications.notifyUser(c.getUser(), Notification.STATUS_CHANGED, "Complaint " + c.getComplaintId() + ": repair photo added",
                    "Staff added a photo showing the repair work.", c.getId());
        }
        return toView(c, true);
    }

    @Transactional(readOnly = true)
    public ImageData resolutionImage(User caller, Long id) {
        Complaint c = load(caller, id);
        if (c.getResolutionImagePath() == null) throw ApiException.notFound("This complaint has no repair photo.");
        byte[] bytes = files.read(c.getResolutionImagePath()).orElseThrow(() -> ApiException.notFound("The photo file is missing."));
        return new ImageData(c.getResolutionImageContentType() == null ? "application/octet-stream" : c.getResolutionImageContentType(), bytes);
    }

    // ------------------------------------------------------------------ mapping / filtering

    ComplaintView toView(Complaint c, boolean forAdmin) {
        ComplaintStatus st = ComplaintStatus.fromLabel(c.getStatus()).orElse(null);
        List<String> next = forAdmin && st != null ? st.allowedNext().stream().map(ComplaintStatus::label).toList() : List.of();
        boolean hasImage = c.hasImage();
        return new ComplaintView(c.getId(), c.getComplaintId(), c.getUser().getId(), c.getUser().getName(),
                Analytics.category(c), c.getDescription(), c.getLat(), c.getLng(), c.getAddress(),
                RoadKeys.label(c.getRoadName(), c.getAddress(), c.getLat(), c.getLng()),
                Analytics.severity(c), Analytics.status(c), hasImage,
                hasImage ? "/api/complaints/" + c.getId() + "/image" : null,
                c.getResolutionImagePath() != null, c.getResolutionImagePath() != null ? "/api/complaints/" + c.getId() + "/resolution-photo" : null,
                c.getDuplicateOfId() == null ? null : complaints.findById(c.getDuplicateOfId()).map(Complaint::getComplaintId).orElse(null),
                c.sample(), c.getCreatedAt(), c.getUpdatedAt(), next);
    }

    private static Specification<Complaint> spec(Filters f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.userId() != null) ps.add(cb.equal(root.get("user").get("id"), f.userId()));
            if (notBlank(f.status())) {
                ps.add(cb.equal(root.get("status"), ComplaintStatus.fromLabel(f.status()).map(ComplaintStatus::label).orElse("\u0000")));
            }
            if (notBlank(f.severity())) {
                ps.add(cb.equal(root.get("severity"), Severity.fromLabel(f.severity()).map(Severity::label).orElse("\u0000")));
            }
            if (notBlank(f.category())) {
                ps.add(cb.equal(root.get("issueType"), IssueCategory.fromLabel(f.category()).map(IssueCategory::label).orElse("\u0000")));
            }
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), f.from()));
            if (f.to() != null) ps.add(cb.lessThan(root.<Instant>get("createdAt"), f.to()));
            if (!f.includeSample()) {
                ps.add(cb.or(cb.isNull(root.get("dataOrigin")), cb.notEqual(root.get("dataOrigin"), "SAMPLE")));
            }
            if (notBlank(f.q())) {
                String pattern = "%" + escapeLike(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
                List<Predicate> any = new ArrayList<>();
                for (String field : new String[]{"complaintId", "address", "roadName", "description", "issueType"}) {
                    Expression<String> expr = cb.lower(root.<String>get(field));
                    any.add(cb.like(expr, pattern, '\\'));
                }
                ps.add(cb.or(any.toArray(new Predicate[0])));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

}
