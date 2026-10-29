package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.RouteDtos;
import com.roadguard.dto.RouteDtos.AnalyzeRequest;
import com.roadguard.dto.RouteDtos.HazardPoint;
import com.roadguard.dto.RouteDtos.Point;
import com.roadguard.dto.RouteDtos.RouteAnalysis;
import com.roadguard.dto.RouteDtos.RouteOption;
import com.roadguard.dto.RouteDtos.SafetyAssessment;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.Validation;
import com.roadguard.geo.GeoUtils;
import com.roadguard.model.User;
import com.roadguard.routing.NoRouteException;
import com.roadguard.routing.RouteCandidate;
import com.roadguard.routing.RoutingProvider;
import com.roadguard.routing.RoutingUnavailableException;
import com.roadguard.security.SlidingWindowCounter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Gets real route geometry from the routing provider and scores each route against the reported hazards. */
@Service
public class RoutePlannerService {
    public static final String DISCLAIMER = "This safety score is an estimate based only on road problems reported through RoadGuard "
            + "(including any sample data, which is labelled). It does not measure road condition, traffic, lighting or personal safety "
            + "and does not guarantee that a route is safe. Crowdsourced reports can be missing, outdated or wrong, and a route with no reports may still have hazards. "
            + "Always drive according to the conditions you actually see.";

    private final RoutingProvider provider;
    private final RouteSafetyService safety;
    private final RoadIssueService roadIssues;
    private final PreferenceService preferences;
    private final RoadGuardProperties props;
    /** 30 route analyses per user per 5 minutes: protects the (shared, fair-use) routing service. */
    private final SlidingWindowCounter perUser = new SlidingWindowCounter(30, 5 * 60_000L);

    public RoutePlannerService(RoutingProvider provider, RouteSafetyService safety, RoadIssueService roadIssues,
                               PreferenceService preferences, RoadGuardProperties props) {
        this.provider = provider;
        this.safety = safety;
        this.roadIssues = roadIssues;
        this.preferences = preferences;
        this.props = props;
    }

    public RouteAnalysis analyze(User user, AnalyzeRequest req) {
        Point origin = req.origin();
        Point destination = req.destination();
        Validation v = new Validation();
        v.check(origin != null && Inputs.validLatLng(origin.lat(), origin.lng()), "origin", "Choose a valid starting location.");
        v.check(destination != null && Inputs.validLatLng(destination.lat(), destination.lng()), "destination", "Choose a valid destination.");
        v.throwIfInvalid();
        double straight = GeoUtils.haversineMeters(origin.lat(), origin.lng(), destination.lat(), destination.lng());
        if (straight < 50) throw ApiException.badRequest("The start and destination are almost the same place. Choose two different locations.");
        if (straight / 1000.0 > props.getRouting().getMaxDistanceKm()) {
            throw ApiException.badRequest("These locations are too far apart (limit " + (int) props.getRouting().getMaxDistanceKm() + " km).");
        }
        if (!perUser.tryAcquire("user:" + user.getId())) {
            throw ApiException.tooMany("You are requesting routes too quickly. Please wait a minute and try again.");
        }

        boolean includeSample = req.includeSample() != null ? req.includeSample() : preferences.effective(user).isShowSampleData();

        List<RouteCandidate> candidates;
        try {
            candidates = provider.route(origin.lat(), origin.lng(), destination.lat(), destination.lng());
        } catch (NoRouteException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_ROUTE", e.getMessage());
        } catch (RoutingUnavailableException e) {
            throw ApiException.unavailable("ROUTING_UNAVAILABLE",
                    "The routing service is not reachable right now, so routes cannot be calculated. Please try again in a moment.");
        }

        List<HazardPoint> hazards = roadIssues.openHazards(includeSample);
        List<RouteOption> options = new ArrayList<>();
        int best = 0;
        List<SafetyAssessment> assessments = new ArrayList<>();
        for (RouteCandidate c : candidates) assessments.add(safety.assess(c.geometry(), c.distanceMeters(), hazards));
        for (int i = 1; i < candidates.size(); i++) {
            int cmp = Comparator.comparingInt((Integer k) -> assessments.get(k).score())
                    .thenComparing(k -> -candidates.get(k).durationSeconds()).compare(i, best);
            if (cmp > 0) best = i;
        }
        for (int i = 0; i < candidates.size(); i++) {
            RouteCandidate c = candidates.get(i);
            options.add(new RouteOption(i, Math.round(c.distanceMeters()), Math.round(c.durationSeconds()),
                    GeoUtils.simplify(c.geometry(), 4.0), assessments.get(i), i == best));
        }
        long sample = hazards.stream().filter(HazardPoint::sample).count();
        List<String> warnings = new ArrayList<>();
        RouteOption fastest = options.get(0), chosen = options.get(best);
        String reason;
        if (options.size() == 1) {
            reason = "Only one route was available from the routing service, so no alternative could be compared.";
            warnings.add("No alternative route could be calculated. The known hazards on the available route are listed below.");
        } else if (best == 0) {
            reason = options.stream().skip(1).allMatch(o -> o.safety().score() == fastest.safety().score())
                    ? "All routes have the same estimated hazard exposure, so the fastest is suggested."
                    : "The fastest route also has the highest safety score (" + fastest.safety().score() + "/100).";
        } else {
            int fewer = fastest.safety().hazardCount() - chosen.safety().hazardCount();
            double km = (chosen.distanceMeters() - fastest.distanceMeters()) / 1000.0;
            long min = Math.round((chosen.durationSeconds() - fastest.durationSeconds()) / 60.0);
            reason = "Alternative " + (best + 1) + " scores " + chosen.safety().score() + "/100 against " + fastest.safety().score() + " for the fastest route"
                    + (fewer > 0 ? ": it passes " + fewer + " fewer reported hazard" + (fewer == 1 ? "" : "s") : ": its reported hazards are less severe, recent or confirmed")
                    + ", but it is " + String.format(Locale.ROOT, "%.1f", Math.abs(km)) + " km " + (km >= 0 ? "longer" : "shorter")
                    + " and about " + Math.abs(min) + " min " + (min >= 0 ? "slower" : "faster") + ".";
        }
        if (chosen.safety().hazardCount() > 0) {
            warnings.add("Even the suggested route passes " + chosen.safety().hazardCount() + " reported hazard(s). Reports are crowdsourced and may be incomplete.");
        }
        if (!RouteSafetyService.SAFE.equals(chosen.safety().classification())) {
            warnings.add("No available route is rated Safe (best: " + chosen.safety().classification() + ").");
        }
        String o = String.format(Locale.ROOT, "%.6f,%.6f", origin.lat(), origin.lng());
        String d = String.format(Locale.ROOT, "%.6f,%.6f", destination.lat(), destination.lng());
        RouteDtos.ExternalNavigation nav = new RouteDtos.ExternalNavigation(
                "https://www.google.com/maps/dir/?api=1&origin=" + o + "&destination=" + d + "&travelmode=driving",
                "https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=" + o + "%3B" + d,
                "These apps calculate their own route from the same start and destination. It may differ from the route shown here and does not use RoadGuard's hazard data.");
        return new RouteAnalysis(label(origin), label(destination), options, best, provider.name(), hazards.size(), (int) sample,
                includeSample, safety.model(), DISCLAIMER, reason, warnings, nav);
    }

    private static Point label(Point p) {
        String l = p.label() == null ? "" : Inputs.clean(p.label());
        if (l.length() > 200) l = l.substring(0, 200);
        if (l.isEmpty()) l = String.format(Locale.ROOT, "%.5f, %.5f", p.lat(), p.lng());
        return new Point(p.lat(), p.lng(), l);
    }
}
