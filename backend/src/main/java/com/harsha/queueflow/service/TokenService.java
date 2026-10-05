package com.harsha.queueflow.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.CreateTokenRequest;
import com.harsha.queueflow.dto.QueueTokenView;
import com.harsha.queueflow.dto.TokenResponse;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.NotificationType;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.exception.BadRequestException;
import com.harsha.queueflow.exception.ForbiddenOperationException;
import com.harsha.queueflow.exception.NotFoundException;
import com.harsha.queueflow.repository.QueueTokenRepository;
import com.harsha.queueflow.repository.UserRepository;

/**
 * User facing token operations.
 *
 * <p>Every read is filtered by the authenticated user id taken from the JWT -
 * there is deliberately no endpoint that returns "all tokens" to a normal USER,
 * and no request body can influence ownership.</p>
 */
@Service
public class TokenService {

    private final QueueTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final CatalogService catalogService;
    private final QueueEngine queueEngine;
    private final TokenNumberService tokenNumberService;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;

    public TokenService(QueueTokenRepository tokenRepository,
                        UserRepository userRepository,
                        CatalogService catalogService,
                        QueueEngine queueEngine,
                        TokenNumberService tokenNumberService,
                        NotificationService notificationService,
                        RealtimePublisher realtimePublisher) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.catalogService = catalogService;
        this.queueEngine = queueEngine;
        this.tokenNumberService = tokenNumberService;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
    }

    /**
     * Generates a token for the authenticated user. The token number is allocated
     * under a row lock, so simultaneous requests can never collide.
     */
    @Transactional
    public TokenResponse generate(Long userId, CreateTokenRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
        QueueService service = catalogService.requireActive(request.serviceId());

        long openTokens = tokenRepository
                .findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(token -> token.getStatus().isActiveInQueue())
                .filter(token -> token.getQueueService().getId().equals(service.getId()))
                .count();
        if (openTokens > 0) {
            throw new BadRequestException("You already hold an active token for this service");
        }

        QueueToken token = new QueueToken();
        token.setUser(user);
        token.setQueueService(service);
        token.setPriorityType(request.priorityOrDefault());
        token.setStatus(TokenStatus.WAITING);
        token.setTokenNumber(tokenNumberService.nextTokenNumber(service));
        QueueToken saved = tokenRepository.saveAndFlush(token);

        QueueTokenView view = queueEngine.describe(saved);
        notificationService.notify(user, saved, NotificationType.TOKEN_GENERATED,
                "Token " + saved.getTokenNumber() + " generated for " + service.getName()
                        + (view.peopleAhead() != null && view.peopleAhead() > 0
                        ? ". " + view.peopleAhead() + " people ahead of you." : "."));
        realtimePublisher.publishTokenCreated(view);
        return toResponse(view);
    }

    @Transactional(readOnly = true)
    public Page<TokenResponse> myTokens(Long userId, Pageable pageable) {
        return tokenRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::describe);
    }

    @Transactional(readOnly = true)
    public TokenResponse myTokenById(Long userId, Long tokenId) {
        QueueToken token = tokenRepository.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new ForbiddenOperationException("You may only access your own tokens"));
        return describe(token);
    }

    /** The token the user should care about right now, if any. */
    @Transactional(readOnly = true)
    public TokenResponse currentToken(Long userId) {
        return tokenRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId,
                        List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.SERVING))
                .map(this::describe)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<TokenResponse> history(Long userId, Pageable pageable) {
        List<TokenStatus> terminal = List.of(TokenStatus.COMPLETED, TokenStatus.SKIPPED, TokenStatus.CANCELLED);
        return tokenRepository.findHistory(userId, terminal, pageable).map(this::describe);
    }

    /** Users may only cancel their own WAITING token; the other transitions are staff operations. */
    @Transactional
    public TokenResponse cancel(Long userId, Long tokenId) {
        QueueToken token = tokenRepository.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new ForbiddenOperationException("You may only modify your own tokens"));
        if (token.getStatus() != TokenStatus.WAITING) {
            throw new BadRequestException("Only a waiting token can be cancelled");
        }
        token.setStatus(TokenStatus.CANCELLED);
        QueueToken saved = tokenRepository.saveAndFlush(token);
        QueueTokenView view = queueEngine.describe(saved);
        notificationService.notify(token.getUser(), saved, NotificationType.GENERAL,
                "Token " + saved.getTokenNumber() + " was cancelled.");
        realtimePublisher.publishTokenStatusChanged(view);
        return toResponse(view);
    }

    private TokenResponse describe(QueueToken token) {
        return toResponse(queueEngine.describe(token));
    }

    static TokenResponse toResponse(QueueTokenView view) {
        return new TokenResponse(
                view.id(),
                view.tokenNumber(),
                view.priority(),
                view.status(),
                view.serviceId(),
                view.serviceName(),
                view.organizationName(),
                view.averageServiceTime(),
                view.counterId(),
                view.counterNumber(),
                view.position() == null ? null : (view.position() > 0 ? view.position() : null),
                view.peopleAhead() == null ? null : (view.peopleAhead() > 0 ? view.peopleAhead() : 0),
                view.status() == TokenStatus.WAITING ? view.estimatedWaitingMinutes() : 0,
                view.activeCounters(),
                view.createdAt(),
                view.calledAt(),
                view.servingAt(),
                view.completedAt()
        );
    }

    static long waitingMinutes(QueueToken token, Instant now) {
        Instant reference = token.getCreatedAt();
        return Math.max(0, Duration.between(reference, now).toMinutes());
    }
}
