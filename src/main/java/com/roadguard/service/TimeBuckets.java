package com.roadguard.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Week (Monday start) and month bucketing in a fixed time zone, so charts do not shift with the server's locale. */
public final class TimeBuckets {
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private TimeBuckets() {}

    public static LocalDate weekStart(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static YearMonth month(Instant instant, ZoneId zone) {
        return YearMonth.from(instant.atZone(zone));
    }

    /** The last `count` week-start dates ending with the current week, oldest first. */
    public static List<LocalDate> lastWeeks(Instant now, ZoneId zone, int count) {
        LocalDate current = weekStart(now, zone);
        List<LocalDate> out = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) out.add(current.minusWeeks(i));
        return out;
    }

    /** The last `count` months ending with the current month, oldest first. */
    public static List<YearMonth> lastMonths(Instant now, ZoneId zone, int count) {
        YearMonth current = month(now, zone);
        List<YearMonth> out = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) out.add(current.minusMonths(i));
        return out;
    }

    public static String weekLabel(LocalDate weekStart) { return DAY_LABEL.format(weekStart); }
    public static String monthLabel(YearMonth month) { return MONTH_LABEL.format(month.atDay(1)); }
}
