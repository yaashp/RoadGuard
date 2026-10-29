package com.roadguard.controller;

import com.roadguard.dto.HistoryDtos.Hotspot;
import com.roadguard.dto.HistoryDtos.PeriodComparison;
import com.roadguard.dto.HistoryDtos.RoadHistory;
import com.roadguard.dto.HistoryDtos.RoadSummary;
import com.roadguard.model.User;
import com.roadguard.security.SessionAuth;
import jakarta.servlet.http.HttpServletRequest;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.service.RoadHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roads")
@Access(AccessLevel.PUBLIC)
public class RoadHistoryController {
    private final RoadHistoryService roads;
    private final SessionAuth sessions;

    public RoadHistoryController(RoadHistoryService roads, SessionAuth sessions) {
        this.roads = roads;
        this.sessions = sessions;
    }

    /** Admins see every road; other logged-in users see only roads they reported on. Anonymous callers are refused. */
    private Long scope(HttpServletRequest http) {
        User viewer = sessions.requireUser(http);
        return viewer.admin() ? null : viewer.getId();
    }

    @GetMapping("/search")
    @Access(AccessLevel.USER)
    public List<RoadSummary> search(@RequestParam(value = "q", required = false, defaultValue = "") String q,
                                    @RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample,
                                    @RequestParam(value = "limit", defaultValue = "20") int limit,
                                    HttpServletRequest http) {
        return roads.search(q, includeSample, Math.min(Math.max(limit, 1), 100), scope(http));
    }

    @GetMapping("/history")
    @Access(AccessLevel.USER)
    public RoadHistory history(@RequestParam("road") String road,
                               @RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample,
                               HttpServletRequest http) {
        return roads.history(road, includeSample, scope(http));
    }

    @GetMapping("/hotspots")
    @Access(AccessLevel.USER)
    public List<Hotspot> hotspots(@RequestParam(value = "limit", defaultValue = "10") int limit,
                                  @RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample,
                                  HttpServletRequest http) {
        return roads.hotspots(Math.min(Math.max(limit, 1), 50), includeSample, scope(http));
    }

    @GetMapping("/compare")
    @Access(AccessLevel.USER)
    public PeriodComparison compare(@RequestParam(value = "road", required = false) String road,
                                    @RequestParam(value = "periodDays", defaultValue = "30") int periodDays,
                                    @RequestParam(value = "includeSample", defaultValue = "true") boolean includeSample,
                                    HttpServletRequest http) {
        return roads.compare(road, periodDays, includeSample, scope(http));
    }
}
