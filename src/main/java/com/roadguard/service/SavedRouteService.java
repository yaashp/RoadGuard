package com.roadguard.service;

import com.roadguard.dto.RouteDtos.AnalyzeRequest;
import com.roadguard.dto.RouteDtos.Point;
import com.roadguard.dto.RouteDtos.RouteAnalysis;
import com.roadguard.dto.RouteDtos.RouteOption;
import com.roadguard.dto.SavedRouteDtos.ReanalyzeResponse;
import com.roadguard.dto.SavedRouteDtos.SaveRequest;
import com.roadguard.dto.SavedRouteDtos.SaveResponse;
import com.roadguard.dto.SavedRouteDtos.SavedRouteView;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.Validation;
import com.roadguard.model.SavedRoute;
import com.roadguard.model.User;
import com.roadguard.repository.SavedRouteRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Saved routes belong to one user. Every lookup goes through findByIdAndUser_Id, so another user's id
 * behaves exactly like an id that does not exist. The network call to the routing service is deliberately
 * made outside any database transaction.
 */
@Service
public class SavedRouteService {
    static final int MAX_PER_USER = 25;

    private final SavedRouteRepository routes;
    private final RoutePlannerService planner;

    public SavedRouteService(SavedRouteRepository routes, RoutePlannerService planner) {
        this.routes = routes;
        this.planner = planner;
    }

    public List<SavedRouteView> list(User user) {
        return routes.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream().map(SavedRouteService::toView).toList();
    }

    public SavedRouteView get(User user, Long id) {
        return toView(find(user, id));
    }

    public SaveResponse save(User user, SaveRequest req) {
        String name = Inputs.clean(req.name());
        Validation v = new Validation();
        v.check(!name.isEmpty() && name.length() <= 80, "name", "Give the route a name (max 80 characters).");
        v.check(req.origin() != null && Inputs.validLatLng(req.origin().lat(), req.origin().lng()), "origin", "Choose a valid starting location.");
        v.check(req.destination() != null && Inputs.validLatLng(req.destination().lat(), req.destination().lng()), "destination", "Choose a valid destination.");
        v.throwIfInvalid();
        if (routes.countByUser_Id(user.getId()) >= MAX_PER_USER) {
            throw ApiException.conflict("You can keep up to " + MAX_PER_USER + " saved routes. Delete one to save another.");
        }

        SavedRoute r = new SavedRoute();
        r.setUser(user);
        r.setName(name);
        r.setOriginLabel(label(req.origin()));
        r.setOriginLat(req.origin().lat());
        r.setOriginLng(req.origin().lng());
        r.setDestinationLabel(label(req.destination()));
        r.setDestinationLat(req.destination().lat());
        r.setDestinationLng(req.destination().lng());

        boolean analysed = true;
        String message = null;
        try {
            apply(r, planner.analyze(user, new AnalyzeRequest(req.origin(), req.destination(), null)));
        } catch (ApiException e) {
            if (e.getStatus().is5xxServerError() || "NO_ROUTE".equals(e.getCode())) {   // save anyway; just no score yet
                analysed = false;
                message = e.getMessage();
            } else {
                throw e;
            }
        }
        return new SaveResponse(toView(routes.save(r)), analysed, message);
    }

    public SavedRouteView rename(User user, Long id, String newName) {
        String name = Inputs.clean(newName);
        if (name.isEmpty() || name.length() > 80) {
            throw new com.roadguard.exception.ValidationException(java.util.Map.of("name", "Give the route a name (max 80 characters)."));
        }
        SavedRoute r = find(user, id);
        r.setName(name);
        return toView(routes.save(r));
    }

    public void delete(User user, Long id) {
        routes.delete(find(user, id));
    }

    /** Re-scores the saved route against today's hazards and remembers the previous score for comparison. */
    public ReanalyzeResponse reanalyze(User user, Long id) {
        SavedRoute r = find(user, id);
        Integer previous = r.getLastScore();
        RouteAnalysis analysis = planner.analyze(user, new AnalyzeRequest(
                new Point(r.getOriginLat(), r.getOriginLng(), r.getOriginLabel()),
                new Point(r.getDestinationLat(), r.getDestinationLng(), r.getDestinationLabel()), null));
        apply(r, analysis);
        SavedRoute saved = routes.save(r);
        int latest = saved.getLastScore();
        return new ReanalyzeResponse(toView(saved), analysis, previous, latest, previous == null ? null : latest - previous);
    }

    // ----------------------------------------------------------------

    private SavedRoute find(User user, Long id) {
        return routes.findByIdAndUser_Id(id, user.getId()).orElseThrow(() -> ApiException.notFound("Saved route not found."));
    }

    /** Stores the numbers of the recommended (best-scoring) alternative. */
    private static void apply(SavedRoute r, RouteAnalysis analysis) {
        RouteOption best = analysis.routes().get(analysis.recommendedIndex());
        r.setPreviousScore(r.getLastScore());
        r.setLastScore(best.safety().score());
        r.setLastClassification(best.safety().classification());
        r.setLastHazardCount(best.safety().hazardCount());
        r.setLastDistanceMeters(best.distanceMeters());
        r.setLastDurationSeconds(best.durationSeconds());
        r.setLastAnalyzedAt(Instant.now());
    }

    private static String label(Point p) {
        String l = p.label() == null ? "" : Inputs.clean(p.label());
        if (l.length() > 200) l = l.substring(0, 200);
        return l.isEmpty() ? String.format(Locale.ROOT, "%.5f, %.5f", p.lat(), p.lng()) : l;
    }

    static SavedRouteView toView(SavedRoute r) {
        return new SavedRouteView(r.getId(), r.getName(), r.getOriginLabel(), r.getOriginLat(), r.getOriginLng(),
                r.getDestinationLabel(), r.getDestinationLat(), r.getDestinationLng(), r.getCreatedAt(),
                r.getLastScore(), r.getLastClassification(), r.getLastHazardCount(), r.getLastDistanceMeters(),
                r.getLastDurationSeconds(), r.getLastAnalyzedAt(), r.getPreviousScore());
    }
}
