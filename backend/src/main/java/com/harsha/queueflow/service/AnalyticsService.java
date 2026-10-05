package com.harsha.queueflow.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.AdminDashboardResponse;
import com.harsha.queueflow.dto.AnalyticsResponse;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.repository.CounterRepository;
import com.harsha.queueflow.repository.OrganizationRepository;
import com.harsha.queueflow.repository.QueueServiceRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;
import com.harsha.queueflow.repository.UserRepository;

@Service
public class AnalyticsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int ACTIVITY_DAYS = 7;

    private final QueueTokenRepository tokenRepository;
    private final QueueServiceRepository serviceRepository;
    private final CounterRepository counterRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public AnalyticsService(QueueTokenRepository tokenRepository,
                            QueueServiceRepository serviceRepository,
                            CounterRepository counterRepository,
                            OrganizationRepository organizationRepository,
                            UserRepository userRepository) {
        this.tokenRepository = tokenRepository;
        this.serviceRepository = serviceRepository;
        this.counterRepository = counterRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics() {
        Instant now = Instant.now();
        List<QueueToken> allTokens = tokenRepository.findAll();
        Instant windowStart = LocalDate.now(ZoneOffset.UTC).minusDays(ACTIVITY_DAYS - 1L)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        List<QueueToken> windowTokens = allTokens.stream()
                .filter(token -> !token.getCreatedAt().isBefore(windowStart))
                .toList();

        long completed = allTokens.stream().filter(t -> t.getStatus() == TokenStatus.COMPLETED).count();
        long skipped = allTokens.stream().filter(t -> t.getStatus() == TokenStatus.SKIPPED).count();
        long cancelled = allTokens.stream().filter(t -> t.getStatus() == TokenStatus.CANCELLED).count();
        long waiting = allTokens.stream().filter(t -> t.getStatus() == TokenStatus.WAITING).count();
        long serving = allTokens.stream().filter(t -> t.getStatus() == TokenStatus.SERVING).count();

        double avgWaiting = averageWaitingMinutes(allTokens);
        double avgService = averageServiceMinutes(allTokens);
        double completionRate = allTokens.isEmpty() ? 0
                : Math.round((completed * 10000.0) / allTokens.size()) / 100.0;

        return new AnalyticsResponse(
                new AnalyticsResponse.Overview(
                        allTokens.size(), completed, skipped, cancelled, waiting, serving,
                        round1(avgWaiting), round1(avgService), completionRate,
                        userRepository.count(),
                        serviceRepository.countByActiveTrue(),
                        counterRepository.countByActiveTrue(),
                        userRepository.countByRole(Role.STAFF)
                ),
                tokensPerService(windowStart, now),
                activityByDay(windowStart, now),
                peakHours(windowTokens),
                statusBreakdown(allTokens),
                counterLoad(),
                now
        );
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        Instant now = Instant.now();
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<QueueToken> allTokens = tokenRepository.findAll();

        long waiting = allTokens.stream().filter(t -> t.getStatus() == TokenStatus.WAITING).count();
        long serving = allTokens.stream().filter(t -> t.getStatus() == TokenStatus.SERVING).count();
        long completedToday = allTokens.stream()
                .filter(t -> t.getStatus() == TokenStatus.COMPLETED && t.getCompletedAt() != null
                        && !t.getCompletedAt().isBefore(startOfDay))
                .count();
        long tokensToday = allTokens.stream().filter(t -> !t.getCreatedAt().isBefore(startOfDay)).count();

        List<AdminDashboardResponse.QueueServiceSummary> services = serviceRepository.findAll().stream()
                .map(service -> new AdminDashboardResponse.QueueServiceSummary(
                        service.getId(),
                        service.getName(),
                        service.getOrganization().getName(),
                        service.getAverageServiceTime(),
                        service.isActive(),
                        allTokens.stream().filter(t -> t.getQueueService().getId().equals(service.getId())
                                && t.getStatus() == TokenStatus.WAITING).count(),
                        allTokens.stream().filter(t -> t.getQueueService().getId().equals(service.getId())
                                && t.getStatus() == TokenStatus.SERVING).count(),
                        counterRepository.findByQueueServiceIdAndActiveTrueAndStatusNotOrderByCounterNumberAsc(
                                service.getId(), CounterStatus.OFFLINE).size()
                ))
                .toList();

        List<AdminDashboardResponse.CounterSummary> counters = counterRepository.findAll().stream()
                .map(counter -> new AdminDashboardResponse.CounterSummary(
                        counter.getId(),
                        counter.getCounterNumber(),
                        counter.getQueueService().getName(),
                        counter.getStatus(),
                        counter.getStaff() == null ? null : counter.getStaff().getName(),
                        currentTokenOf(counter, allTokens)
                ))
                .toList();

        List<AdminDashboardResponse.StaffSummary> staff = userRepository
                .findByRoleInAndActiveTrueOrderByNameAsc(List.of(Role.STAFF, Role.ADMIN)).stream()
                .map(user -> {
                    List<Counter> assigned = counterRepository.findByStaffIdAndActiveTrue(user.getId());
                    Counter counter = assigned.isEmpty() ? null : assigned.get(0);
                    return new AdminDashboardResponse.StaffSummary(
                            user.getId(), user.getName(), user.getEmail(),
                            counter == null ? null : counter.getCounterNumber(),
                            counter == null ? null : counter.getQueueService().getName());
                })
                .toList();

        return new AdminDashboardResponse(
                new AdminDashboardResponse.Stats(
                        userRepository.count(),
                        organizationRepository.count(),
                        serviceRepository.countByActiveTrue(),
                        counterRepository.countByActiveTrueAndStatusNot(CounterStatus.OFFLINE),
                        waiting, serving, completedToday, tokensToday
                ),
                services,
                counters,
                staff
        );
    }

    // ------------------------------------------------------------- internals

    private List<AnalyticsResponse.ServiceStat> tokensPerService(Instant from, Instant to) {
        Map<Long, QueueService> services = new LinkedHashMap<>();
        serviceRepository.findAll().forEach(service -> services.put(service.getId(), service));
        List<QueueToken> tokens = tokenRepository.findAll().stream()
                .filter(token -> !token.getCreatedAt().isBefore(from))
                .toList();

        return services.values().stream().map(service -> {
            List<QueueToken> forService = tokens.stream()
                    .filter(token -> token.getQueueService().getId().equals(service.getId()))
                    .toList();
            return new AnalyticsResponse.ServiceStat(
                    service.getId(),
                    service.getName(),
                    service.getOrganization().getName(),
                    forService.size(),
                    forService.stream().filter(t -> t.getStatus() == TokenStatus.COMPLETED).count(),
                    service.getAverageServiceTime()
            );
        }).sorted((a, b) -> Long.compare(b.tokensGenerated(), a.tokensGenerated())).toList();
    }

    private List<AnalyticsResponse.TimeBucket> activityByDay(Instant from, Instant to) {
        Map<String, long[]> buckets = new TreeMap<>();
        LocalDate day = LocalDate.ofInstant(from, ZoneOffset.UTC);
        LocalDate last = LocalDate.ofInstant(to, ZoneOffset.UTC);
        while (!day.isAfter(last)) {
            buckets.put(day.format(DAY), new long[2]);
            day = day.plusDays(1);
        }
        for (QueueToken token : tokenRepository.findAll()) {
            String key = LocalDate.ofInstant(token.getCreatedAt(), ZoneOffset.UTC).format(DAY);
            long[] bucket = buckets.get(key);
            if (bucket != null) {
                bucket[0]++;
            }
            if (token.getStatus() == TokenStatus.COMPLETED && token.getCompletedAt() != null) {
                long[] completedBucket = buckets.get(LocalDate.ofInstant(token.getCompletedAt(), ZoneOffset.UTC)
                        .format(DAY));
                if (completedBucket != null) {
                    completedBucket[1]++;
                }
            }
        }
        List<AnalyticsResponse.TimeBucket> result = new ArrayList<>(buckets.size());
        buckets.forEach((date, counts) -> result.add(new AnalyticsResponse.TimeBucket(date, counts[0], counts[1])));
        return result;
    }

    private List<AnalyticsResponse.HourBucket> peakHours(List<QueueToken> tokens) {
        long[] hours = new long[24];
        for (QueueToken token : tokens) {
            int hour = LocalDateTime.ofInstant(token.getCreatedAt(), ZoneOffset.UTC).getHour();
            hours[hour]++;
        }
        List<AnalyticsResponse.HourBucket> buckets = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            buckets.add(new AnalyticsResponse.HourBucket(hour, hours[hour]));
        }
        return buckets;
    }

    private List<AnalyticsResponse.TokenBreakdown> statusBreakdown(List<QueueToken> tokens) {
        return List.of(TokenStatus.values()).stream()
                .map(status -> new AnalyticsResponse.TokenBreakdown(status.name(),
                        tokens.stream().filter(t -> t.getStatus() == status).count()))
                .toList();
    }

    private List<AnalyticsResponse.CounterLoad> counterLoad() {
        List<QueueToken> allTokens = tokenRepository.findAll();
        return counterRepository.findAll().stream().map(counter -> new AnalyticsResponse.CounterLoad(
                counter.getId(),
                counter.getCounterNumber(),
                counter.getQueueService().getName(),
                counter.getStaff() == null ? null : counter.getStaff().getName(),
                counter.getStatus().name(),
                allTokens.stream().filter(t -> t.getCounter() != null
                        && t.getCounter().getId().equals(counter.getId())
                        && t.getStatus() == TokenStatus.COMPLETED).count()
        )).toList();
    }

    private String currentTokenOf(Counter counter, List<QueueToken> tokens) {
        return tokens.stream()
                .filter(t -> t.getCounter() != null && t.getCounter().getId().equals(counter.getId())
                        && (t.getStatus() == TokenStatus.SERVING || t.getStatus() == TokenStatus.CALLED))
                .map(QueueToken::getTokenNumber)
                .findFirst()
                .orElse(null);
    }

    private double averageWaitingMinutes(List<QueueToken> tokens) {
        return tokens.stream()
                .filter(t -> t.getCalledAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getCalledAt()).toMinutes())
                .average()
                .orElse(0);
    }

    private double averageServiceMinutes(List<QueueToken> tokens) {
        return tokens.stream()
                .filter(t -> t.getServingAt() != null && t.getCompletedAt() != null)
                .mapToLong(t -> Duration.between(t.getServingAt(), t.getCompletedAt()).toMinutes())
                .average()
                .orElse(0);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
