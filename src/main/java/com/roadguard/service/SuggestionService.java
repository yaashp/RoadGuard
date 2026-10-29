package com.roadguard.service;

import com.roadguard.dto.InsightDtos.CategorySuggestion;
import com.roadguard.dto.InsightDtos.DescriptionDraft;
import com.roadguard.model.IssueCategory;
import com.roadguard.model.Severity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule-based helpers for the report form. There is NO AI model behind this: the category comes from keyword matching
 * on the user's own words and the draft is a fill-in-the-blanks template. Both are suggestions the user must accept and can edit.
 */
@Service
public class SuggestionService {
    static final String METHOD = "Rule-based keyword matching (no AI model is used)";
    private static final Map<IssueCategory, List<String>> RULES = new LinkedHashMap<>();

    static {
        RULES.put(IssueCategory.POTHOLE, List.of("pothole", "potholes", "hole", "holes", "crater", "pit"));
        RULES.put(IssueCategory.ROAD_DAMAGE, List.of("crack", "cracks", "cracked", "damaged road", "broken road", "sinking", "subsidence", "caved", "uneven", "peeling"));
        RULES.put(IssueCategory.ACCIDENT, List.of("accident", "crash", "collision", "skid", "skidded", "overturned"));
        RULES.put(IssueCategory.WATERLOGGING, List.of("water", "flood", "flooded", "waterlogged", "waterlogging", "drain", "drainage", "puddle", "submerged", "overflow"));
        RULES.put(IssueCategory.STREETLIGHT, List.of("streetlight", "streetlights", "street light", "lamp", "lamppost", "no light", "light is out", "dark"));
        RULES.put(IssueCategory.TRAFFIC_SIGNAL, List.of("traffic signal", "traffic light", "signal", "signals", "red light"));
        RULES.put(IssueCategory.CONSTRUCTION, List.of("construction", "digging", "excavation", "roadwork", "road work", "barricade", "diversion"));
        RULES.put(IssueCategory.DEBRIS, List.of("debris", "rubble", "garbage", "waste", "stones", "gravel", "sand", "oil spill", "glass"));
        RULES.put(IssueCategory.OBSTRUCTION, List.of("tree", "branch", "fallen", "blocked", "obstruction", "stalled", "broken down", "pole"));
        RULES.put(IssueCategory.TRAFFIC_HAZARD, List.of("speed breaker", "speed bump", "hump", "unmarked", "signage", "sign missing", "wrong side", "blind turn"));
    }

    public CategorySuggestion suggestCategory(String text) {
        String t = " " + Inputs.clean(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ") + " ";
        IssueCategory best = null;
        List<String> bestWords = List.of();
        for (Map.Entry<IssueCategory, List<String>> e : RULES.entrySet()) {
            List<String> hits = new ArrayList<>();
            for (String k : e.getValue()) if (t.contains(" " + k + " ")) hits.add(k);
            if (hits.size() > bestWords.size()) { best = e.getKey(); bestWords = hits; }
        }
        if (best == null) {
            return new CategorySuggestion(null, List.of(), "none", METHOD, "No category could be suggested from this text. Please choose one yourself.");
        }
        String confidence = bestWords.size() >= 2 ? "medium" : "low";
        return new CategorySuggestion(best.label(), bestWords, confidence, METHOD,
                "Suggested because the text mentions: " + String.join(", ", bestWords) + ". Check it before applying.");
    }

    public DescriptionDraft draft(String categoryLabel, String severityLabel, String roadName, String landmark) {
        IssueCategory cat = IssueCategory.fromLabel(categoryLabel).orElse(IssueCategory.OTHER);
        Severity sev = Severity.fromLabel(severityLabel).orElse(Severity.MEDIUM);
        String road = Inputs.clean(roadName);
        String lm = Inputs.clean(landmark);
        StringBuilder sb = new StringBuilder(cat.label()).append(" reported");
        if (!road.isEmpty()) sb.append(" on ").append(road);
        if (!lm.isEmpty()) sb.append(", near ").append(lm);
        sb.append(". ");
        sb.append(switch (sev) {
            case HIGH -> "It looks dangerous for road users and needs quick attention. ";
            case MEDIUM -> "It is a problem for traffic and should be repaired soon. ";
            case LOW -> "It is a minor problem but should be fixed. ";
        });
        sb.append("Details to add: which lane it is in, how large it is and how long it has been there.");
        return new DescriptionDraft(sb.toString(), "Template filled from the details you entered (no AI model is used)");
    }
}
