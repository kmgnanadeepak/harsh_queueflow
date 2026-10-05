package com.harsha.queueflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.harsha.queueflow.dto.CreateTokenRequest;
import com.harsha.queueflow.entity.Organization;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.PriorityType;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.exception.BadRequestException;
import com.harsha.queueflow.integration.TestSupport;

/**
 * The queueing rules that users actually experience: who is served next, how long
 * the wait is, and how token numbers are handed out.
 */
@SpringBootTest
class QueueEngineTest extends TestSupport {

    @Autowired
    private QueueEngine queueEngine;

    @Autowired
    private TokenService tokenService;

    private User customer;
    private Organization organization;
    private QueueService service;

    @BeforeEach
    void setUp() {
        customer = createUser("engine@example.com", Role.USER);
        organization = createOrganization("Engine Hospital");
        service = createService(organization, "General Consultation", 10);
    }

    @Test
    @DisplayName("priority decides the order: EMERGENCY, then ELDERLY, then NORMAL")
    void higherPriorityIsServedFirst() {
        QueueToken normal = waiting("A001", PriorityType.NORMAL, 0);
        QueueToken emergency = waiting("A002", PriorityType.EMERGENCY, 1);
        QueueToken elderly = waiting("A003", PriorityType.ELDERLY, 2);
        QueueToken disabled = waiting("A004", PriorityType.DISABLED, 3);

        assertThat(queueEngine.orderedWaiting(service))
                .extracting(QueueToken::getTokenNumber)
                .containsExactly(emergency.getTokenNumber(), elderly.getTokenNumber(),
                        disabled.getTokenNumber(), normal.getTokenNumber());
    }

    @Test
    @DisplayName("ageing lets a long-waiting NORMAL overtake a fresh ELDERLY token")
    void ageingProtectsNormalCustomers() {
        // 40 minutes of waiting = 2 ageing steps = rank 3 -> 1.
        QueueToken waitingNormal = waiting("A001", PriorityType.NORMAL, 40 * 60);
        QueueToken freshElderly = waiting("A002", PriorityType.ELDERLY, 1);

        assertThat(queueEngine.orderedWaiting(service))
                .extracting(QueueToken::getTokenNumber)
                .containsExactly(waitingNormal.getTokenNumber(), freshElderly.getTokenNumber());
    }

    @Test
    @DisplayName("ageing is capped, so NORMAL never overtakes EMERGENCY")
    void ageingIsCappedForEmergency() {
        QueueToken veryOldNormal = waiting("A001", PriorityType.NORMAL, 600 * 60);
        QueueToken emergency = waiting("A002", PriorityType.EMERGENCY, 1);

        assertThat(queueEngine.orderedWaiting(service))
                .extracting(QueueToken::getTokenNumber)
                .containsExactly(emergency.getTokenNumber(), veryOldNormal.getTokenNumber());
    }

    @Test
    @DisplayName("a freshly arrived token gets no ageing boost")
    void freshTokensHaveNoBoost() {
        QueueToken freshElderly = waiting("A001", PriorityType.ELDERLY, 1);
        assertThat(queueEngine.effectiveRank(freshElderly))
                .isEqualTo(freshElderly.getPriorityType().baseRank());
    }

    @Test
    @DisplayName("equal priority falls back to first-come-first-served")
    void equalPriorityUsesArrivalOrder() {
        QueueToken first = waiting("A001", PriorityType.NORMAL, 10);
        QueueToken second = waiting("A002", PriorityType.NORMAL, 1);

        assertThat(queueEngine.orderedWaiting(service))
                .extracting(QueueToken::getTokenNumber)
                .containsExactly(first.getTokenNumber(), second.getTokenNumber());
    }

    @Test
    @DisplayName("estimated waiting time divides by the number of active counters")
    void waitingTimeUsesActiveCounters() {
        createCounter(organization, service, "C1", null, CounterStatus.AVAILABLE);
        createCounter(organization, service, "C2", null, CounterStatus.BUSY);

        waiting("A001", PriorityType.NORMAL, 0);
        waiting("A002", PriorityType.NORMAL, 1);
        waiting("A003", PriorityType.NORMAL, 2);
        waiting("A004", PriorityType.NORMAL, 3);

        QueueEngine.QueueSnapshot snapshot = queueEngine.snapshot(service);
        assertThat(snapshot.activeCounters()).isEqualTo(2);
        assertThat(snapshot.ordered()).hasSize(4);
        assertThat(snapshot.ordered().get(0).position()).isEqualTo(1);
        assertThat(snapshot.ordered().get(0).peopleAhead()).isZero();
        assertThat(snapshot.ordered().get(0).estimatedWaitingMinutes()).isZero();
        // 3 people ahead * 10 min average / 2 active counters = 15 min
        assertThat(snapshot.ordered().get(3).peopleAhead()).isEqualTo(3);
        assertThat(snapshot.ordered().get(3).estimatedWaitingMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("offline counters are excluded from the estimate")
    void offlineCountersAreIgnored() {
        createCounter(organization, service, "C1", null, CounterStatus.AVAILABLE);
        createCounter(organization, service, "C2", null, CounterStatus.OFFLINE);

        waiting("A001", PriorityType.NORMAL, 0);
        waiting("A002", PriorityType.NORMAL, 1);

        QueueEngine.QueueSnapshot snapshot = queueEngine.snapshot(service);
        assertThat(snapshot.activeCounters()).isEqualTo(1);
        assertThat(snapshot.ordered().get(1).estimatedWaitingMinutes()).isEqualTo(10);
    }

    @Test
    @DisplayName("the estimate falls back to a serial sum when no counter is active")
    void noActiveCountersFallsBackToSerialEstimate() {
        createCounter(organization, service, "C1", null, CounterStatus.OFFLINE);
        waiting("A001", PriorityType.NORMAL, 0);
        waiting("A002", PriorityType.NORMAL, 1);
        waiting("A003", PriorityType.NORMAL, 2);

        QueueEngine.QueueSnapshot snapshot = queueEngine.snapshot(service);
        assertThat(snapshot.activeCounters()).isZero();
        assertThat(snapshot.ordered().get(2).estimatedWaitingMinutes()).isEqualTo(20);
    }

    @Test
    @DisplayName("estimateWaitingMinutes is safe for every input combination")
    void estimateIsMathematicallySafe() {
        assertThat(QueueEngine.estimateWaitingMinutes(0, 10, 0)).isZero();
        assertThat(QueueEngine.estimateWaitingMinutes(3, 10, 0)).isEqualTo(30);
        assertThat(QueueEngine.estimateWaitingMinutes(3, 10, 2)).isEqualTo(15);
        assertThat(QueueEngine.estimateWaitingMinutes(1, 0, 0)).isEqualTo(1);
        assertThat(QueueEngine.estimateWaitingMinutes(1, 5, -3)).isEqualTo(5);
        assertThat(QueueEngine.estimateWaitingMinutes(1, 5, 7)).isEqualTo(1);
    }

    @Test
    @DisplayName("token numbers are prefixed from the service name and increase sequentially")
    void tokenNumbersAreSequential() {
        var first = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        tokenService.cancel(customer.getId(), first.id());

        var second = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));

        assertThat(first.tokenNumber()).isEqualTo("GC001");
        assertThat(second.tokenNumber()).isEqualTo("GC002");
    }

    @Test
    @DisplayName("a user cannot hold two active tokens for the same service")
    void duplicateActiveTokenIsRejected() {
        tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));

        assertThatThrownBy(() -> tokenService.generate(customer.getId(),
                        new CreateTokenRequest(service.getId(), PriorityType.ELDERLY, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already hold an active token");
    }

    @Test
    @DisplayName("a freshly generated token reports its own position and zero wait")
    void generatedTokenReportsPosition() {
        createCounter(organization, service, "C1", null, CounterStatus.AVAILABLE);

        var first = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));

        assertThat(first.status()).isEqualTo(TokenStatus.WAITING);
        assertThat(first.position()).isEqualTo(1);
        assertThat(first.peopleAhead()).isZero();
        assertThat(first.estimatedWaitingMinutes()).isZero();
        assertThat(first.tokenNumber()).isEqualTo("GC001");
    }

    @Test
    @DisplayName("a cancelled token no longer occupies a queue position")
    void cancelledTokenLeavesTheQueue() {
        createCounter(organization, service, "C1", null, CounterStatus.AVAILABLE);
        var first = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        tokenService.cancel(customer.getId(), first.id());

        var second = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));

        assertThat(queueEngine.orderedWaiting(service))
                .extracting(QueueToken::getTokenNumber)
                .containsExactly(second.tokenNumber());
        assertThat(second.position()).isEqualTo(1);
    }

    @Test
    @DisplayName("a token for an inactive service is refused")
    void inactiveServiceRejectsTokens() {
        service.setActive(false);
        serviceRepository.saveAndFlush(service);

        assertThatThrownBy(() -> tokenService.generate(customer.getId(),
                        new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not currently accepting");
    }

    @Test
    @DisplayName("position is reported per service, not globally")
    void positionsAreScopedPerService() {
        createCounter(organization, service, "C1", null, CounterStatus.AVAILABLE);
        QueueService other = createService(organization, "Pharmacy", 5);

        tokenService.generate(customer.getId(), new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        waiting("PH001", PriorityType.NORMAL, 0, other, customer);

        assertThat(queueEngine.orderedWaiting(other)).hasSize(1);
        assertThat(queueEngine.snapshot(other).ordered().get(0).position()).isEqualTo(1);
        assertThat(queueEngine.snapshot(service).ordered().get(0).position()).isEqualTo(1);
    }

    private QueueToken waiting(String number, PriorityType priority, int createdSecondsAgo) {
        return waiting(number, priority, createdSecondsAgo, service, customer);
    }

    private QueueToken waiting(String number, PriorityType priority, int createdSecondsAgo,
                               QueueService queueService, User owner) {
        QueueToken token = new QueueToken();
        token.setTokenNumber(number);
        token.setUser(owner);
        token.setQueueService(queueService);
        token.setPriorityType(priority);
        token.setStatus(TokenStatus.WAITING);
        token.setCreatedAt(Instant.now().minusSeconds(createdSecondsAgo));
        return tokenRepository.saveAndFlush(token);
    }

    @Test
    @DisplayName("ordered waiting lists exclude tokens that already left the queue")
    void onlyWaitingTokensAreOrdered() {
        QueueToken served = waiting("A001", PriorityType.NORMAL, 0);
        served.setStatus(TokenStatus.SERVING);
        tokenRepository.saveAndFlush(served);
        waiting("A002", PriorityType.NORMAL, 1);

        List<QueueToken> ordered = queueEngine.orderedWaiting(service);
        assertThat(ordered).extracting(QueueToken::getTokenNumber).containsExactly("A002");
    }
}