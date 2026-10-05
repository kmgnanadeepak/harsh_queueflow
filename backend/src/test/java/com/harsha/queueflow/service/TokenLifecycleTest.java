package com.harsha.queueflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.harsha.queueflow.dto.CreateTokenRequest;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.Organization;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.NotificationType;
import com.harsha.queueflow.enums.PriorityType;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.integration.TestSupport;
import com.harsha.queueflow.repository.QueueTokenRepository;

/**
 * Token lifecycle: allocation under concurrency, counter operations and the
 * legal state machine.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenLifecycleTest extends TestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StaffQueueService staffQueueService;

    @Autowired
    private QueueTokenRepository tokenRepo;

    private Organization organization;
    private QueueService service;
    private Counter counter;
    private User staff;
    private User customer;
    private String staffToken;

    @BeforeEach
    void setUp() {
        organization = createOrganization("Lifecycle Hospital");
        service = createService(organization, "Billing", 5);
        staff = createUser("lifecycle.staff@example.com", Role.STAFF);
        counter = createCounter(organization, service, "B1", staff, CounterStatus.AVAILABLE);
        customer = createUser("lifecycle.user@example.com", Role.USER);
        staffToken = tokenFor(staff);
    }

    @Test
    @DisplayName("concurrent token generation never produces a duplicate number")
    void concurrentGenerationProducesUniqueNumbers() throws Exception {
        int customers = 8;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            users.add(createUser("bulk" + i + "@example.com", Role.USER));
        }

        ExecutorService executor = Executors.newFixedThreadPool(customers);
        try {
            List<Callable<String>> tasks = users.stream()
                    .map(user -> (Callable<String>) () -> tokenService
                            .generate(user.getId(), new CreateTokenRequest(service.getId(),
                                    PriorityType.NORMAL, null))
                            .tokenNumber())
                    .toList();
            List<Future<String>> futures = executor.invokeAll(tasks, 60, TimeUnit.SECONDS);
            List<String> numbers = new ArrayList<>();
            for (Future<String> future : futures) {
                numbers.add(future.get());
            }

            assertThat(numbers).doesNotContainNull();
            assertThat(Set.copyOf(numbers)).hasSize(customers);
            assertThat(numbers).allMatch(number -> number.matches("B\\d{3}"));
        } finally {
            executor.shutdownNow();
        }

        assertThat(tokenRepo.findByQueueServiceIdAndStatusInOrderByCreatedAtAsc(service.getId(),
                List.of(TokenStatus.WAITING))).hasSize(customers);
    }

    @Test
    @DisplayName("staff can call, start, complete a token and the counter is released")
    void fullHappyPath() throws Exception {
        var token = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));

        mockMvc.perform(post("/api/staff/tokens/call-next")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"counterId":%d,"confirmCounter":true}
                                """.formatted(counter.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"))
                .andExpect(jsonPath("$.counterNumber").value("B1"));

        assertThat(counterRepository.findById(counter.getId()).orElseThrow().getStatus())
                .isEqualTo(CounterStatus.BUSY);

        mockMvc.perform(post("/api/staff/tokens/" + token.id() + "/start")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SERVING"));

        mockMvc.perform(post("/api/staff/tokens/" + token.id() + "/complete")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(counterRepository.findById(counter.getId()).orElseThrow().getStatus())
                .isEqualTo(CounterStatus.AVAILABLE);
    }

    @Test
    @DisplayName("call-next serves the highest priority waiting token first")
    void callNextHonoursPriority() {
        User normalUser = createUser("normal.priority@example.com", Role.USER);
        User emergencyUser = createUser("emergency.priority@example.com", Role.USER);

        var normal = tokenService.generate(normalUser.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        var emergency = tokenService.generate(emergencyUser.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.EMERGENCY, null));

        var called = staffQueueService.callNext(staff.getId(), Role.STAFF,
                new com.harsha.queueflow.dto.CallNextRequest(counter.getId(), service.getId(), true));

        assertThat(called.id()).isEqualTo(emergency.id());
        assertThat(called.status()).isEqualTo(TokenStatus.CALLED);
        assertThat(tokenRepo.findById(normal.id()).orElseThrow().getStatus()).isEqualTo(TokenStatus.WAITING);
    }

    @Test
    @DisplayName("call-next fails cleanly when the queue is empty")
    void callNextOnEmptyQueue() {
        assertThatThrownBy(() -> staffQueueService.callNext(staff.getId(), Role.STAFF,
                        new com.harsha.queueflow.dto.CallNextRequest(counter.getId(), service.getId(), true)))
                .isInstanceOf(com.harsha.queueflow.exception.BadRequestException.class)
                .hasMessageContaining("queue is empty");
    }

    @Test
    @DisplayName("a token cannot be started before it is called")
    void cannotStartUncalledToken() {
        var token = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));

        assertThatThrownBy(() -> staffQueueService.start(staff.getId(), Role.STAFF, token.id()))
                .isInstanceOf(com.harsha.queueflow.exception.BadRequestException.class);
    }

    @Test
    @DisplayName("a completed token cannot be completed twice")
    void cannotCompleteTwice() {
        var token = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        staffQueueService.callNext(staff.getId(), Role.STAFF,
                new com.harsha.queueflow.dto.CallNextRequest(counter.getId(), service.getId(), true));
        staffQueueService.start(staff.getId(), Role.STAFF, token.id());
        staffQueueService.complete(staff.getId(), Role.STAFF, token.id());

        assertThatThrownBy(() -> staffQueueService.complete(staff.getId(), Role.STAFF, token.id()))
                .isInstanceOf(com.harsha.queueflow.exception.BadRequestException.class);
    }

    @Test
    @DisplayName("a skipped token leaves the waiting queue")
    void skipRemovesTokenFromQueue() {
        var token = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));

        var skipped = staffQueueService.skip(staff.getId(), Role.STAFF, token.id());
        assertThat(skipped.status()).isEqualTo(TokenStatus.SKIPPED);
        assertThat(queueEngineWaitingCount()).isZero();
    }

    @Test
    @DisplayName("token generation creates a notification for the owner")
    void generationNotifiesOwner() {
        var token = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));

        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(customer.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .anyMatch(n -> n.getType() == NotificationType.TOKEN_GENERATED
                        && n.getMessage().contains(token.tokenNumber()));
    }

    @Test
    @DisplayName("notifications are scoped to their owner")
    void notificationsArePrivate() throws Exception {
        tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        User other = createUser("nosy@example.com", Role.USER);

        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/notifications").header("Authorization", "Bearer " + tokenFor(other)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(customer.getEmail());
        assertThat(tokenService.currentToken(other.getId())).isNull();
    }

    @Test
    @DisplayName("a user can cancel their own waiting token but not a served one")
    void cancelRules() {
        var first = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        assertThat(tokenService.cancel(customer.getId(), first.id()).status()).isEqualTo(TokenStatus.CANCELLED);

        var second = tokenService.generate(customer.getId(),
                new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        staffQueueService.callNext(staff.getId(), Role.STAFF,
                new com.harsha.queueflow.dto.CallNextRequest(counter.getId(), service.getId(), true));
        assertThatThrownBy(() -> tokenService.cancel(customer.getId(), second.id()))
                .isInstanceOf(com.harsha.queueflow.exception.BadRequestException.class);
    }

    @Test
    @DisplayName("a token for an inactive service is refused")
    void inactiveServiceRejectsTokens() {
        service.setActive(false);
        serviceRepository.saveAndFlush(service);

        assertThatThrownBy(() -> tokenService.generate(customer.getId(),
                        new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null)))
                .isInstanceOf(com.harsha.queueflow.exception.BadRequestException.class)
                .hasMessageContaining("not currently accepting");
    }

    @Test
    @DisplayName("every stored token has a unique number within its service")
    void uniqueConstraintHolds() {
        for (int i = 0; i < 5; i++) {
            User user = createUser("seq" + i + "@example.com", Role.USER);
            tokenService.generate(user.getId(),
                    new CreateTokenRequest(service.getId(), PriorityType.NORMAL, null));
        }
        List<String> numbers = tokenRepo.findAll().stream()
                .map(QueueToken::getTokenNumber)
                .collect(Collectors.toList());
        assertThat(Set.copyOf(numbers)).hasSize(numbers.size());
    }

    private long queueEngineWaitingCount() {
        return tokenRepo.findByQueueServiceIdAndStatusOrderByCreatedAtAsc(service.getId(), TokenStatus.WAITING)
                .size();
    }
}
