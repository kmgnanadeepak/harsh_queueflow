package com.harsha.queueflow.dto;

import java.util.List;

import com.harsha.queueflow.enums.CounterStatus;

/**
 * Compact admin dashboard payload (no charting, purely the KPI strip + live
 * queue snapshots).
 */
public record AdminDashboardResponse(
        Stats stats,
        List<QueueServiceSummary> services,
        List<CounterSummary> counters,
        List<StaffSummary> staff
) {
    public record Stats(
            long totalUsers,
            long totalOrganizations,
            long activeServices,
            long activeCounters,
            long waitingTokens,
            long servingTokens,
            long completedToday,
            long tokensToday
    ) {
    }

    public record QueueServiceSummary(
            Long id,
            String name,
            String organizationName,
            int averageServiceTime,
            boolean active,
            long waiting,
            long serving,
            int activeCounters
    ) {
    }

    public record CounterSummary(
            Long id,
            String counterNumber,
            String serviceName,
            CounterStatus status,
            String staffName,
            String currentToken
    ) {
    }

    public record StaffSummary(
            Long id,
            String name,
            String email,
            String counterNumber,
            String serviceName
    ) {
    }
}
