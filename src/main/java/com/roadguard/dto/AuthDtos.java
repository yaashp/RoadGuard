package com.roadguard.dto;

import com.roadguard.model.User;

import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(String name, String email, String phone, String password) {}
    public record LoginRequest(String email, String password) {}
    public record UpdateProfileRequest(String name, String phone) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    /** Never contains the password hash. */
    public record UserDto(Long id, String name, String email, String phone, String role, Instant createdAt) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getCreatedAt());
        }
    }
}
