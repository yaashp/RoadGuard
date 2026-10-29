package com.roadguard.config;

import com.roadguard.model.Complaint;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.ComplaintStatusHistory;
import com.roadguard.model.DataOrigin;
import com.roadguard.model.IssueCategory;
import com.roadguard.model.RoadIssue;
import com.roadguard.model.User;
import com.roadguard.repository.ComplaintRepository;
import com.roadguard.repository.ComplaintStatusHistoryRepository;
import com.roadguard.repository.RoadIssueRepository;
import com.roadguard.repository.UserRepository;
import com.roadguard.exception.Validation;
import com.roadguard.service.Inputs;
import com.roadguard.service.RoadIssueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs on every start and is safe to run repeatedly (it never inserts the same record twice).
 * <ol>
 *   <li>Upgrades rows that were written before this version: labels demo rows as SAMPLE, adds a first history
 *       entry for complaints that have none, and creates map pins for real complaints that had none.</li>
 *   <li>If roadguard.seed.enabled: creates the demo accounts and a fixed set of sample complaints with realistic
 *       status histories. Every sample record is flagged SAMPLE in the database and shown as sample data in the UI.</li>
 * </ol>
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final List<String> LEGACY_DEMO_COMPLAINTS = List.of("RG-2026-00417", "RG-2026-00398", "RG-2026-00405");

    private final UserRepository users;
    private final ComplaintRepository complaints;
    private final ComplaintStatusHistoryRepository history;
    private final RoadIssueRepository issues;
    private final RoadIssueService roadIssueService;
    private final PasswordEncoder encoder;
    private final RoadGuardProperties props;

    @PersistenceContext
    private EntityManager em;

    public DataSeeder(UserRepository users, ComplaintRepository complaints, ComplaintStatusHistoryRepository history,
                      RoadIssueRepository issues, RoadIssueService roadIssueService, PasswordEncoder encoder, RoadGuardProperties props) {
        this.users = users;
        this.complaints = complaints;
        this.history = history;
        this.issues = issues;
        this.roadIssueService = roadIssueService;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        RoadGuardProperties.Seed seed = props.getSeed();
        User demo = null;
        User admin = null;
        if (seed.isEnabled()) {
            demo = ensureUser("Aarav Sharma", seed.getDemoUserEmail(), "9876543210", seed.getDemoUserPassword(), User.ROLE_USER);
            admin = ensureUser("RoadGuard Admin", seed.getAdminEmail(), "9999999999", seed.getAdminPassword(), User.ROLE_ADMIN);
        }
        bootstrapAdmin();
        renameLegacyStatuses();
        upgradeLegacyRows(seed.getDemoUserEmail());
        if (seed.isEnabled()) {
            seedLegacyPinsIfEmpty();
            seedSampleComplaints(demo, admin);
        }
    }

    // ------------------------------------------------------------------ first administrator for real deployments

    private void bootstrapAdmin() {
        RoadGuardProperties.BootstrapAdmin b = props.getBootstrapAdmin();
        if (b.getEmail().isEmpty() && b.getPassword().isEmpty()) return;          // not configured: nothing to do
        if (b.getEmail().isEmpty() || b.getPassword().isEmpty()) {
            log.warn("BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD must both be set; no administrator was created");
            return;
        }
        Validation v = new Validation();
        v.check(Inputs.validEmail(b.getEmail()), "email", "not a valid e-mail address");
        Inputs.checkPassword(v, "password", b.getPassword());
        if (v.hasErrors()) {
            log.warn("Bootstrap administrator not created: the e-mail must be valid and the password 8-64 characters with a letter and a number");
            return;
        }
        if (users.existsByEmailIgnoreCase(b.getEmail())) {
            log.info("Bootstrap administrator {} already exists; left unchanged", b.getEmail());
            return;
        }
        users.save(new User("Administrator", b.getEmail(), "", encoder.encode(b.getPassword()), User.ROLE_ADMIN));
        log.info("Created administrator account {}", b.getEmail());
    }

    // ------------------------------------------------------------------ lifecycle rename

    /** "Assigned" became "Action Planned" and "In Progress" became "Repair in Progress". Existing rows are renamed once; safe to repeat. */
    private void renameLegacyStatuses() {
        String[][] renames = {{"Assigned", ComplaintStatus.ASSIGNED.label()}, {"In Progress", ComplaintStatus.IN_PROGRESS.label()}};
        int changed = 0;
        for (String[] r : renames) {
            changed += em.createQuery("update Complaint c set c.status = :n where c.status = :o").setParameter("n", r[1]).setParameter("o", r[0]).executeUpdate();
            changed += em.createQuery("update RoadIssue i set i.status = :n where i.status = :o").setParameter("n", r[1]).setParameter("o", r[0]).executeUpdate();
            changed += em.createQuery("update ComplaintStatusHistory h set h.toValue = :n where h.toValue = :o and h.eventType <> 'SEVERITY_CHANGE'").setParameter("n", r[1]).setParameter("o", r[0]).executeUpdate();
            changed += em.createQuery("update ComplaintStatusHistory h set h.fromValue = :n where h.fromValue = :o and h.eventType <> 'SEVERITY_CHANGE'").setParameter("n", r[1]).setParameter("o", r[0]).executeUpdate();
        }
        if (changed > 0) log.info("Renamed {} stored status values to the new lifecycle labels", changed);
        em.clear();
    }

    // ------------------------------------------------------------------ upgrade of older databases

    private void upgradeLegacyRows(String demoEmail) {
        int pins = 0, labelled = 0, histories = 0, linked = 0;
        for (RoadIssue i : issues.findAll()) {
            boolean changed = false;
            if (i.getSource() == null || i.getSource().isBlank()) {
                // before this version only the seeder created pins, so any pin without a source is demo data
                i.setSource(i.getComplaintRef() == null ? RoadIssue.SOURCE_SAMPLE : RoadIssue.SOURCE_REPORTED);
                changed = true;
            }
            String canonical = IssueCategory.fromLabel(i.getType()).map(IssueCategory::label).orElse(IssueCategory.OTHER.label());
            if (!canonical.equals(i.getType())) {
                i.setType(canonical);
                changed = true;
            }
            if (changed) {
                issues.save(i);
                pins++;
            }
        }
        for (Complaint c : complaints.findAll()) {
            if (c.getDataOrigin() == null || c.getDataOrigin().isBlank()) {
                boolean demoRow = LEGACY_DEMO_COMPLAINTS.contains(c.getComplaintId())
                        || (c.getUser() != null && demoEmail.equalsIgnoreCase(c.getUser().getEmail()));
                c.setDataOrigin(demoRow ? DataOrigin.SAMPLE.name() : DataOrigin.USER.name());
                complaints.save(c);
                labelled++;
            }
            if (!history.existsByComplaint_Id(c.getId())) {
                history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.IMPORTED, null,
                        currentStatus(c), "Imported from records created before status history was tracked.", null, c.getCreatedAt()));
                histories++;
            }
            if (!c.sample() && issues.findFirstByComplaintRef(c.getId()).isEmpty()) {
                roadIssueService.syncFromComplaint(c);
                linked++;
            }
        }
        if (pins + labelled + histories + linked > 0) {
            log.info("Upgraded existing data: {} pins normalised, {} complaints labelled, {} history entries added, {} map pins created",
                    pins, labelled, histories, linked);
        }
    }

    private static String currentStatus(Complaint c) {
        return ComplaintStatus.fromLabel(c.getStatus()).map(ComplaintStatus::label).orElse(c.getStatus());
    }

    // ------------------------------------------------------------------ demo data

    private User ensureUser(String name, String email, String phone, String password, String role) {
        return users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(new User(name, email, phone, encoder.encode(password), role)));
    }

    /** The 12 demo map pins that shipped with the original project (only on an empty table). */
    private void seedLegacyPinsIfEmpty() {
        if (issues.count() > 0) return;
        Object[][] pins = {
                {"Pothole", 19.0760, 72.8777, "Marine Drive, Mumbai", "High", "Under Review", 17},
                {"Pothole", 19.0330, 73.0297, "Palm Beach Road, Navi Mumbai", "Medium", "Action Planned", 9},
                {"Pothole", 19.2183, 72.9781, "Ghodbunder Road, Thane", "Low", "Submitted", 3},
                {"Accident", 19.0896, 72.8656, "Western Express Highway, Mumbai", "High", "Repair in Progress", 6},
                {"Accident", 19.0176, 73.0169, "Sion-Panvel Highway, Navi Mumbai", "Medium", "Under Review", 4},
                {"Road Construction", 19.0473, 72.9159, "Eastern Express Highway, Mumbai", "Medium", "Repair in Progress", 2},
                {"Road Construction", 19.1943, 72.9634, "LBS Marg, Thane", "Low", "Submitted", 1},
                {"Traffic Hazard", 19.1090, 72.8767, "S.V. Road, Mumbai", "High", "Under Review", 12},
                {"Traffic Hazard", 19.0410, 73.0080, "Vashi Bridge, Navi Mumbai", "Medium", "Action Planned", 8},
                {"Other", 19.1663, 72.9950, "Pokhran Road, Thane", "Low", "Resolved", 5},
                {"Other", 19.0522, 72.8300, "Linking Road, Mumbai", "Medium", "Resolved", 11},
                {"Pothole", 19.2403, 72.9784, "Majiwada, Thane", "High", "Under Review", 14}};
        for (Object[] p : pins) {
            RoadIssue pin = new RoadIssue((String) p[0], (Double) p[1], (Double) p[2], (String) p[3], (String) p[4], (String) p[5], (Integer) p[6]);
            pin.setSource(RoadIssue.SOURCE_SAMPLE);
            issues.save(pin);
        }
    }

    // id | road | lat | lng | category | severity | days ago | final status | description
    private static final String[][] SAMPLE = {
            {"RG-2026-00417", "Marine Drive, Mumbai", "19.0760", "72.8777", "Pothole", "High", "9", "Repair in Progress", "Large pothole near the signal, hard to spot at night."},
            {"RG-2026-00398", "Palm Beach Road, Navi Mumbai", "19.0330", "73.0297", "Waterlogging", "Medium", "25", "Resolved", "Water accumulates fully after rain, blocks one lane."},
            {"RG-2026-00405", "LBS Marg, Thane", "19.1943", "72.9634", "Road Damage", "Low", "4", "Under Review", "Cracked road surface across two lanes."},
            {"RG-S-0001", "Linking Road, Mumbai", "19.0561", "72.8302", "Pothole", "High", "84", "Resolved", "Deep pothole outside the shopping arcade, two-wheelers swerve to avoid it."},
            {"RG-S-0002", "Linking Road, Mumbai", "19.0574", "72.8299", "Pothole", "Medium", "47", "Resolved", "New pothole forming near the bus stop after the last repair."},
            {"RG-S-0003", "Linking Road, Mumbai", "19.0588", "72.8296", "Broken Streetlight", "Medium", "19", "Action Planned", "Three streetlights out in a row, stretch is very dark after 9 pm."},
            {"RG-S-0004", "S.V. Road, Mumbai", "19.1086", "72.8768", "Traffic Hazard", "High", "62", "Resolved", "Unmarked speed breaker causing sudden braking."},
            {"RG-S-0005", "S.V. Road, Mumbai", "19.1093", "72.8766", "Pothole", "High", "21", "Repair in Progress", "Cluster of potholes in the left lane before the junction."},
            {"RG-S-0006", "S.V. Road, Mumbai", "19.1101", "72.8770", "Damaged Traffic Signal", "High", "6", "Under Review", "Signal stuck on red for one direction, traffic backing up."},
            {"RG-S-0007", "Western Express Highway, Mumbai", "19.0898", "72.8655", "Accident", "High", "55", "Resolved", "Frequent collisions at this merge point; no warning signage."},
            {"RG-S-0008", "Western Express Highway, Mumbai", "19.0891", "72.8658", "Road Damage", "Medium", "33", "Resolved", "Edge of the carriageway has crumbled away."},
            {"RG-S-0009", "Western Express Highway, Mumbai", "19.0902", "72.8652", "Waterlogging", "Medium", "12", "Under Review", "Water collects under the flyover even after light rain."},
            {"RG-S-0010", "Eastern Express Highway, Mumbai", "19.0476", "72.9161", "Road Construction", "Medium", "40", "Repair in Progress", "Lane closure with no diversion boards."},
            {"RG-S-0011", "Eastern Express Highway, Mumbai", "19.0470", "72.9156", "Fallen Tree / Obstruction", "High", "2", "Submitted", "Large branch blocking the left lane."},
            {"RG-S-0012", "Eastern Express Highway, Mumbai", "19.0481", "72.9163", "Pothole", "Low", "71", "Resolved", "Small pothole at the service-road entry."},
            {"RG-S-0013", "Ghodbunder Road, Thane", "19.2181", "72.9779", "Pothole", "Medium", "28", "Action Planned", "Potholes along the right lane near the petrol pump."},
            {"RG-S-0014", "Ghodbunder Road, Thane", "19.2188", "72.9784", "Accident", "High", "15", "Under Review", "Two-wheeler skid spot after oil leak; several falls reported."},
            {"RG-S-0015", "Palm Beach Road, Navi Mumbai", "19.0334", "73.0291", "Waterlogging", "High", "58", "Resolved", "Road under water for hours during monsoon."},
            {"RG-S-0016", "Palm Beach Road, Navi Mumbai", "19.0326", "73.0301", "Broken Streetlight", "Low", "9", "Submitted", "Streetlight flickering near the junction."},
            {"RG-S-0017", "Sion-Panvel Highway, Navi Mumbai", "19.0178", "73.0166", "Accident", "Medium", "36", "Rejected", "Reported as an accident spot, but could not be verified at the location."},
            {"RG-S-0018", "Sion-Panvel Highway, Navi Mumbai", "19.0172", "73.0172", "Road Damage", "High", "17", "Repair in Progress", "Large crack across the lane, bikes losing balance."},
            {"RG-S-0019", "Andheri-Kurla Road, Mumbai", "19.1138", "72.8695", "Pothole", "High", "10", "Under Review", "Deep pothole filled with water, invisible in rain."},
            {"RG-S-0020", "Andheri-Kurla Road, Mumbai", "19.1131", "72.8702", "Traffic Hazard", "Medium", "5", "Submitted", "Vendors' carts spilling onto the carriageway."},
            {"RG-S-0021", "Kurla Station Road, Mumbai", "19.0730", "72.8824", "Waterlogging", "Medium", "30", "Resolved", "Drain blocked; water stands after every shower."},
            {"RG-S-0022", "Mahim Causeway, Mumbai", "19.0411", "72.8402", "Pothole", "Medium", "3", "Submitted", "Patchwork repair already breaking up."},
            {"RG-S-0023", "Mahim Causeway, Mumbai", "19.0406", "72.8407", "Damaged Traffic Signal", "Low", "75", "Resolved", "Pedestrian signal not working."},
    };

    private static final List<ComplaintStatus> PATH = List.of(ComplaintStatus.UNDER_REVIEW, ComplaintStatus.ASSIGNED,
            ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED);

    private void seedSampleComplaints(User demo, User admin) {
        int created = 0;
        Instant now = Instant.now();
        for (String[] row : SAMPLE) {
            if (complaints.findByComplaintId(row[0]).isPresent()) continue;   // already seeded: never duplicate
            Instant createdAt = now.minus(Long.parseLong(row[6]), ChronoUnit.DAYS).minus(3, ChronoUnit.HOURS);
            ComplaintStatus target = ComplaintStatus.fromLabel(row[7]).orElseThrow();

            Complaint c = new Complaint();
            c.setUser(demo);
            c.setComplaintId(row[0]);
            c.setIssueType(IssueCategory.fromLabel(row[4]).orElseThrow().label());
            c.setDescription(row[8]);
            c.setLat(Double.parseDouble(row[2]));
            c.setLng(Double.parseDouble(row[3]));
            c.setAddress(row[1]);
            c.setRoadName(row[1].split(",")[0].trim());
            c.setSeverity(row[5]);
            c.setStatus(target.label());
            c.setCreatedAt(createdAt);
            c.setUpdatedAt(createdAt);
            c.setDataOrigin(DataOrigin.SAMPLE.name());
            c = complaints.save(c);

            history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.SUBMITTED, null, ComplaintStatus.SUBMITTED.label(),
                    "Complaint submitted.", demo, createdAt));
            List<ComplaintStatus> steps = new ArrayList<>();
            if (target == ComplaintStatus.REJECTED) {
                steps.add(ComplaintStatus.UNDER_REVIEW);
                steps.add(ComplaintStatus.REJECTED);
            } else if (target != ComplaintStatus.SUBMITTED) {
                for (ComplaintStatus s : PATH) {
                    steps.add(s);
                    if (s == target) break;
                }
            }
            ComplaintStatus previous = ComplaintStatus.SUBMITTED;
            Duration age = Duration.between(createdAt, now);
            Instant last = createdAt;
            for (int k = 0; k < steps.size(); k++) {
                // each step happens at most 3 days after the previous one, and never in the future
                Instant at = createdAt.plus(minDuration(age.multipliedBy(k + 1).dividedBy(steps.size() + 1), Duration.ofDays(3L * (k + 1))));
                if (!at.isAfter(last)) at = last.plus(1, ChronoUnit.HOURS);
                history.save(new ComplaintStatusHistory(c, ComplaintStatusHistory.STATUS_CHANGE, previous.label(), steps.get(k).label(),
                        steps.get(k) == ComplaintStatus.REJECTED ? "Could not be verified at the reported location." : null, admin, at));
                previous = steps.get(k);
                last = at;
            }
            c.setUpdatedAt(last);
            complaints.save(c);
            roadIssueService.syncFromComplaint(c);
            created++;
        }
        if (created > 0) log.info("Seeded {} sample complaints (flagged SAMPLE)", created);
    }

    private static Duration minDuration(Duration a, Duration b) { return a.compareTo(b) <= 0 ? a : b; }
}
