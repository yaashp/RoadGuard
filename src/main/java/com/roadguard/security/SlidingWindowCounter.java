package com.roadguard.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory "N events per window per key" counter, used for failed-login throttling and for
 * protecting the outbound geocoding / routing calls. State is per application instance and is
 * cleared on restart (fine for a single-server college project; use a shared store if you scale out).
 */
public class SlidingWindowCounter {
    private final int max;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();

    public SlidingWindowCounter(int max, long windowMillis) {
        this(max, windowMillis, System::currentTimeMillis);
    }

    public SlidingWindowCounter(int max, long windowMillis, LongSupplier clock) {
        this.max = max;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** True if the key has already used up its quota (does not record anything). */
    public boolean isBlocked(String key) {
        return count(key) >= max;
    }

    /** Records an event unconditionally. */
    public void record(String key) {
        Deque<Long> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(clock.getAsLong());
        }
    }

    /** Records an event only if the quota allows it; returns whether it was allowed. */
    public boolean tryAcquire(String key) {
        Deque<Long> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            if (q.size() >= max) return false;
            q.addLast(clock.getAsLong());
            return true;
        }
    }

    public void clear(String key) {
        events.remove(key);
    }

    public int count(String key) {
        Deque<Long> q = events.get(key);
        if (q == null) return 0;
        synchronized (q) {
            prune(q);
            return q.size();
        }
    }

    /** Seconds until the oldest counted event leaves the window (at least 1). */
    public long retryAfterSeconds(String key) {
        Deque<Long> q = events.get(key);
        if (q == null) return 1;
        synchronized (q) {
            prune(q);
            if (q.isEmpty()) return 1;
            long wait = q.peekFirst() + windowMillis - clock.getAsLong();
            return Math.max(1, (wait + 999) / 1000);
        }
    }

    private void prune(Deque<Long> q) {
        long cutoff = clock.getAsLong() - windowMillis;
        while (!q.isEmpty() && q.peekFirst() <= cutoff) q.removeFirst();
    }
}
