package com.roadguard.service;

import com.roadguard.dto.ContactDtos.ContactRequest;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.Validation;
import com.roadguard.model.ContactMessage;
import com.roadguard.repository.ContactMessageRepository;
import com.roadguard.security.SlidingWindowCounter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class ContactService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ContactMessageRepository messages;
    /** At most 5 messages per network address per hour. */
    private final SlidingWindowCounter perAddress = new SlidingWindowCounter(5, 60 * 60_000L);

    public ContactService(ContactMessageRepository messages) {
        this.messages = messages;
    }

    public void submit(ContactRequest req, String address) {
        String key = address == null ? "unknown" : address;
        // Honeypot: a real visitor never fills the hidden field. Pretend success so bots learn nothing.
        if (req.website() != null && !req.website().isBlank()) return;
        if (perAddress.isBlocked(key)) {
            throw ApiException.tooMany("Too many messages from this network. Please try again later.");
        }

        String name = trim(req.name());
        String email = trim(req.email());
        String subject = trim(req.subject());
        String message = trim(req.message());

        Validation v = new Validation();
        v.check(name.length() >= 2 && name.length() <= 80, "name", "Please enter your name (2 to 80 characters).");
        v.check(email.length() <= 254 && EMAIL.matcher(email).matches(), "email", "Please enter a valid email address.");
        v.check(subject.length() <= 120, "subject", "Subject is too long (maximum 120 characters).");
        v.check(message.length() >= 10 && message.length() <= 2000, "message", "Please write 10 to 2000 characters.");
        v.throwIfInvalid();

        perAddress.record(key);
        messages.save(new ContactMessage(name, email, subject.isEmpty() ? null : subject, message, Instant.now()));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
