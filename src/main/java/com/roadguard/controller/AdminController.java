package com.roadguard.controller;

import com.roadguard.dto.AdminDtos.AdminComplaintDetail;
import com.roadguard.dto.AdminDtos.Overview;
import com.roadguard.dto.ComplaintDtos.ComplaintView;
import com.roadguard.dto.ComplaintDtos.DuplicateRequest;
import com.roadguard.dto.ComplaintDtos.SeverityRequest;
import com.roadguard.dto.ComplaintDtos.StatusRequest;
import com.roadguard.dto.MapDtos.RoadIssueRequest;
import com.roadguard.dto.MapDtos.RoadIssueView;
import com.roadguard.dto.PageResult;
import com.roadguard.model.Complaint;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.ComplaintService;
import com.roadguard.service.ComplaintService.Filters;
import com.roadguard.dto.InsightDtos.AuditEntry;
import com.roadguard.dto.InsightDtos.DismissRequest;
import com.roadguard.dto.InsightDtos.Insights;
import com.roadguard.service.AdminInsightsService;
import com.roadguard.service.DashboardService;
import com.roadguard.service.RoadIssueService;
import com.roadguard.service.RoadIssueService.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Administrator API. The class-level @Access(ADMIN) is enforced by AccessInterceptor before any method runs,
 * and every method re-checks through SessionAuth.requireAdmin (defence in depth, and it yields the acting admin).
 */
@RestController
@RequestMapping("/api/admin")
@Access(AccessLevel.ADMIN)
public class AdminController {
    private final ComplaintService complaints;
    private final DashboardService dashboard;
    private final RoadIssueService roadIssues;
    private final SessionAuth sessions;
    private final AdminInsightsService insights;

    public AdminController(ComplaintService complaints, DashboardService dashboard, RoadIssueService roadIssues,
                           SessionAuth sessions, AdminInsightsService insights) {
        this.insights = insights;
        this.complaints = complaints;
        this.dashboard = dashboard;
        this.roadIssues = roadIssues;
        this.sessions = sessions;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample, HttpServletRequest http) {
        sessions.requireAdmin(http);
        return dashboard.adminOverview(includeSample);
    }

    @GetMapping("/complaints")
    public PageResult<ComplaintView> complaints(@RequestParam(value = "status", required = false) String status,
                                                @RequestParam(value = "severity", required = false) String severity,
                                                @RequestParam(value = "category", required = false) String category,
                                                @RequestParam(value = "q", required = false) String q,
                                                @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample,
                                                @RequestParam(value = "sort", defaultValue = "newest") String sort,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "size", defaultValue = "15") int size,
                                                HttpServletRequest http) {
        sessions.requireAdmin(http);
        Filters f = complaints.filters(null, status, severity, category, q, from, to, includeSample, sort);
        return complaints.search(f, page, size, true);
    }

    @GetMapping("/complaints/{id}")
    public AdminComplaintDetail complaint(@PathVariable("id") Long id, HttpServletRequest http) {
        var admin = sessions.requireAdmin(http);
        Complaint c = complaints.load(admin, id);
        var detail = complaints.detail(admin, id);
        return new AdminComplaintDetail(detail.complaint(), detail.timeline(), c.getUser().getEmail(), c.getUser().getPhone());
    }

    @PatchMapping("/complaints/{id}/status")
    public ComplaintView changeStatus(@PathVariable("id") Long id, @RequestBody StatusRequest req, HttpServletRequest http) {
        return complaints.changeStatus(sessions.requireAdmin(http), id, req);
    }

    @PatchMapping("/complaints/{id}/severity")
    public ComplaintView changeSeverity(@PathVariable("id") Long id, @RequestBody SeverityRequest req, HttpServletRequest http) {
        return complaints.changeSeverity(sessions.requireAdmin(http), id, req);
    }

    @PostMapping("/complaints/{id}/duplicate")
    public ComplaintView markDuplicate(@PathVariable("id") Long id, @RequestBody DuplicateRequest req, HttpServletRequest http) {
        return complaints.markDuplicate(sessions.requireAdmin(http), id, req);
    }

    /** Optional repair evidence ("after" photo). */
    @PostMapping(value = "/complaints/{id}/resolution-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplaintView resolutionPhoto(@PathVariable("id") Long id, @RequestParam(value = "image", required = false) MultipartFile image, HttpServletRequest http) {
        return complaints.attachResolutionPhoto(sessions.requireAdmin(http), id, ComplaintController.toUpload(image));
    }

    /** Priority queue, hotspots and counters. The priority score is an aid for human review only. */
    @GetMapping("/insights")
    public Insights insights(@RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample,
                             @RequestParam(value = "limit", defaultValue = "30") int limit, HttpServletRequest http) {
        sessions.requireAdmin(http);
        return insights.insights(includeSample, limit);
    }

    @PostMapping("/hazards/{id}/dismiss-flag")
    public Map<String, String> dismissFlag(@PathVariable("id") Long id, @RequestBody DismissRequest req, HttpServletRequest http) {
        insights.dismissFlag(sessions.requireAdmin(http), id, req.note());
        return Map.of("status", "flag dismissed");
    }

    @GetMapping("/audit")
    public List<AuditEntry> audit(@RequestParam(value = "limit", defaultValue = "50") int limit, HttpServletRequest http) {
        sessions.requireAdmin(http);
        return insights.audit(limit);
    }

    // ---- road issue (map pin) management ----

    @GetMapping("/road-issues")
    public List<RoadIssueView> roadIssues(@RequestParam(value = "q", required = false) String q, HttpServletRequest http) {
        sessions.requireAdmin(http);
        return roadIssues.list(new Filter(null, null, null, q, null, true, true, true, null));
    }

    @PostMapping("/road-issues")
    @ResponseStatus(HttpStatus.CREATED)
    public RoadIssueView createRoadIssue(@RequestBody RoadIssueRequest req, HttpServletRequest http) {
        sessions.requireAdmin(http);
        return roadIssues.create(req);
    }

    @PutMapping("/road-issues/{id}")
    public RoadIssueView updateRoadIssue(@PathVariable("id") Long id, @RequestBody RoadIssueRequest req, HttpServletRequest http) {
        sessions.requireAdmin(http);
        return roadIssues.update(id, req);
    }

    @DeleteMapping("/road-issues/{id}")
    public Map<String, String> deleteRoadIssue(@PathVariable("id") Long id, HttpServletRequest http) {
        sessions.requireAdmin(http);
        roadIssues.delete(id);
        return Map.of("status", "deleted");
    }
}
