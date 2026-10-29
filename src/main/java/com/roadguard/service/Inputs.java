package com.roadguard.service;

import com.roadguard.exception.Validation;

import java.util.regex.Pattern;

/** Shared input rules, so every endpoint validates the same way. */
public final class Inputs {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{7,15}$");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    private Inputs() {}

    /** Trimmed text, or "" when null. Control characters (other than newline/tab) are removed. */
    public static String clean(String text) {
        if (text == null) return "";
        return CONTROL.matcher(text).replaceAll("").trim();
    }

    public static boolean validEmail(String email) {
        return email != null && email.length() <= 120 && EMAIL.matcher(email).matches();
    }

    /** Optional phone: empty is fine; otherwise 7-15 digits with an optional leading +. */
    public static boolean validPhone(String phone) {
        if (phone == null || phone.isEmpty()) return true;
        return PHONE.matcher(phone.replaceAll("[\\s-]", "")).matches();
    }

    public static boolean validLatLng(Double lat, Double lng) {
        return lat != null && lng != null && Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    /** At least 8 characters (max 64: bcrypt ignores anything past 72 bytes), with a letter and a digit. */
    public static void checkPassword(Validation v, String field, String password) {
        if (password == null || password.length() < 8) {
            v.fail(field, "Password must be at least 8 characters.");
        } else if (password.length() > 64) {
            v.fail(field, "Password must be at most 64 characters.");
        } else if (!password.matches(".*\\p{L}.*") || !password.matches(".*\\d.*")) {
            v.fail(field, "Password must contain at least one letter and one number.");
        }
    }
}
