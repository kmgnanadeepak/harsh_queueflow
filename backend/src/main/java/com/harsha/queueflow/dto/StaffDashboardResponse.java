package com.harsha.queueflow.dto;

import java.util.List;

import com.harsha.queueflow.enums.CounterStatus;

/**
 * Everything a staff member needs to run one counter, delivered in a single call
 * so the dashboard never has to stitch several endpoints together.
 */
public record StaffDashboardResponse(
        CounterInfo counter,
        List<CounterInfo> availableCounters,
        List<StaffTokenResponse> waitingQueue,
        StaffTokenResponse currentToken,
        StaffStats stats,
        List<StaffTokenResponse> recentCompleted
) {
    public record CounterInfo(
            Long id,
            String counterNumber,
            String name,
            CounterStatus status,
            Long serviceId,
            String serviceName,
            String organizationName,
            int averageServiceTime
    ) {
    }

    public record StaffStats(
            int waiting,
            int serving,
            int completedToday,
            int skippedToday,
            int emergencyWaiting,
            int totalToday
    ) {
    }
}
