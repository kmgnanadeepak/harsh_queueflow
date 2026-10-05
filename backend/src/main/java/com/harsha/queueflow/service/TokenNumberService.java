package com.harsha.queueflow.service;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.TokenSequence;
import com.harsha.queueflow.repository.TokenSequenceRepository;

/**
 * Allocates collision-free, human readable token numbers such as {@code GC001}.
 *
 * <p>Numbers are allocated per service from a dedicated sequence table whose row
 * is locked with {@code SELECT ... FOR UPDATE} for the duration of the
 * surrounding transaction. Concurrent requests therefore queue up behind each
 * other and can never receive the same number. A unique constraint on
 * {@code (queue_service_id, token_number)} in the {@code tokens} table remains
 * the final safety net.</p>
 */
@Service
public class TokenNumberService {

    private static final Logger log = LoggerFactory.getLogger(TokenNumberService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int DEFAULT_SEQUENCE_WIDTH = 3;
    private static final int MAX_SEQUENCE = 999_999;

    private final TokenSequenceRepository sequenceRepository;
    private final TransactionTemplate requiresNew;

    public TokenNumberService(TokenSequenceRepository sequenceRepository,
                              PlatformTransactionManager transactionManager) {
        this.sequenceRepository = sequenceRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Must be invoked inside the transaction that persists the new token.
     */
    @Transactional
    public String nextTokenNumber(QueueService service) {
        String prefix = prefixFor(service.getName());
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                TokenSequence sequence = sequenceRepository.findByServiceIdForUpdate(service.getId())
                        .orElseGet(() -> initialiseSequence(service.getId()));
                long next = sequence.next();
                if (next > MAX_SEQUENCE) {
                    throw new IllegalStateException("Token number space exhausted for service " + service.getId());
                }
                sequenceRepository.saveAndFlush(sequence);
                return prefix + String.format(Locale.ROOT, "%0" + DEFAULT_SEQUENCE_WIDTH + "d", next);
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException ex) {
                log.warn("Token sequence contention for service {} (attempt {}/{})", service.getId(), attempt,
                        MAX_ATTEMPTS);
                if (attempt == MAX_ATTEMPTS) {
                    throw ex;
                }
                initialiseSequence(service.getId());
            }
        }
        throw new IllegalStateException("Unable to allocate a token number");
    }

    /**
     * Returns the locked sequence row for the service, creating it on first use.
     *
     * <p>The existence check is done with the locking read only. An additional
     * unlocked read would place a stale copy of the row in the persistence
     * context, and Hibernate then rejects the later locking read with
     * {@code StaleObjectStateException} as soon as a concurrent transaction has
     * bumped the version in between.</p>
     *
     * <p>The insert runs in its own transaction because the current one already
     * holds locks for this service. Concurrent first-time callers can race on the
     * unique {@code queue_service_id} constraint; the loser treats that as
     * success and simply reads the row the winner created.</p>
     */
    private TokenSequence initialiseSequence(Long serviceId) {
        Optional<TokenSequence> existing = sequenceRepository.findByServiceIdForUpdate(serviceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            requiresNew.executeWithoutResult(status ->
                    sequenceRepository.save(new TokenSequence(serviceId, 0L)));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Token sequence for service {} created concurrently", serviceId);
        }
        return sequenceRepository.findByServiceIdForUpdate(serviceId)
                .orElseThrow(() -> new IllegalStateException("Unable to create token sequence for service " + serviceId));
    }

    /**
     * Builds the alphabetic prefix of a token number from the service name:
     * "General Consultation" becomes {@code GC}, "Billing" becomes {@code B}.
     */
    static String prefixFor(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return "T";
        }
        String[] words = serviceName.trim().split("\\s+");
        StringBuilder prefix = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            char first = Character.toUpperCase(word.charAt(0));
            if (Character.isLetterOrDigit(first)) {
                prefix.append(first);
            }
            if (prefix.length() == 2) {
                break;
            }
        }
        return prefix.length() == 0 ? "T" : prefix.toString();
    }
}
