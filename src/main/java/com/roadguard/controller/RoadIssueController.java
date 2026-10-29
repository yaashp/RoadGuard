package com.roadguard.controller;

import com.roadguard.dto.MapDtos.RoadIssueView;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.RoadIssueService;
import com.roadguard.service.RoadIssueService.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public map data: road issues with no personal information. Sample/demo pins are flagged. */
@RestController
@RequestMapping("/api/road-issues")
@Access(AccessLevel.PUBLIC)
public class RoadIssueController {
    private final RoadIssueService issues;
    private final SessionAuth sessions;

    public RoadIssueController(RoadIssueService issues, SessionAuth sessions) {
        this.issues = issues;
        this.sessions = sessions;
    }

    @GetMapping
    public List<RoadIssueView> list(@RequestParam(value = "category", required = false) String category,
                                    @RequestParam(value = "severity", required = false) String severity,
                                    @RequestParam(value = "status", required = false) String status,
                                    @RequestParam(value = "q", required = false) String q,
                                    @RequestParam(value = "maxAgeDays", required = false) Integer maxAgeDays,
                                    @RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample,
                                    @RequestParam(value = "includeResolved", defaultValue = "true") boolean includeResolved,
                                    @RequestParam(value = "mine", defaultValue = "false") boolean mine,
                                    HttpServletRequest http) {
        // mine=true: only the logged-in user's own reports. Enforced here, not just in the UI.
        Long ownerId = mine ? sessions.requireUser(http).getId() : null;
        return issues.list(new Filter(category, severity, status, q, maxAgeDays, includeSample, includeResolved, false, ownerId));
    }

    @GetMapping("/{id}")
    public RoadIssueView get(@PathVariable("id") Long id) {
        return issues.get(id);
    }
}
