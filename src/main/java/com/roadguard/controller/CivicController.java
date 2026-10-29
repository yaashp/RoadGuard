package com.roadguard.controller;

import com.roadguard.dto.CivicDtos.*;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.CivicService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Access(AccessLevel.PUBLIC)
public class CivicController {
    private final CivicService civic;
    private final SessionAuth sessions;

    public CivicController(CivicService civic, SessionAuth sessions) {
        this.civic = civic;
        this.sessions = sessions;
    }

    // ---- 1. authority loop (owner side) ----
    @GetMapping("/complaints/{id}/authority")
    @Access(AccessLevel.USER)
    public AuthorityView authority(@PathVariable("id") Long id, HttpServletRequest http) {
        return civic.authorityView(sessions.requireUser(http), id);
    }

    @PutMapping("/complaints/{id}/authority")
    @Access(AccessLevel.USER)
    public AuthorityView setAuthority(@PathVariable("id") Long id, @RequestBody AuthorityRequest req, HttpServletRequest http) {
        return civic.setAuthority(sessions.requireUser(http), id, req);
    }

    @GetMapping("/complaints/{id}/letter")
    @Access(AccessLevel.USER)
    public LetterView letter(@PathVariable("id") Long id, HttpServletRequest http) {
        return civic.letter(sessions.requireUser(http), id);
    }

    // ---- 1. authority loop (staff side) ----
    @PutMapping("/admin/complaints/{id}/authority-response")
    @Access(AccessLevel.ADMIN)
    public AuthorityView recordResponse(@PathVariable("id") Long id, @RequestBody AuthorityResponseRequest req, HttpServletRequest http) {
        return civic.recordAuthorityResponse(sessions.requireAdmin(http), id, req);
    }

    // ---- 2. repair-proof pages (public) and the fixed feed ----
    @GetMapping("/repairs/{reportId}")
    public RepairPage repair(@PathVariable("reportId") String reportId) {
        return civic.repairPage(reportId);
    }

    @GetMapping("/repairs/fixed")
    public List<FixedCard> fixed(@RequestParam(value = "limit", defaultValue = "12") int limit) {
        return civic.fixedRecently(limit);
    }

    // ---- 7. your street ----
    @GetMapping("/impact/street")
    @Access(AccessLevel.USER)
    public StreetCard street(HttpServletRequest http) {
        return civic.street(sessions.requireUser(http));
    }
}
