package com.roadguard.service;

import com.roadguard.dto.PreferenceDtos.PreferenceRequest;
import com.roadguard.dto.PreferenceDtos.PreferenceView;
import com.roadguard.exception.Validation;
import com.roadguard.model.User;
import com.roadguard.model.UserPreference;
import com.roadguard.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class PreferenceService {
    private static final Set<String> THEMES = Set.of("light", "dark", "system");
    /** Must match the supported list in static/js/i18n/config.js. */
    private static final Set<String> LANGUAGES = Set.of("en", "hi", "bn", "mr", "te", "ta", "gu", "ur", "kn", "or", "ml", "pa");

    private final UserPreferenceRepository repo;

    public PreferenceService(UserPreferenceRepository repo) {
        this.repo = repo;
    }

    /** The user's saved preferences, or (unsaved) defaults when they never changed anything. */
    @Transactional(readOnly = true)
    public UserPreference effective(User user) {
        return repo.findByUser_Id(user.getId()).orElseGet(() -> new UserPreference(user));
    }

    @Transactional(readOnly = true)
    public PreferenceView view(User user) {
        return toView(effective(user));
    }

    @Transactional
    public PreferenceView update(User user, PreferenceRequest req) {
        Validation v = new Validation();
        if (req.theme() != null) v.check(THEMES.contains(req.theme()), "theme", "Theme must be light, dark or system.");
        if (req.language() != null) v.check(LANGUAGES.contains(req.language()), "language", "Choose a language from the list.");
        v.throwIfInvalid();

        UserPreference p = repo.findByUser_Id(user.getId()).orElseGet(() -> new UserPreference(user));
        if (req.theme() != null) p.setTheme(req.theme());
        if (req.language() != null) p.setLanguage(req.language());
        if (req.notifyOnStatusChange() != null) p.setNotifyOnStatusChange(req.notifyOnStatusChange());
        if (req.notifyOnSubmission() != null) p.setNotifyOnSubmission(req.notifyOnSubmission());
        if (req.mapShowResolved() != null) p.setMapShowResolved(req.mapShowResolved());
        if (req.showSampleData() != null) p.setShowSampleData(req.showSampleData());
        if (req.showOnLeaderboard() != null) p.setShowOnLeaderboard(req.showOnLeaderboard());
        return toView(repo.save(p));
    }

    private static PreferenceView toView(UserPreference p) {
        return new PreferenceView(p.getTheme(), p.isNotifyOnStatusChange(), p.isNotifyOnSubmission(),
                p.isMapShowResolved(), p.isShowSampleData(), p.isShowOnLeaderboard(), p.getLanguage());
    }
}
