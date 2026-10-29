package com.roadguard.service;

import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.HazardVerification;
import com.roadguard.model.RoadIssue;

import java.time.Instant;
import java.util.Collection;

/**
 * Community flags for human review. A flag only says "several people disagree with the current official picture";
 * it never changes a status by itself.
 * <ul><li>POSSIBLY_GONE: an open hazard that at least `threshold` people say is no longer there (and more say gone than still there).</li>
 *     <li>REPORTED_STILL_PRESENT: a hazard marked Resolved that at least `threshold` people say is still there since it was resolved.</li></ul>
 * Votes older than an administrator's last "dismiss flag" decision are ignored.
 */
final class HazardFlags {
    static final String POSSIBLY_GONE = "POSSIBLY_GONE";
    static final String STILL_PRESENT = "REPORTED_STILL_PRESENT";

    private HazardFlags() {}

    static String of(RoadIssue pin, Collection<HazardVerification> votes, int threshold) {
        ComplaintStatus st = ComplaintStatus.fromLabel(pin.getStatus()).orElse(ComplaintStatus.SUBMITTED);
        Instant since = pin.getFlagClearedAt();
        if (st == ComplaintStatus.RESOLVED) {
            if (pin.getUpdatedAt() != null && (since == null || pin.getUpdatedAt().isAfter(since))) since = pin.getUpdatedAt();
            final Instant from = since;
            long still = votes.stream().filter(v -> HazardVerification.STILL_THERE.equals(v.getVote())).filter(v -> after(v, from)).count();
            return still >= threshold ? STILL_PRESENT : null;
        }
        if (st.isOpen()) {
            final Instant from = since;
            long gone = votes.stream().filter(v -> HazardVerification.GONE.equals(v.getVote())).filter(v -> after(v, from)).count();
            long still = votes.stream().filter(v -> HazardVerification.STILL_THERE.equals(v.getVote())).filter(v -> after(v, from)).count();
            return gone >= threshold && gone > still ? POSSIBLY_GONE : null;
        }
        return null;
    }

    private static boolean after(HazardVerification v, Instant since) {
        return since == null || v.votedAt().isAfter(since);
    }
}
