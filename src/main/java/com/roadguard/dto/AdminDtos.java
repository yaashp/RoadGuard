package com.roadguard.dto;

import java.util.List;
import java.util.Map;

public final class AdminDtos {
    private AdminDtos() {}

    public record Overview(DashboardDtos.Summary summary, long registeredUsers, long awaitingReview,
                           Map<String, List<String>> statusTransitions) {}

    public record AdminComplaintDetail(ComplaintDtos.ComplaintView complaint,
                                       List<ComplaintDtos.TimelineEntry> timeline,
                                       String reporterEmail, String reporterPhone) {}
}
