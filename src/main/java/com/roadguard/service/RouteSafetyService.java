package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.RouteDtos.HazardPoint;
import com.roadguard.dto.RouteDtos.NearbyHazard;
import com.roadguard.dto.RouteDtos.SafetyAssessment;
import com.roadguard.dto.RouteDtos.ScoringModel;
import com.roadguard.geo.GeoUtils;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.Severity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Estimates how much reported road hazard a route is exposed to.
 *
 * <h3>Scoring model (all numbers configurable under roadguard.scoring.*)</h3>
 * <ol>
 *   <li>Only OPEN hazards count (status Submitted, Under Review, Assigned, In Progress).
 *       Resolved and Rejected reports are ignored.</li>
 *   <li>For every hazard we measure the shortest distance to the route line (over all route segments), so a
 *       hazard next to two overlapping segments is still measured and counted once.</li>
 *   <li>Hazards farther than {@code bufferMeters} (default 60 m) from the route are ignored.</li>
 *   <li>Reports of the same category within {@code duplicateMeters} (default 15 m) of each other are treated as
 *       one physical hazard (the most severe report is kept) so several people reporting the same pothole do not
 *       multiply the penalty.</li>
 *   <li>penalty = severityPenalty x proximityFactor x statusFactor x recencyFactor x confidenceFactor, where
 *       severityPenalty = 2 / 5 / 10 for Low / Medium / High;
 *       proximityFactor = 1.0 up to {@code nearMeters} (20 m), falling linearly to {@code edgeFactor} (0.5) at the buffer edge;
 *       statusFactor = 1.0, or {@code inProgressFactor} (0.75) when repair work is already under way;
 *       recencyFactor = 1.0 for reports up to 30 days old, fading linearly to 0.6 at 180 days (old reports may be stale);
 *       confidenceFactor = 1 + 0.1 per "still there" confirmation (max +0.3) - 0.15 per "no longer there" vote (max -0.5).</li>
 *   <li>score = 100 - sum(penalties), rounded and clamped to 0..100.</li>
 *   <li>Classification: Safe &gt;= 80, Moderate &gt;= 50, otherwise Risky (thresholds configurable).</li>
 * </ol>
 * The score is an estimate built only from reports in this system. It says nothing about road surface
 * condition, traffic, lighting or crime and is not a guarantee of safety.
 */
@Service
public class RouteSafetyService {
    public static final String SAFE = "Safe";
    public static final String MODERATE = "Moderate";
    public static final String RISKY = "Risky";

    private final RoadGuardProperties.Scoring cfg;

    public RouteSafetyService(RoadGuardProperties props) {
        this.cfg = props.getScoring();
    }

    public SafetyAssessment assess(List<double[]> route, double distanceMeters, List<HazardPoint> hazards) {
        return assess(route, distanceMeters, hazards, Instant.now());
    }

    public SafetyAssessment assess(List<double[]> route, double distanceMeters, List<HazardPoint> hazards, Instant now) {
        double buffer = cfg.getBufferMeters();
        double[] box = GeoUtils.boundingBox(route, buffer);

        // 1-3: open hazards within the buffer, each with its single shortest distance to the route
        List<Candidate> candidates = new ArrayList<>();
        for (HazardPoint h : hazards) {
            if (!isOpen(h.status())) continue;
            if (!GeoUtils.inBox(h.lat(), h.lng(), box)) continue;
            double d = GeoUtils.distanceToPolylineMeters(h.lat(), h.lng(), route);
            if (d <= buffer) candidates.add(new Candidate(h, d));
        }

        // 4: merge duplicate reports (most severe first, then closest to the route)
        candidates.sort(Comparator.<Candidate>comparingInt(c -> -severityRank(c.hazard.severity()))
                .thenComparingDouble(c -> c.distance)
                .thenComparing(c -> c.hazard.id() == null ? 0L : c.hazard.id()));
        List<Candidate> kept = new ArrayList<>();
        int merged = 0;
        for (Candidate c : candidates) {
            Candidate twin = null;
            for (Candidate k : kept) {
                if (k.hazard.category().equalsIgnoreCase(c.hazard.category())
                        && GeoUtils.haversineMeters(k.hazard.lat(), k.hazard.lng(), c.hazard.lat(), c.hazard.lng()) <= cfg.getDuplicateMeters()) {
                    twin = k;
                    break;
                }
            }
            if (twin != null) {
                twin.merged++;
                merged++;
            } else {
                kept.add(c);
            }
        }

        // 5: penalties
        List<NearbyHazard> nearby = new ArrayList<>();
        double total = 0;
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        for (Severity s : new Severity[]{Severity.HIGH, Severity.MEDIUM, Severity.LOW}) bySeverity.put(s.label(), 0);
        Map<String, Double> penaltyBySeverity = new LinkedHashMap<>();
        for (Candidate c : kept) {
            double recency = recencyFactor(c.hazard.createdAt(), now);
            double confidence = confidenceFactor(c.hazard.stillThere(), c.hazard.gone());
            double penalty = round2(severityPenalty(c.hazard.severity()) * proximityFactor(c.distance) * statusFactor(c.hazard.status()) * recency * confidence);
            total += penalty;
            String sev = severityLabel(c.hazard.severity());
            bySeverity.merge(sev, 1, Integer::sum);
            penaltyBySeverity.merge(sev, penalty, Double::sum);
            nearby.add(new NearbyHazard(c.hazard.id(), c.hazard.category(), sev, c.hazard.status(),
                    c.hazard.lat(), c.hazard.lng(), c.hazard.roadName(), round1(c.distance), penalty, 1 + c.merged, c.hazard.sample(),
                    ageDays(c.hazard.createdAt(), now), c.hazard.stillThere(), c.hazard.gone(), round2(recency), round2(confidence)));
        }
        nearby.sort(Comparator.comparingDouble(NearbyHazard::penalty).reversed().thenComparingDouble(NearbyHazard::distanceMeters));
        total = round2(total);

        // 6-7: score and class
        int score = (int) Math.round(Math.max(0, Math.min(100, 100 - total)));
        String classification = classify(score);
        double km = Math.max(distanceMeters / 1000.0, 0.001);
        double exposurePerKm = round2(total / km);

        List<String> explanation = explain(score, classification, total, nearby, bySeverity, penaltyBySeverity, merged);
        return new SafetyAssessment(score, classification, total, nearby.size(), merged, exposurePerKm, bySeverity, nearby, explanation);
    }

    public String classify(int score) {
        if (score >= cfg.getSafeMinScore()) return SAFE;
        if (score >= cfg.getModerateMinScore()) return MODERATE;
        return RISKY;
    }

    public ScoringModel model() {
        return new ScoringModel(cfg.getLowPenalty(), cfg.getMediumPenalty(), cfg.getHighPenalty(),
                cfg.getNearMeters(), cfg.getBufferMeters(), cfg.getEdgeFactor(), cfg.getInProgressFactor(),
                cfg.getDuplicateMeters(), cfg.getSafeMinScore(), cfg.getModerateMinScore(),
                "Score = 100 minus the penalties of distinct open reported hazards within " + fmt(cfg.getBufferMeters())
                        + " m of the route. Penalty = severity (Low " + fmt(cfg.getLowPenalty()) + ", Medium " + fmt(cfg.getMediumPenalty())
                        + ", High " + fmt(cfg.getHighPenalty()) + ") x proximity (full weight within " + fmt(cfg.getNearMeters())
                        + " m, falling to " + fmt(cfg.getEdgeFactor()) + " at the edge) x status (In Progress counts "
                        + fmt(cfg.getInProgressFactor()) + "). Safe >= " + cfg.getSafeMinScore() + ", Moderate >= " + cfg.getModerateMinScore()
                        + ", otherwise Risky. Penalties are also scaled by report recency (older reports count less) and community confirmations.");
    }

    // ---- building blocks (package-private for unit tests) ----

    double severityPenalty(String severity) {
        return switch (Severity.fromLabel(severity).orElse(Severity.MEDIUM)) {
            case LOW -> cfg.getLowPenalty();
            case MEDIUM -> cfg.getMediumPenalty();
            case HIGH -> cfg.getHighPenalty();
        };
    }

    double proximityFactor(double distanceMeters) {
        double near = cfg.getNearMeters(), buffer = cfg.getBufferMeters();
        if (distanceMeters <= near) return 1.0;
        if (distanceMeters >= buffer) return cfg.getEdgeFactor();
        double t = (distanceMeters - near) / (buffer - near);
        return 1.0 - t * (1.0 - cfg.getEdgeFactor());
    }

    double recencyFactor(Instant createdAt, Instant now) {
        if (createdAt == null) return 1.0;
        double days = Duration.between(createdAt, now).toHours() / 24.0;
        if (days <= cfg.getRecencyFullDays()) return 1.0;
        if (days >= cfg.getRecencyMinDays()) return cfg.getRecencyMinFactor();
        double t = (days - cfg.getRecencyFullDays()) / (cfg.getRecencyMinDays() - cfg.getRecencyFullDays());
        return 1.0 - t * (1.0 - cfg.getRecencyMinFactor());
    }

    double confidenceFactor(int stillThere, int gone) {
        double up = Math.min(stillThere * cfg.getConfirmBonusPerVote(), cfg.getConfirmMaxBonus());
        double down = Math.min(gone * cfg.getGoneMalusPerVote(), cfg.getGoneMaxMalus());
        return 1.0 + up - down;
    }

    private static int ageDays(Instant createdAt, Instant now) {
        return createdAt == null ? 0 : (int) Math.max(0, Duration.between(createdAt, now).toDays());
    }

    double statusFactor(String status) {
        return ComplaintStatus.fromLabel(status).orElse(ComplaintStatus.SUBMITTED) == ComplaintStatus.IN_PROGRESS
                ? cfg.getInProgressFactor() : 1.0;
    }

    private static boolean isOpen(String status) {
        return ComplaintStatus.fromLabel(status).map(ComplaintStatus::isOpen).orElse(true);
    }

    private static int severityRank(String severity) {
        return Severity.fromLabel(severity).orElse(Severity.MEDIUM).rank();
    }

    private static String severityLabel(String severity) {
        return Severity.fromLabel(severity).orElse(Severity.MEDIUM).label();
    }

    private List<String> explain(int score, String classification, double total, List<NearbyHazard> nearby,
                                 Map<String, Integer> bySeverity, Map<String, Double> penaltyBySeverity, int merged) {
        List<String> lines = new ArrayList<>();
        lines.add("The score starts at 100.");
        if (nearby.isEmpty()) {
            lines.add("No open reported hazards were found within " + fmt(cfg.getBufferMeters())
                    + " m of this route, so no points were deducted.");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(nearby.size()).append(nearby.size() == 1 ? " distinct open hazard lies" : " distinct open hazards lie")
                    .append(" within ").append(fmt(cfg.getBufferMeters())).append(" m of the route: ");
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : bySeverity.entrySet()) {
                if (e.getValue() > 0) {
                    parts.add(e.getValue() + " " + e.getKey() + " (-" + fmt(penaltyBySeverity.getOrDefault(e.getKey(), 0.0)) + ")");
                }
            }
            sb.append(String.join(", ", parts)).append(". Total deduction: ").append(fmt(total)).append(".");
            lines.add(sb.toString());
            NearbyHazard top = nearby.get(0);
            String where = top.roadName() == null || top.roadName().isBlank() ? "" : " on " + top.roadName();
            lines.add("Largest single deduction: " + top.category() + where + " (" + top.severity() + ", "
                    + fmt(top.distanceMeters()) + " m from the route): -" + fmt(top.penalty()) + ". Reported " + top.ageDays() + " day(s) ago with "
                    + top.stillThere() + " 'still there' and " + top.gone() + " 'no longer there' confirmation(s).");
        }
        if (merged > 0) {
            lines.add(merged + (merged == 1 ? " additional report was" : " additional reports were")
                    + " merged into nearby reports of the same hazard so each hazard is counted once.");
        }
        lines.add("Each penalty is also scaled by how recent the report is (older reports count less, never below " + fmt(cfg.getRecencyMinFactor())
                + ") and by community confirmations (more 'still there' votes raise it, 'no longer there' votes lower it).");
        lines.add("Resolved, rejected and duplicate reports, and hazards farther than " + fmt(cfg.getBufferMeters()) + " m, are ignored.");
        lines.add("Result: " + score + "/100 = " + classification + " (Safe >= " + cfg.getSafeMinScore()
                + ", Moderate >= " + cfg.getModerateMinScore() + ", otherwise Risky).");
        return lines;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.format(Locale.ROOT, "%d", (long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static final class Candidate {
        final HazardPoint hazard;
        final double distance;
        int merged = 0;

        Candidate(HazardPoint hazard, double distance) {
            this.hazard = hazard;
            this.distance = distance;
        }
    }
}
