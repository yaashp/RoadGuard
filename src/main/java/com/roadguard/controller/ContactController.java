package com.roadguard.controller;

import com.roadguard.dto.ContactDtos.ContactRequest;
import com.roadguard.security.Access;
import com.roadguard.security.AccessLevel;
import com.roadguard.service.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
@Access(AccessLevel.PUBLIC)
public class ContactController {
    private final ContactService contact;

    public ContactController(ContactService contact) {
        this.contact = contact;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submit(@RequestBody ContactRequest body, HttpServletRequest http) {
        contact.submit(body, http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Thanks. Your message has been received."));
    }
}
