package com.roadguard.dto;

public final class ContactDtos {
    private ContactDtos() {}

    /** "website" is a honeypot field: hidden from people, so any value means an automated form-filler. */
    public record ContactRequest(String name, String email, String subject, String message, String website) {}
}
