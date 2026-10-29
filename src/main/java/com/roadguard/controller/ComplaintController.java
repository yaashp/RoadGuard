package com.roadguard.controller;

import com.roadguard.dto.ComplaintDtos.ComplaintDetail;
import com.roadguard.dto.ComplaintDtos.ComplaintView;
import com.roadguard.dto.ComplaintDtos.CreateRequest;
import com.roadguard.dto.ComplaintDtos.StatusRequest;
import com.roadguard.dto.ComplaintDtos.TimelineEntry;
import com.roadguard.exception.ApiException;
import com.roadguard.model.User;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.ComplaintService;
import com.roadguard.service.ComplaintService.ImageData;
import com.roadguard.service.ComplaintService.ImageUpload;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Citizen-facing complaint endpoints. A user only ever sees complaints they own: the service answers
 * "not found" for anyone else's id. Administrators use /api/admin/... (and the two legacy admin routes below).
 */
@RestController
@RequestMapping("/api/complaints")
@Access(AccessLevel.USER)
public class ComplaintController {
    private final ComplaintService complaints;
    private final SessionAuth sessions;

    public ComplaintController(ComplaintService complaints, SessionAuth sessions) {
        this.complaints = complaints;
        this.sessions = sessions;
    }

    /** The logged-in user's complaints (newest first), optionally filtered. */
    @GetMapping("/mine")
    public List<ComplaintView> mine(@RequestParam(value = "status", required = false) String status,
                                    @RequestParam(value = "severity", required = false) String severity,
                                    @RequestParam(value = "category", required = false) String category,
                                    @RequestParam(value = "q", required = false) String q,
                                    @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(value = "sort", required = false, defaultValue = "newest") String sort,
                                    HttpServletRequest http) {
        User user = sessions.requireUser(http);
        return complaints.list(complaints.filters(user.getId(), status, severity, category, q, from, to, true, sort), false);
    }

    /** Create from a JSON body (the original contract). The optional "image" is a base64 data URL. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintView createJson(@RequestBody CreateRequest req, HttpServletRequest http) {
        return complaints.create(sessions.requireUser(http), req);
    }

    /** Create from a form with an optional photo (multipart). Used by the web app. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintView createMultipart(@RequestParam(value = "issueType", required = false) String issueType,
                                         @RequestParam(value = "description", required = false) String description,
                                         @RequestParam(value = "severity", required = false) String severity,
                                         @RequestParam(value = "lat", required = false) Double lat,
                                         @RequestParam(value = "lng", required = false) Double lng,
                                         @RequestParam(value = "address", required = false) String address,
                                         @RequestParam(value = "roadName", required = false) String roadName,
                                         @RequestParam(value = "clientRequestId", required = false) String clientRequestId,
                                         @RequestParam(value = "allowDuplicate", required = false) Boolean allowDuplicate,
                                         @RequestParam(value = "image", required = false) MultipartFile image,
                                         HttpServletRequest http) {
        User user = sessions.requireUser(http);
        CreateRequest req = new CreateRequest(issueType, description, null, lat, lng, address, roadName, severity, clientRequestId, allowDuplicate);
        return complaints.create(user, req, toUpload(image));
    }

    @GetMapping("/{id}")
    public ComplaintDetail detail(@PathVariable("id") Long id, HttpServletRequest http) {
        return complaints.detail(sessions.requireUser(http), id);
    }

    /** Status history of one complaint (oldest first). */
    @GetMapping("/{id}/history")
    public List<TimelineEntry> history(@PathVariable("id") Long id, HttpServletRequest http) {
        return complaints.timeline(id, sessions.requireUser(http));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable("id") Long id, HttpServletRequest http) {
        ImageData img = complaints.image(sessions.requireUser(http), id);
        String ext = img.contentType().equals("image/png") ? "png" : img.contentType().equals("image/webp") ? "webp" : "jpg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"complaint-" + id + "." + ext + "\"")
                .body(img.bytes());
    }

    /** The "after" photo staff attached as repair evidence (owner or administrator). */
    @GetMapping("/{id}/resolution-photo")
    public ResponseEntity<byte[]> resolutionPhoto(@PathVariable("id") Long id, HttpServletRequest http) {
        ImageData img = complaints.resolutionImage(sessions.requireUser(http), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(img.bytes());
    }

    /** Attach (or replace) the photo of one of your own complaints. */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplaintView uploadImage(@PathVariable("id") Long id,
                                     @RequestParam(value = "image", required = false) MultipartFile image,
                                     HttpServletRequest http) {
        return complaints.attachImage(sessions.requireUser(http), id, toUpload(image));
    }

    // ---- legacy administrator routes kept so existing clients keep working ----

    /** Administrator only: every complaint from every user (newest first, max 1000). Prefer GET /api/admin/complaints. */
    @GetMapping
    @Access(AccessLevel.ADMIN)
    public List<ComplaintView> all(HttpServletRequest http) {
        sessions.requireAdmin(http);
        return complaints.list(complaints.filters(null, null, null, null, null, null, null, true, "newest"), true);
    }

    /** Administrator only: move a complaint along the timeline. Prefer PATCH /api/admin/complaints/{id}/status. */
    @PatchMapping("/{id}/status")
    @Access(AccessLevel.ADMIN)
    public ComplaintView updateStatus(@PathVariable("id") Long id, @RequestBody StatusRequest req, HttpServletRequest http) {
        return complaints.changeStatus(sessions.requireAdmin(http), id, req);
    }

    // ---- helpers ----

    static ImageUpload toUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try {
            return new ImageUpload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded photo could not be read.");
        }
    }

}
