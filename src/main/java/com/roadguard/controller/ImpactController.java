package com.roadguard.controller;

import com.roadguard.dto.InsightDtos.ImpactRules;
import com.roadguard.dto.InsightDtos.ImpactSummary;
import com.roadguard.dto.InsightDtos.Leaderboard;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.ImpactService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/impact")
@Access(AccessLevel.PUBLIC)
public class ImpactController {
    private final ImpactService impact;
    private final SessionAuth sessions;

    public ImpactController(ImpactService impact, SessionAuth sessions) {
        this.impact = impact;
        this.sessions = sessions;
    }

    @GetMapping("/rules")
    public ImpactRules rules() { return impact.rules(); }

    @GetMapping("/leaderboard")
    public Leaderboard leaderboard(@RequestParam(value = "month", required = false) String month) { return impact.leaderboard(month); }

    @GetMapping("/me")
    @Access(AccessLevel.USER)
    public ImpactSummary me(HttpServletRequest http) { return impact.me(sessions.requireUser(http)); }
}
