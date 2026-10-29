package com.roadguard.controller;

import com.roadguard.config.RoadGuardProperties;
import com.roadguard.dto.MapDtos.CategoryInfo;
import com.roadguard.dto.MapDtos.ImageRules;
import com.roadguard.dto.MapDtos.MapConfig;
import com.roadguard.dto.MapDtos.MetaResponse;
import com.roadguard.model.ComplaintStatus;
import com.roadguard.model.IssueCategory;
import com.roadguard.model.Severity;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.service.RoutePlannerService;
import com.roadguard.service.RouteSafetyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Static reference data the front end needs (categories, statuses, map tile settings ...). */
@RestController
@RequestMapping("/api/meta")
@Access(AccessLevel.PUBLIC)
public class MetaController {
    private final RoadGuardProperties props;
    private final RouteSafetyService safety;

    public MetaController(RoadGuardProperties props, RouteSafetyService safety) {
        this.props = props;
        this.safety = safety;
    }

    @GetMapping
    public MetaResponse meta() {
        List<CategoryInfo> categories = Arrays.stream(IssueCategory.values())
                .map(c -> new CategoryInfo(c.label(), c.color(), c.icon())).toList();
        List<String> severities = Arrays.stream(Severity.values()).map(Severity::label).toList();
        List<String> statuses = Arrays.stream(ComplaintStatus.values()).map(ComplaintStatus::label).toList();
        Map<String, List<String>> transitions = new LinkedHashMap<>();
        for (ComplaintStatus s : ComplaintStatus.values()) {
            transitions.put(s.label(), s.allowedNext().stream().map(ComplaintStatus::label).toList());
        }
        RoadGuardProperties.MapSettings m = props.getMap();
        return new MetaResponse(categories, severities, statuses, transitions,
                new MapConfig(m.getTileUrl(), m.getAttribution(), m.getMaxZoom(), m.getCenterLat(), m.getCenterLng(), m.getZoom()),
                new ImageRules(props.getUpload().getMaxBytes(), List.of("image/jpeg", "image/png", "image/webp")),
                safety.model(),
                "Records labelled \"Sample data\" are demonstration entries created by the seeder. They are not real reports.",
                RoutePlannerService.DISCLAIMER);
    }
}
