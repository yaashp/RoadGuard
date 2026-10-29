package com.roadguard.controller;

import com.roadguard.dto.DashboardDtos.MyDashboard;
import com.roadguard.model.User;
import com.roadguard.dto.DashboardDtos.Summary;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Access(AccessLevel.PUBLIC)
public class DashboardController {
    private final DashboardService dashboard;
    private final SessionAuth sessions;

    public DashboardController(DashboardService dashboard, SessionAuth sessions) {
        this.dashboard = dashboard;
        this.sessions = sessions;
    }

    /**
     * Dashboard figures. Administrators get the whole system; everyone else gets only their own data.
     * Anonymous callers are refused.
     */
    @GetMapping
    @Access(AccessLevel.USER)
    public Summary summary(@RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample,
                           HttpServletRequest http) {
        User viewer = sessions.requireUser(http);
        return dashboard.summary(includeSample, viewer.admin() ? null : viewer.getId());
    }

    /** The logged-in user's own dashboard. */
    @GetMapping("/me")
    @Access(AccessLevel.USER)
    public MyDashboard mine(HttpServletRequest http) {
        return dashboard.mine(sessions.requireUser(http));
    }
}
