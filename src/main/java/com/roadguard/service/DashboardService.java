package com.roadguard.service;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.AdminDtos.Overview;
import com.roadguard.dto.ComplaintDtos.ComplaintView;
import com.roadguard.dto.DashboardDtos.ActivityItem;
import com.roadguard.dto.DashboardDtos.ConditionStats;
import com.roadguard.dto.DashboardDtos.MyDashboard;
import com.roadguard.dto.DashboardDtos.RecentReport;
import com.roadguard.dto.DashboardDtos.RoadCount;
import com.roadguard.dto.DashboardDtos.Summary;
import com.roadguard.dto.DashboardDtos.Totals;
import com.roadguard.geo.RoadKeys;
import com.roadguard.model.Complaint;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.ComplaintStatusHistory;
import com.roadguard.model.RoadIssue;
import com.roadguard.model.User;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.ComplaintStatusHistoryRepository;
import com.roadguard.repository.RoadIssueRepository;
import com.roadguard.repository.SavedRouteRepository;
import com.roadguard.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Dashboard numbers. Everything here is computed from rows in the database at request time. */
@Service
public class DashboardService {
    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final RoadIssueRepository roadIssues;
    private final UserRepository users;
    private final SavedRouteRepository savedRoutes;
    private final NotificationService notifications;
    private final ComplaintService complaintService;
    private final ZoneId zone;

    public DashboardService(ComplaintRepository complaints, ComplaintStatusHistoryRepository history,
                            RoadIssueRepository roadIssues, UserRepository users, SavedRouteRepository savedRoutes,
                            NotificationService notifications, ComplaintService complaintService, RoadGuardProperties props) {
        this.complaints = complaints;
        this.history = history;
        this.roadIssues = roadIssues;
        this.users = users;
        this.savedRoutes = savedRoutes;
        this.notifications = notifications;
        this.complaintService = complaintService;
        this.zone = ZoneId.of(props.getTimeZone());
    }

    /**
     * Dashboard figures. ownerId == null: the whole system (admin view). ownerId set: only that user's
     * complaints, their map pins and their history (user view).
     */
    @Transactional(readOnly = true)
    public Summary summary(boolean includeSample, Long ownerId) {
        Instant now = Instant.now();
        List<Complaint> all = complaints.findAll().stream()
                .filter(c -> ownerId == null || owns(c, ownerId)).toList();
        Set<Long> scopeIds = all.stream().map(Complaint::getId).collect(Collectors.toSet());
        long sampleCount = all.stream().filter(Complaint::sample).count();
        List<Complaint> list = all.stream().filter(c -> includeSample || !c.sample()).toList();
        List<RoadIssue> pins = roadIssues.findAll().stream()
                .filter(i -> ownerId == null || (i.getComplaintRef() != null && scopeIds.contains(i.getComplaintRef())))
                .filter(i -> includeSample || !i.sample())
                .filter(i -> !isRejected(i.getStatus())).toList();

        List<ComplaintStatusHistory> events = history.findByEventType(ComplaintStatusHistory.STATUS_CHANGE).stream()
                .filter(e -> ownerId == null || (e.getComplaint() != null && scopeIds.contains(e.getComplaint().getId())))
                .toList();
        Map<Long, Instant> resolvedAt = Analytics.resolvedAt(events);

        long open = list.stream().filter(Analytics::isOpen).count();
        long underReview = list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.UNDER_REVIEW)).count();
        long resolved = list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.RESOLVED)).count();
        long rejected = list.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.REJECTED)).count();
        long highOpen = pins.stream().filter(i -> ComplaintStatus.fromLabel(i.getStatus()).map(ComplaintStatus::isOpen).orElse(true))
                .filter(i -> "High".equalsIgnoreCase(i.getSeverity())).count();
        Totals totals = new Totals(pins.size(), list.size(), open, underReview, resolved, rejected, highOpen);

        List<RecentReport> recent = list.stream()
                .sorted(Comparator.comparing(Complaint::getCreatedAt).reversed())
                .limit(8)
                .map(c -> new RecentReport(c.getComplaintId(), Analytics.category(c), Analytics.roadLabel(c),
                        Analytics.severity(c), Analytics.status(c), c.sample(), c.getCreatedAt()))
                .toList();

        List<ActivityItem> activity = new ArrayList<>();
        List<ComplaintStatusHistory> feed = ownerId == null
                ? history.findTop15ByOrderByCreatedAtDescIdDesc()
                : history.findTop15ByComplaint_User_IdOrderByCreatedAtDescIdDesc(ownerId);
        for (ComplaintStatusHistory h : feed) {
            Complaint c = h.getComplaint();
            if (!includeSample && c.sample()) continue;
            activity.add(new ActivityItem(c.getComplaintId(), h.getEventType(), describe(h), c.sample(), h.getCreatedAt()));
            if (activity.size() == 10) break;
        }

        ConditionStats conditions = new ConditionStats(Analytics.resolutionRate(resolved, open),
                Analytics.avgDaysToResolve(list, resolvedAt),
                list.stream().filter(c -> resolvedAt.containsKey(c.getId()) && Analytics.isStatus(c, ComplaintStatus.RESOLVED)).count(),
                roadsNeedingAttention(list, 5));

        return new Summary(totals, Analytics.statusCounts(list), Analytics.countBy(list, Analytics::category),
                Analytics.severityCounts(list), Analytics.weekly(list, resolvedAt, now, zone, 8),
                Analytics.monthly(list, resolvedAt, now, zone, 6), Analytics.severityMonthly(list, now, zone, 6),
                recent, activity, conditions, includeSample, sampleCount, all.size() - sampleCount, now);
    }

    @Transactional(readOnly = true)
    public MyDashboard mine(User user) {
        List<Complaint> mine = complaints.findByUserOrderByCreatedAtDesc(user);
        long open = mine.stream().filter(Analytics::isOpen).count();
        long resolved = mine.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.RESOLVED)).count();
        long rejected = mine.stream().filter(c -> Analytics.isStatus(c, ComplaintStatus.REJECTED)).count();
        List<ComplaintView> recent = mine.stream().limit(5).map(c -> complaintService.toView(c, false)).toList();
        return new MyDashboard(mine.size(), open, resolved, rejected, Analytics.statusCounts(mine), recent,
                notifications.unreadCount(user), savedRoutes.countByUser_Id(user.getId()));
    }

    @Transactional(readOnly = true)
    public Overview adminOverview(boolean includeSample) {
        long awaiting = complaints.countByStatus(ComplaintStatus.SUBMITTED.label());
        Map<String, List<String>> transitions = new HashMap<>();
        for (ComplaintStatus s : ComplaintStatus.values()) {
            transitions.put(s.label(), s.allowedNext().stream().map(ComplaintStatus::label).toList());
        }
        return new Overview(summary(includeSample, null), users.count(), awaiting, transitions);
    }

    private List<RoadCount> roadsNeedingAttention(List<Complaint> list, int limit) {
        Map<String, long[]> byRoad = new HashMap<>();      // key -> {open, high, total}
        Map<String, String> labels = new HashMap<>();
        for (Complaint c : list) {
            String label = Analytics.roadLabel(c);
            String key = RoadKeys.key(label);
            labels.putIfAbsent(key, label);
            long[] v = byRoad.computeIfAbsent(key, k -> new long[3]);
            v[2]++;
            if (Analytics.isOpen(c)) {
                v[0]++;
                if (Analytics.isHigh(c)) v[1]++;
            }
        }
        return byRoad.entrySet().stream()
                .filter(e -> e.getValue()[0] > 0)
                .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(e -> -e.getValue()[1])
                        .thenComparingLong(e -> -e.getValue()[0]).thenComparing(e -> labels.get(e.getKey())))
                .limit(limit)
                .map(e -> new RoadCount(labels.get(e.getKey()), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    private static String describe(ComplaintStatusHistory h) {
        return switch (h.getEventType()) {
            case ComplaintStatusHistory.SUBMITTED -> "New complaint submitted";
            case ComplaintStatusHistory.SEVERITY_CHANGE -> "Severity changed from " + h.getFromValue() + " to " + h.getToValue();
            case ComplaintStatusHistory.IMPORTED -> "Record imported with status " + h.getToValue();
            default -> "Status changed from " + h.getFromValue() + " to " + h.getToValue();
        };
    }

    private static boolean owns(Complaint c, Long userId) {
        return c.getUser() != null && userId.equals(c.getUser().getId());
    }

    private static boolean isRejected(String status) {
        return ComplaintStatus.fromLabel(status).filter(s -> s == ComplaintStatus.REJECTED || s == ComplaintStatus.DUPLICATE).isPresent();
    }
}
