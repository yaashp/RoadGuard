package com.roadguard.controller;

import com.roadguard.dto.InsightDtos.CategorySuggestion;
import com.roadguard.dto.InsightDtos.DescriptionDraft;
import com.roadguard.dto.InsightDtos.DraftRequest;
import com.roadguard.dto.InsightDtos.TextRequest;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.service.SuggestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rule-based suggestions for the report form (no AI model). The user always confirms and can edit. */
@RestController
@RequestMapping("/api/suggest")
@Access(AccessLevel.USER)
public class SuggestController {
    private final SuggestionService suggestions;

    public SuggestController(SuggestionService suggestions) { this.suggestions = suggestions; }

    @PostMapping("/category")
    public CategorySuggestion category(@RequestBody TextRequest req) { return suggestions.suggestCategory(req.text()); }

    @PostMapping("/description")
    public DescriptionDraft description(@RequestBody DraftRequest req) { return suggestions.draft(req.category(), req.severity(), req.roadName(), req.landmark()); }
}
