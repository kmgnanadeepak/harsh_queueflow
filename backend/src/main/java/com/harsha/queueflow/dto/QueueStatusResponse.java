package com.harsha.queueflow.dto;

import java.time.Instant;
import java.util.List;

import com.harsha.queueflow.enums.CounterStatus;

/**
 * Real-time snapshot of one service queue.
 */
public record QueueStatusResponse(
        Long serviceId,
        String serviceName,
        String organizationName,
        int averageServiceTime,
        int waitingCount,
        int servingCount,
        int calledCount,
        int completedToday,
        int activeCounters,
        int availableCounters,
        List<QueueEntry> queue,
        List<CounterSummary> counters,
        Instant generatedAt
) {
    public record QueueEntry(
            String tokenNumber,
            int position,
            int peopleAhead,
            int estimatedWaitingMinutes,
            String priority
    ) {
    }

    public record CounterSummary(
            Long id,
            String counterNumber,
            CounterStatus status,
            String servingToken
    ) {
    }
}
