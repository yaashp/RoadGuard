package com.roadguard.controller;

import com.roadguard.dto.SavedRouteDtos.ReanalyzeResponse;
import com.roadguard.dto.SavedRouteDtos.RenameRequest;
import com.roadguard.dto.SavedRouteDtos.SaveRequest;
import com.roadguard.dto.SavedRouteDtos.SaveResponse;
import com.roadguard.dto.SavedRouteDtos.SavedRouteView;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.SavedRouteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saved-routes")
@Access(AccessLevel.USER)
public class SavedRouteController {
    private final SavedRouteService routes;
    private final SessionAuth sessions;

    public SavedRouteController(SavedRouteService routes, SessionAuth sessions) {
        this.routes = routes;
        this.sessions = sessions;
    }

    @GetMapping
    public List<SavedRouteView> list(HttpServletRequest http) {
        return routes.list(sessions.requireUser(http));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaveResponse save(@RequestBody SaveRequest req, HttpServletRequest http) {
        return routes.save(sessions.requireUser(http), req);
    }

    @GetMapping("/{id}")
    public SavedRouteView get(@PathVariable("id") Long id, HttpServletRequest http) {
        return routes.get(sessions.requireUser(http), id);
    }

    @PatchMapping("/{id}")
    public SavedRouteView rename(@PathVariable("id") Long id, @RequestBody RenameRequest req, HttpServletRequest http) {
        return routes.rename(sessions.requireUser(http), id, req.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id, HttpServletRequest http) {
        routes.delete(sessions.requireUser(http), id);
    }

    /** Re-score the saved route against current hazards and compare with the previous score. */
    @PostMapping("/{id}/analyze")
    public ReanalyzeResponse reanalyze(@PathVariable("id") Long id, HttpServletRequest http) {
        return routes.reanalyze(sessions.requireUser(http), id);
    }
}
