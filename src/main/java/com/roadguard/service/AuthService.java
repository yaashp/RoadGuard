package com.roadguard.service;

import com.roadguard.dto.AuthDtos.ChangePasswordRequest;
import com.roadguard.dto.AuthDtos.LoginRequest;
import com.roadguard.dto.AuthDtos.RegisterRequest;
import com.roadguard.dto.AuthDtos.UpdateProfileRequest;
import com.roadguard.exception.ApiException;
import com.roadguard.exception.Validation;
import com.roadguard.model.User;
import com.roadguard.repository.UserRepository;
import com.roadguard.security.SessionAuth;
import com.roadguard.security.SlidingWindowCounter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionAuth sessions;

    /** Wrong passwords per e-mail address and per client address, in a 10-minute window. */
    private final SlidingWindowCounter failuresByEmail = new SlidingWindowCounter(5, 10 * 60_000L);
    private final SlidingWindowCounter failuresByIp = new SlidingWindowCounter(30, 10 * 60_000L);
    /** Hash checked when the e-mail is unknown, so response time does not reveal which e-mails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, SessionAuth sessions) {
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.dummyHash = encoder.encode("roadguard-dummy-password-1");
    }

    @Transactional
    public User register(RegisterRequest req, HttpServletRequest http) {
        String name = Inputs.clean(req.name());
        String email = Inputs.clean(req.email());
        String phone = Inputs.clean(req.phone());
        String password = req.password();

        Validation v = new Validation();
        v.check(name.length() >= 2 && name.length() <= 80, "name", "Full name must be 2-80 characters.");
        v.check(Inputs.validEmail(email), "email", "Enter a valid email address.");
        v.check(Inputs.validPhone(phone), "phone", "Enter a valid phone number (7-15 digits).");
        Inputs.checkPassword(v, "password", password);
        v.throwIfInvalid();

        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("An account with this email already exists.");
        User saved;
        try {
            saved = users.saveAndFlush(new User(name, email, phone, encoder.encode(password), User.ROLE_USER));
        } catch (DataIntegrityViolationException e) {   // two people registering the same e-mail at once
            throw ApiException.conflict("An account with this email already exists.");
        }
        sessions.startSession(http, saved);
        return saved;
    }

    @Transactional
    public User login(LoginRequest req, HttpServletRequest http) {
        String email = Inputs.clean(req.email()).toLowerCase(Locale.ROOT);
        String password = req.password();
        if (email.isEmpty() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("Please enter your email and password.");
        }
        String ipKey = "ip:" + http.getRemoteAddr();
        String emailKey = "email:" + email;
        if (failuresByEmail.isBlocked(emailKey) || failuresByIp.isBlocked(ipKey)) {
            long wait = Math.max(failuresByEmail.retryAfterSeconds(emailKey), 1);
            throw ApiException.tooMany("Too many failed login attempts. Please try again in about " + ((wait + 59) / 60) + " minute(s).");
        }

        User user = users.findByEmailIgnoreCase(email).orElse(null);
        boolean ok = encoder.matches(password, user == null ? dummyHash : user.getPasswordHash()) && user != null;
        if (!ok) {
            failuresByEmail.record(emailKey);
            failuresByIp.record(ipKey);
            throw ApiException.unauthorized("Invalid email or password.");
        }
        failuresByEmail.clear(emailKey);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        sessions.startSession(http, user);
        return user;
    }

    public void logout(HttpServletRequest http) {
        sessions.endSession(http);
    }

    @Transactional
    public User updateProfile(User user, UpdateProfileRequest req) {
        String name = Inputs.clean(req.name());
        String phone = Inputs.clean(req.phone());
        Validation v = new Validation();
        v.check(name.length() >= 2 && name.length() <= 80, "name", "Full name must be 2-80 characters.");
        v.check(Inputs.validPhone(phone), "phone", "Enter a valid phone number (7-15 digits).");
        v.throwIfInvalid();
        user.setName(name);
        user.setPhone(phone);
        return users.save(user);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest req, HttpServletRequest http) {
        Validation v = new Validation();
        if (req.currentPassword() == null || !encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            v.fail("currentPassword", "Current password is incorrect.");
        }
        Inputs.checkPassword(v, "newPassword", req.newPassword());
        if (req.newPassword() != null && req.newPassword().equals(req.currentPassword())) {
            v.fail("newPassword", "New password must be different from the current one.");
        }
        v.throwIfInvalid();
        user.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(user);
        if (http.getSession(false) != null) http.changeSessionId();   // fresh session id after a credential change
    }
}
