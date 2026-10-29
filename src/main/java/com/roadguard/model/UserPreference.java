package com.roadguard.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_preferences")
public class UserPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    /** "light", "dark" or "system". */
    @Column(nullable = false)
    private String theme = "system";

    @Column(nullable = false)
    private boolean notifyOnStatusChange = true;

    @Column(nullable = false)
    private boolean notifyOnSubmission = true;

    /** Interface language code (en, hi, bn, mr, te, ta, gu, ur, kn, or, ml, pa). Nullable so existing rows migrate
     *  without a default value; null is read as "en". The allowed list lives in PreferenceService. */
    private String language;

    /** Map: show resolved issues. */
    @Column(nullable = false)
    private boolean mapShowResolved = false;

    /** Include records flagged as sample/demo data in dashboards, the map and route scoring. */
    @Column(nullable = false)
    private boolean showSampleData = true;

    /** Opt-in: show a short display name (first name + initial) on the public monthly leaderboard. Nullable = never chosen = off. */
    private Boolean showOnLeaderboard;

    public UserPreference() {}

    public UserPreference(User user) { this.user = user; }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getLanguage() { return language == null ? "en" : language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isNotifyOnStatusChange() { return notifyOnStatusChange; }
    public void setNotifyOnStatusChange(boolean v) { this.notifyOnStatusChange = v; }
    public boolean isNotifyOnSubmission() { return notifyOnSubmission; }
    public void setNotifyOnSubmission(boolean v) { this.notifyOnSubmission = v; }
    public boolean isMapShowResolved() { return mapShowResolved; }
    public void setMapShowResolved(boolean v) { this.mapShowResolved = v; }
    public boolean isShowOnLeaderboard() { return Boolean.TRUE.equals(showOnLeaderboard); }
    public void setShowOnLeaderboard(boolean v) { this.showOnLeaderboard = v; }
    public boolean isShowSampleData() { return showSampleData; }
    public void setShowSampleData(boolean v) { this.showSampleData = v; }
}
