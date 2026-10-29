package com.roadguard.controller;

import com.roadguard.dto.RouteDtos.AnalyzeRequest;
import com.roadguard.dto.RouteDtos.GeocodeResult;
import com.roadguard.dto.RouteDtos.RouteAnalysis;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.GeocodingService;
import com.roadguard.service.RoutePlannerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Access(AccessLevel.USER)
public class RouteController {
    private final RoutePlannerService planner;
    private final GeocodingService geocoding;
    private final SessionAuth sessions;

    public RouteController(RoutePlannerService planner, GeocodingService geocoding, SessionAuth sessions) {
        this.planner = planner;
        this.geocoding = geocoding;
        this.sessions = sessions;
    }

    /** Real routes from the routing provider, each scored against reported hazards. */
    @PostMapping("/routes/analyze")
    public RouteAnalysis analyze(@RequestBody AnalyzeRequest req, HttpServletRequest http) {
        return planner.analyze(sessions.requireUser(http), req);
    }

    /** Place search (public so the map search box works for visitors; rate-limited per client). */
    @GetMapping("/geocode")
    @Access(AccessLevel.PUBLIC)
    public List<GeocodeResult> geocode(@RequestParam(value = "q", required = false) String q, HttpServletRequest http) {
        return geocoding.search(q, "geo:" + http.getRemoteAddr());
    }
}
