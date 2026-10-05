package com.harsha.queueflow.dto;

import java.time.Instant;

import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.enums.CounterStatus;

public record CounterResponse(
        Long id,
        String counterNumber,
        String name,
        CounterStatus status,
        boolean active,
        Long organizationId,
        String organizationName,
        Long serviceId,
        String serviceName,
        Long staffId,
        String staffName,
        String currentTokenNumber
) {
    public static CounterResponse from(Counter counter, String currentTokenNumber) {
        return new CounterResponse(
                counter.getId(),
                counter.getCounterNumber(),
                counter.getName(),
                counter.getStatus(),
                counter.isActive(),
                counter.getOrganization().getId(),
                counter.getOrganization().getName(),
                counter.getQueueService().getId(),
                counter.getQueueService().getName(),
                counter.getStaff() == null ? null : counter.getStaff().getId(),
                counter.getStaff() == null ? null : counter.getStaff().getName(),
                currentTokenNumber
        );
    }
}
