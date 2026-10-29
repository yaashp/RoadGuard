package com.roadguard.controller;

import com.roadguard.dto.HazardDtos.HazardDetail;
import com.roadguard.dto.HazardDtos.NearbyHazardView;
import com.roadguard.dto.HazardDtos.VerifyRequest;
import com.roadguard.dto.HazardDtos.VerifyResult;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.security.SessionAuth;
import com.roadguard.service.HazardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Public hazard details and community verification. */
@RestController
@RequestMapping("/api/hazards")
@Access(AccessLevel.PUBLIC)
public class HazardController {
    private final HazardService hazards;
    private final SessionAuth sessions;

    public HazardController(HazardService hazards, SessionAuth sessions) {
        this.hazards = hazards;
        this.sessions = sessions;
    }

    /** Possible duplicates of a report you are about to make. */
    @GetMapping("/nearby")
    @Access(AccessLevel.USER)
    public List<NearbyHazardView> nearby(@RequestParam("lat") double lat, @RequestParam("lng") double lng,
                                         @RequestParam(value = "category", required = false) String category,
                                         @RequestParam(value = "radius", required = false) Double radius) {
        return hazards.nearby(lat, lng, category, radius);
    }

    @GetMapping("/{id}")
    public HazardDetail detail(@PathVariable("id") Long id, HttpServletRequest http) {
        return hazards.detail(id, sessions.currentUserOrNull(http));
    }

    @PostMapping("/{id}/verify")
    @Access(AccessLevel.USER)
    public VerifyResult verify(@PathVariable("id") Long id, @RequestBody VerifyRequest req, HttpServletRequest http) {
        return hazards.verify(sessions.requireUser(http), id, req.vote());
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable("id") Long id) {
        return image(hazards.photo(id, false));
    }

    @GetMapping("/{id}/resolution-photo")
    public ResponseEntity<byte[]> resolutionPhoto(@PathVariable("id") Long id) {
        return image(hazards.photo(id, true));
    }

    private static ResponseEntity<byte[]> image(HazardService.Photo p) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.contentType()))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .header("X-Content-Type-Options", "nosniff").body(p.bytes());
    }
}
