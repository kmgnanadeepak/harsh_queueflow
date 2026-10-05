package com.harsha.queueflow.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(
        Overview overview,
        List<ServiceStat> tokensPerService,
        List<TimeBucket> queueActivity,
        List<HourBucket> peakPeriods,
        List<TokenBreakdown> statusBreakdown,
        List<CounterLoad> counterLoad,
        Instant generatedAt
) {
    public record Overview(
            long totalTokens,
            long completedTokens,
            long skippedTokens,
            long cancelledTokens,
            long waitingTokens,
            long servingTokens,
            double averageWaitingMinutes,
            double averageServiceMinutes,
            double completionRate,
            long totalUsers,
            long activeServices,
            long activeCounters,
            long staffCount
    ) {
    }

    public record ServiceStat(
            Long serviceId,
            String serviceName,
            String organizationName,
            long tokensGenerated,
            long tokensCompleted,
            int averageServiceTime
    ) {
    }

    public record TimeBucket(
            String date,
            long generated,
            long completed
    ) {
    }

    public record HourBucket(
            int hour,
            long tokens
    ) {
    }

    public record TokenBreakdown(
            String status,
            long count
    ) {
    }

    public record CounterLoad(
            Long counterId,
            String counterNumber,
            String serviceName,
            String staffName,
            String status,
            long completedTokens
    ) {
    }
}
