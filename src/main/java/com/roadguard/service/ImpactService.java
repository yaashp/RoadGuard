package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.InsightDtos.Badge;
import com.roadguard.dto.InsightDtos.ImpactRules;
import com.roadguard.dto.InsightDtos.ImpactSummary;
import com.roadguard.dto.InsightDtos.Leaderboard;
import com.roadguard.dto.InsightDtos.LeaderboardRow;
import com.roadguard.dto.InsightDtos.PointEventView;
import com.roadguard.exception.ApiException;
import com.roadguard.model.Complaint;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.ComplaintStatusHistory;
import com.roadguard.model.HazardVerification;
import com.roadguard.model.RoadIssue;
import com.roadguard.model.User;
import com.roadguard.model.UserPreference;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.ComplaintStatusHistoryRepository;
import com.roadguard.repository.HazardVerificationRepository;
import com.roadguard.repository.UserPreferenceRepository;
import com.roadguard.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RoadGuard Impact Score. Nothing is stored or sent by the browser: points are derived on the server from the real records,
 * so they cannot be edited, farmed by resubmitting, or earned from sample data.
 * <ul>
 *   <li>+10 when one of your reports has been reviewed and accepted (not Rejected / Duplicate);</li>
 *   <li>+5 more when that report is Resolved;</li>
 *   <li>+2 for confirming (or disputing) someone else's real hazard, at most 5 rewarded confirmations per day.</li>
 * </ul>
 */
@Service
public class ImpactService {
    static final int VALIDATED = 10, RESOLVED = 5, CONFIRMATION = 2, MAX_CONFIRMATIONS_PER_DAY = 5;

    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final HazardVerificationRepository votes;
    private final UserRepository users;
    private final UserPreferenceRepository prefs;
    private final ZoneId zone;

    public ImpactService(ComplaintRepository complaints, ComplaintStatusHistoryRepository history, HazardVerificationRepository votes,
                         UserRepository users, UserPreferenceRepository prefs, RoadGuardProperties props) {
        this.complaints = complaints;
        this.history = history;
        this.votes = votes;
        this.users = users;
        this.prefs = prefs;
        this.zone = ZoneId.of(props.getTimeZone());
    }

    public ImpactRules rules() {
        return new ImpactRules(List.of(
                "+" + VALIDATED + " points when one of your reports is reviewed and accepted by staff. Rejected and duplicate reports earn nothing.",
                "+" + RESOLVED + " more points when that report is marked Resolved.",
                "+" + CONFIRMATION + " points for confirming or disputing someone else's hazard (\"still there\" / \"no longer there\"), up to " + MAX_CONFIRMATIONS_PER_DAY + " rewarded answers per day. You cannot confirm your own report.",
                "Submitting more reports, resubmitting the same hazard or voting repeatedly does not earn extra points.",
                "Sample (demo) records never earn points. Points are calculated on the server from real records.",
                "The monthly leaderboard is opt-in and shows only a short display name (first name and initial)."), badgeDefs(0, 0, 0, 0));
    }

    @Transactional(readOnly = true)
    public ImpactSummary me(User user) {
        List<Event> events = new ArrayList<>(computeAll().getOrDefault(user.getId(), List.of()));
        events.sort(Comparator.comparing((Event e) -> e.at).reversed());
        YearMonth now = YearMonth.now(zone);
        int total = events.stream().mapToInt(e -> e.points).sum();
        int month = events.stream().filter(e -> YearMonth.from(e.at.atZone(zone)).equals(now)).mapToInt(e -> e.points).sum();
        int validated = (int) events.stream().filter(e -> e.type.equals("REPORT_ACCEPTED")).count();
        int resolved = (int) events.stream().filter(e -> e.type.equals("REPORT_RESOLVED")).count();
        int confirmations = (int) events.stream().filter(e -> e.type.equals("CONFIRMATION")).count();
        boolean on = prefs.findByUser_Id(user.getId()).map(UserPreference::isShowOnLeaderboard).orElse(false);
        return new ImpactSummary(total, month, validated, resolved, confirmations, badgeDefs(validated, resolved, confirmations, total),
                events.stream().limit(50).map(e -> new PointEventView(e.type, e.points, e.at, e.reference, e.description)).toList(), on);
    }

    @Transactional(readOnly = true)
    public Leaderboard leaderboard(String monthText) {
        YearMonth month;
        try {
            month = monthText == null || monthText.isBlank() ? YearMonth.now(zone) : YearMonth.parse(monthText.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("month must look like 2026-10.");
        }
        Map<Long, List<Event>> all = computeAll();
        List<LeaderboardRow> rows = new ArrayList<>();
        List<Object[]> scored = new ArrayList<>();
        for (UserPreference p : prefs.findAll()) {
            if (!p.isShowOnLeaderboard()) continue;
            User u = p.getUser();
            int pts = all.getOrDefault(u.getId(), List.of()).stream().filter(e -> YearMonth.from(e.at.atZone(zone)).equals(month)).mapToInt(e -> e.points).sum();
            if (pts > 0) scored.add(new Object[]{displayName(u), pts});
        }
        scored.sort((a, b) -> ((Integer) b[1]).compareTo((Integer) a[1]));
        for (int i = 0; i < Math.min(10, scored.size()); i++) rows.add(new LeaderboardRow(i + 1, (String) scored.get(i)[0], (Integer) scored.get(i)[1]));
        return new Leaderboard(month.toString(), rows, rows.isEmpty()
                ? "Nobody has points on the leaderboard this month yet. Joining is optional: turn it on in Settings."
                : "Only people who opted in appear, with a short display name.");
    }

    // ------------------------------------------------------------------

    private record Event(String type, int points, Instant at, String reference, String description) {}

    private Map<Long, List<Event>> computeAll() {
        Map<Long, List<Event>> out = new HashMap<>();
        Map<Long, Complaint> byId = new HashMap<>();
        Map<Long, List<ComplaintStatusHistory>> changes = new HashMap<>();
        for (ComplaintStatusHistory h : history.findByEventType(ComplaintStatusHistory.STATUS_CHANGE)) {
            changes.computeIfAbsent(h.getComplaint().getId(), k -> new ArrayList<>()).add(h);
        }
        for (Complaint c : complaints.findAll()) {
            byId.put(c.getId(), c);
            if (c.sample()) continue;
            ComplaintStatus st = ComplaintStatus.fromLabel(c.getStatus()).orElse(ComplaintStatus.SUBMITTED);
            if (st == ComplaintStatus.REJECTED || st == ComplaintStatus.DUPLICATE) continue;
            List<ComplaintStatusHistory> ev = changes.getOrDefault(c.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(ComplaintStatusHistory::getCreatedAt).thenComparing(ComplaintStatusHistory::getId)).toList();
            Instant accepted = ev.stream().filter(h -> !ComplaintStatus.REJECTED.label().equals(h.getToValue()) && !ComplaintStatus.DUPLICATE.label().equals(h.getToValue()))
                    .map(ComplaintStatusHistory::getCreatedAt).findFirst().orElse(null);
            if (accepted == null) continue;                       // not reviewed yet
            Long uid = c.getUser().getId();
            out.computeIfAbsent(uid, k -> new ArrayList<>()).add(new Event("REPORT_ACCEPTED", VALIDATED, accepted, c.getComplaintId(), "Report accepted after review"));
            if (st == ComplaintStatus.RESOLVED) {
                ev.stream().filter(h -> ComplaintStatus.RESOLVED.label().equals(h.getToValue())).map(ComplaintStatusHistory::getCreatedAt).reduce((a, b) -> b)
                        .ifPresent(t -> out.get(uid).add(new Event("REPORT_RESOLVED", RESOLVED, t, c.getComplaintId(), "Your report was resolved")));
            }
        }
        // confirmations, capped per day
        Map<Long, List<HazardVerification>> byUser = new HashMap<>();
        for (HazardVerification v : votes.findAll()) byUser.computeIfAbsent(v.getUser().getId(), k -> new ArrayList<>()).add(v);
        for (Map.Entry<Long, List<HazardVerification>> e : byUser.entrySet()) {
            Map<LocalDate, Integer> perDay = new HashMap<>();
            e.getValue().sort(Comparator.comparing(HazardVerification::votedAt));
            for (HazardVerification v : e.getValue()) {
                RoadIssue pin = v.getRoadIssue();
                if (pin.sample()) continue;
                ComplaintStatus ps = ComplaintStatus.fromLabel(pin.getStatus()).orElse(ComplaintStatus.SUBMITTED);
                if (ps == ComplaintStatus.REJECTED || ps == ComplaintStatus.DUPLICATE) continue;
                Complaint c = pin.getComplaintRef() == null ? null : byId.get(pin.getComplaintRef());
                if (c != null && c.getUser().getId().equals(e.getKey())) continue;
                LocalDate day = v.votedAt().atZone(zone).toLocalDate();
                if (perDay.merge(day, 1, Integer::sum) > MAX_CONFIRMATIONS_PER_DAY) continue;
                out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Event("CONFIRMATION", CONFIRMATION, v.votedAt(),
                        c == null ? "PIN-" + pin.getId() : c.getComplaintId(), HazardVerification.STILL_THERE.equals(v.getVote()) ? "Confirmed a hazard is still there" : "Reported a hazard as gone"));
            }
        }
        return out;
    }

    private List<Badge> badgeDefs(int validated, int resolved, int confirmations, int total) {
        return List.of(
                new Badge("first-report", "First accepted report", "One of your reports was reviewed and accepted.", validated >= 1),
                new Badge("regular", "Regular reporter", "Five reports accepted.", validated >= 5),
                new Badge("road-watcher", "Road watcher", "Ten rewarded confirmations of other people's hazards.", confirmations >= 10),
                new Badge("fix-witness", "Fix witness", "A report you made was resolved.", resolved >= 1),
                new Badge("century", "Century", "100 impact points.", total >= 100));
    }

    static String displayName(User u) {
        String[] parts = u.getName().trim().split("\\s+");
        return parts.length > 1 && !parts[parts.length - 1].isEmpty() ? parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + "." : parts[0];
    }
}
