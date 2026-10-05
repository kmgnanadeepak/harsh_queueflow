package com.harsha.queueflow.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.CallNextRequest;
import com.harsha.queueflow.dto.QueueTokenView;
import com.harsha.queueflow.dto.RealtimeEvent;
import com.harsha.queueflow.dto.StaffDashboardResponse;
import com.harsha.queueflow.dto.StaffTokenResponse;
import com.harsha.queueflow.dto.TokenResponse;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.NotificationType;
import com.harsha.queueflow.enums.PriorityType;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.exception.BadRequestException;
import com.harsha.queueflow.exception.ForbiddenOperationException;
import com.harsha.queueflow.exception.NotFoundException;
import com.harsha.queueflow.repository.CounterRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;

/**
 * Counter operations available to STAFF (and ADMIN).
 *
 * <p>Every action is validated twice: the caller must be a privileged role
 * (enforced by Spring Security on {@code /api/staff/**}) <em>and</em> the
 * counter must be assigned to them. Token state transitions follow
 * {@code WAITING -> CALLED -> SERVING -> COMPLETED}, with
 * {@code WAITING/CALLED -> SKIPPED} and {@code WAITING -> CANCELLED}.</p>
 */
@Service
public class StaffQueueService {

    private final QueueTokenRepository tokenRepository;
    private final CounterRepository counterRepository;
    private final CounterService counterService;
    private final QueueEngine queueEngine;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;

    public StaffQueueService(QueueTokenRepository tokenRepository,
                             CounterRepository counterRepository,
                             CounterService counterService,
                             QueueEngine queueEngine,
                             NotificationService notificationService,
                             RealtimePublisher realtimePublisher) {
        this.tokenRepository = tokenRepository;
        this.counterRepository = counterRepository;
        this.counterService = counterService;
        this.queueEngine = queueEngine;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
    }

    // ------------------------------------------------------------- dashboard

    @Transactional(readOnly = true)
    public StaffDashboardResponse dashboard(Long staffId, Role role, Long counterId) {
        List<Counter> assigned = counterRepository.findByStaffIdAndActiveTrue(staffId);
        if (assigned.isEmpty() && role != Role.ADMIN) {
            throw new ForbiddenOperationException(
                    "No counter is assigned to your account. Ask an administrator to assign one.");
        }
        Counter counter = resolveCounter(staffId, role, counterId, assigned);
        QueueService service = counter.getQueueService();

        List<QueueToken> waiting = queueEngine.orderedWaiting(service);
        List<StaffTokenResponse> waitingRows = waiting.stream()
                .map(token -> toStaffRow(token, counter))
                .toList();

        QueueToken current = tokenRepository
                .findByCounterIdAndStatus(counter.getId(), TokenStatus.SERVING).stream().findFirst()
                .or(() -> tokenRepository.findByCounterIdAndStatus(counter.getId(), TokenStatus.CALLED)
                        .stream().findFirst())
                .orElse(null);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<QueueToken> serviceTokens = tokenRepository
                .findByQueueServiceIdAndStatusInOrderByCreatedAtAsc(service.getId(),
                        List.of(TokenStatus.WAITING, TokenStatus.SERVING, TokenStatus.CALLED,
                                TokenStatus.COMPLETED, TokenStatus.SKIPPED, TokenStatus.CANCELLED));

        int servingCount = (int) serviceTokens.stream().filter(t -> t.getStatus() == TokenStatus.SERVING).count();
        int completedToday = (int) serviceTokens.stream()
                .filter(t -> t.getStatus() == TokenStatus.COMPLETED && t.getCompletedAt() != null
                        && !t.getCompletedAt().isBefore(startOfDay))
                .count();
        int skippedToday = (int) serviceTokens.stream()
                .filter(t -> t.getStatus() == TokenStatus.SKIPPED && !t.getCreatedAt().isBefore(startOfDay))
                .count();
        int emergencyWaiting = (int) waiting.stream()
                .filter(t -> t.getPriorityType() == PriorityType.EMERGENCY).count();

        List<StaffTokenResponse> recent = serviceTokens.stream()
                .filter(t -> t.getStatus() == TokenStatus.COMPLETED && t.getCompletedAt() != null)
                .sorted((a, b) -> b.getCompletedAt().compareTo(a.getCompletedAt()))
                .limit(10)
                .map(token -> toStaffRow(token, null))
                .toList();

        return new StaffDashboardResponse(
                toCounterInfo(counter),
                assigned.stream().filter(c -> !c.getId().equals(counter.getId()))
                        .map(this::toCounterInfo).toList(),
                waitingRows,
                current == null ? null : toStaffRow(current, counter),
                new StaffDashboardResponse.StaffStats(
                        waiting.size(),
                        servingCount,
                        completedToday,
                        skippedToday,
                        emergencyWaiting,
                        serviceTokens.size()
                ),
                recent
        );
    }

    // ------------------------------------------------------------ operations

    @Transactional
    public TokenResponse callNext(Long staffId, Role role, CallNextRequest request) {
        List<Counter> assigned = counterRepository.findByStaffIdAndActiveTrue(staffId);
        Counter counter = resolveCounter(staffId, role, request.counterId(), assigned);
        if (counter.getStatus() == CounterStatus.OFFLINE) {
            throw new BadRequestException("This counter is offline");
        }
        if (tokenRepository.findByCounterIdAndStatus(counter.getId(), TokenStatus.CALLED).stream().findAny()
                .isPresent()) {
            throw new BadRequestException("A token is already waiting to be served at this counter");
        }

        QueueService service = counter.getQueueService();
        if (request.serviceId() != null && !request.serviceId().equals(service.getId())) {
            throw new BadRequestException("The selected service does not match this counter");
        }
        QueueToken next = queueEngine.orderedWaiting(service).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("The queue is empty - no token is waiting"));

        next.setStatus(TokenStatus.CALLED);
        next.setCalledAt(Instant.now());
        next.setCounter(counter);
        counter.setStatus(CounterStatus.BUSY);
        QueueToken saved = tokenRepository.saveAndFlush(next);
        counterRepository.saveAndFlush(counter);

        QueueTokenView view = queueEngine.describe(saved);
        notificationService.notify(saved.getUser(), saved, NotificationType.TOKEN_CALLED,
                "Token " + saved.getTokenNumber() + " has been called to counter "
                        + counter.getCounterNumber() + ".");
        realtimePublisher.publishTokenCalled(view);
        publishQueueStats(service, view);
        return TokenService.toResponse(view);
    }

    @Transactional
    public TokenResponse start(Long staffId, Role role, Long tokenId) {
        QueueToken token = requireToken(tokenId);
        Counter counter = requireTokenCounter(token);
        counterService.assertCanOperate(counter, staffId, role);
        if (token.getStatus() != TokenStatus.CALLED) {
            throw new BadRequestException("Only a called token can be moved to serving");
        }
        token.setStatus(TokenStatus.SERVING);
        token.setServingAt(Instant.now());
        counter.setStatus(CounterStatus.BUSY);
        QueueToken saved = tokenRepository.saveAndFlush(token);
        counterRepository.saveAndFlush(counter);

        QueueTokenView view = queueEngine.describe(saved);
        realtimePublisher.publishTokenStatusChanged(view);
        publishQueueStats(saved.getQueueService(), view);
        return TokenService.toResponse(view);
    }

    @Transactional
    public TokenResponse complete(Long staffId, Role role, Long tokenId) {
        QueueToken token = requireToken(tokenId);
        Counter counter = requireTokenCounter(token);
        counterService.assertCanOperate(counter, staffId, role);
        if (token.getStatus() != TokenStatus.SERVING && token.getStatus() != TokenStatus.CALLED) {
            throw new BadRequestException("Only a serving token can be completed");
        }
        token.setStatus(TokenStatus.COMPLETED);
        token.setCompletedAt(Instant.now());
        if (token.getServingAt() == null) {
            token.setServingAt(Instant.now());
        }
        releaseCounter(counter);
        QueueToken saved = tokenRepository.saveAndFlush(token);

        QueueTokenView view = queueEngine.describe(saved);
        notificationService.notify(saved.getUser(), saved, NotificationType.TOKEN_COMPLETED,
                "Token " + saved.getTokenNumber() + " has been completed. Thank you for waiting.");
        realtimePublisher.publishTokenCompleted(view);
        publishQueueStats(saved.getQueueService(), view);
        return TokenService.toResponse(view);
    }

    @Transactional
    public TokenResponse skip(Long staffId, Role role, Long tokenId) {
        QueueToken token = requireToken(tokenId);
        if (token.getStatus() != TokenStatus.WAITING && token.getStatus() != TokenStatus.CALLED) {
            throw new BadRequestException("Only a waiting or called token can be skipped");
        }
        if (token.getCounter() != null) {
            counterService.assertCanOperate(token.getCounter(), staffId, role);
        }
        token.setStatus(TokenStatus.SKIPPED);
        if (token.getCounter() != null) {
            releaseCounter(token.getCounter());
        }
        QueueToken saved = tokenRepository.saveAndFlush(token);

        QueueTokenView view = queueEngine.describe(saved);
        notificationService.notify(saved.getUser(), saved, NotificationType.GENERAL,
                "Token " + saved.getTokenNumber() + " was skipped. Please take a new token if you still need service.");
        realtimePublisher.publishTokenSkipped(view);
        publishQueueStats(saved.getQueueService(), view);
        return TokenService.toResponse(view);
    }

    @Transactional
    public CounterStatus setCounterStatus(Long staffId, Role role, Long counterId, CounterStatus status) {
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> NotFoundException.of("Counter", counterId));
        counterService.assertCanOperate(counter, staffId, role);
        boolean occupied = tokenRepository.findByCounterIdAndStatus(counterId, TokenStatus.SERVING).stream()
                .findAny().isPresent()
                || tokenRepository.findByCounterIdAndStatus(counterId, TokenStatus.CALLED).stream().findAny().isPresent();
        if (occupied && status != CounterStatus.BUSY) {
            throw new BadRequestException("Finish the current token before changing the counter status");
        }
        counter.setStatus(status);
        counterRepository.saveAndFlush(counter);
        return status;
    }

    // ---------------------------------------------------------------- helpers

    private Counter resolveCounter(Long staffId, Role role, Long requestedCounterId, List<Counter> assigned) {
        if (requestedCounterId != null) {
            Counter counter = counterRepository.findById(requestedCounterId)
                    .orElseThrow(() -> NotFoundException.of("Counter", requestedCounterId));
            counterService.assertCanOperate(counter, staffId, role);
            return counter;
        }
        if (assigned.isEmpty()) {
            throw new BadRequestException("No counter is assigned to your account - ask an administrator");
        }
        return assigned.get(0);
    }

    private QueueToken requireToken(Long tokenId) {
        return tokenRepository.findById(tokenId).orElseThrow(() -> NotFoundException.of("Token", tokenId));
    }

    private Counter requireTokenCounter(QueueToken token) {
        if (token.getCounter() == null) {
            throw new BadRequestException("This token has not been assigned to a counter yet");
        }
        return token.getCounter();
    }

    private void releaseCounter(Counter counter) {
        counter.setStatus(CounterStatus.AVAILABLE);
        counterRepository.saveAndFlush(counter);
    }

    private void publishQueueStats(QueueService service, QueueTokenView token) {
        QueueEngine.QueueSnapshot snapshot = queueEngine.snapshot(service);
        realtimePublisher.publishQueueUpdate(service.getId(), new RealtimeEvent(
                RealtimeEvent.QUEUE_UPDATED,
                token.id(), token.tokenNumber(), service.getId(), service.getName(), null, null, null, null,
                null, null, null, null,
                snapshot.ordered().size(), (int) snapshot.ordered().stream()
                        .filter(view -> view.status() == TokenStatus.SERVING).count(),
                snapshot.activeCounters(),
                null, null, Instant.now()));
    }

    private StaffDashboardResponse.CounterInfo toCounterInfo(Counter counter) {
        return new StaffDashboardResponse.CounterInfo(
                counter.getId(),
                counter.getCounterNumber(),
                counter.getName(),
                counter.getStatus(),
                counter.getQueueService().getId(),
                counter.getQueueService().getName(),
                counter.getOrganization().getName(),
                counter.getQueueService().getAverageServiceTime()
        );
    }

    private StaffTokenResponse toStaffRow(QueueToken token, Counter counter) {
        return new StaffTokenResponse(
                token.getId(),
                token.getTokenNumber(),
                token.getPriorityType(),
                token.getStatus(),
                token.getCreatedAt(),
                token.getCalledAt(),
                token.getServingAt(),
                token.getCompletedAt(),
                TokenService.waitingMinutes(token, Instant.now()),
                counter != null ? counter.getCounterNumber()
                        : (token.getCounter() == null ? null : token.getCounter().getCounterNumber()),
                token.getUser().getName()
        );
    }
}
