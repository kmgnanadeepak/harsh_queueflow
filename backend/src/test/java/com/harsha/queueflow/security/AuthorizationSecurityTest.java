package com.harsha.queueflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.harsha.queueflow.dto.PagedResponse;
import com.harsha.queueflow.dto.TokenResponse;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.Organization;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.integration.TestSupport;

/**
 * End-to-end authorization matrix. These tests are the executable form of
 * QueueFlow's security rules: the frontend is never trusted, so every boundary is
 * enforced by Spring Security plus service level ownership validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationSecurityTest extends TestSupport {

    @Autowired
    private MockMvc mockMvc;

    private User userA;
    private User userB;
    private User staff;
    private User admin;
    private Organization organization;
    private QueueService service;
    private Counter counter;
    private String tokenA;
    private String tokenB;
    private String staffToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userA = createUser("alice@example.com", Role.USER);
        userB = createUser("bob@example.com", Role.USER);
        staff = createUser("clerk@example.com", Role.STAFF);
        admin = createUser("root@example.com", Role.ADMIN);

        organization = createOrganization("Security Test Hospital");
        service = createService(organization, "General Consultation", 10);
        counter = createCounter(organization, service, "C1", staff);

        tokenA = tokenFor(userA);
        tokenB = tokenFor(userB);
        staffToken = tokenFor(staff);
        adminToken = tokenFor(admin);
    }

    /** Generates a token exactly the way the React client does. */
    private TokenResponse generateToken(String jwt) throws Exception {
        String body = mockMvc.perform(post("/api/tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":%d,"priority":"NORMAL"}
                                """.formatted(service.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TokenResponse.class);
    }

    @Nested
    @DisplayName("role boundaries")
    class RoleBoundaries {

        @Test
        @DisplayName("USER cannot reach the admin API")
        void userCannotAccessAdminApi() throws Exception {
            mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/analytics").header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER cannot reach the staff API")
        void userCannotAccessStaffApi() throws Exception {
            mockMvc.perform(get("/api/staff/dashboard").header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/staff/tokens/call-next")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("STAFF cannot reach the admin API")
        void staffCannotAccessAdminApi() throws Exception {
            mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/counters").header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN can reach the admin API")
        void adminCanAccessAdminApi() throws Exception {
            mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
            mockMvc.perform(get("/api/admin/analytics").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overview").exists());
        }

        @Test
        @DisplayName("STAFF cannot create queue tokens - only USER can")
        void onlyUsersCanTakeAToken() throws Exception {
            String body = """
                    {"serviceId":%d}
                    """.formatted(service.getId());

            mockMvc.perform(post("/api/tokens")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/tokens")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("anonymous requests are rejected with 401")
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/tokens/my")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/staff/dashboard")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a tampered bearer token is rejected")
        void tamperedTokenIsRejected() throws Exception {
            String tampered = tokenA.substring(0, tokenA.length() - 4) + "aaaa";
            mockMvc.perform(get("/api/tokens/my").header("Authorization", "Bearer " + tampered))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("the public catalogue stays reachable without a token")
        void publicEndpointsAreOpen() throws Exception {
            mockMvc.perform(get("/api/services/active")).andExpect(status().isOk());
            mockMvc.perform(get("/api/organizations/active")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("only ADMIN can create a privileged account")
        void onlyAdminCanPromote() throws Exception {
            String body = """
                    {"name":"New Staff","email":"promoted@example.com","phone":"+919876543210",
                     "password":"Secret123","role":"STAFF"}
                    """;

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("STAFF"));

            assertThat(userRepository.findByEmailIgnoreCase("promoted@example.com").orElseThrow().getRole())
                    .isEqualTo(Role.STAFF);
        }
    }

    @Nested
    @DisplayName("token ownership")
    class TokenOwnership {

        @Test
        @DisplayName("USER A cannot read USER B's token")
        void userACannotReadUserBToken() throws Exception {
            TokenResponse tokenOfB = generateToken(tokenB);

            mockMvc.perform(get("/api/tokens/my/" + tokenOfB.id())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER A cannot cancel USER B's token")
        void userACannotCancelUserBToken() throws Exception {
            TokenResponse tokenOfB = generateToken(tokenB);

            mockMvc.perform(post("/api/tokens/my/" + tokenOfB.id() + "/cancel")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden());

            // The rejection must not have changed anything.
            mockMvc.perform(get("/api/tokens/my/" + tokenOfB.id())
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(TokenStatus.WAITING.name()));
        }

        @Test
        @DisplayName("list endpoints only ever expose the caller's own tokens")
        void listEndpointsAreScopedToTheCaller() throws Exception {
            generateToken(tokenA);
            generateToken(tokenB);

            String mine = mockMvc.perform(get("/api/tokens/my").header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readValue(mine, PagedResponse.class).totalElements()).isEqualTo(1);
            assertThat(mine).contains("GC001").doesNotContain("GC002");

            String history = mockMvc.perform(get("/api/tokens/history")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readValue(history, PagedResponse.class).totalElements()).isZero();
        }

        @Test
        @DisplayName("the current-token endpoint never leaks another user's token")
        void currentTokenIsScopedToTheCaller() throws Exception {
            generateToken(tokenB);

            mockMvc.perform(get("/api/tokens/my/current").header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/tokens/my/current").header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a client supplied userId cannot redirect a token to another owner")
        void bodyCannotForgeOwnership() throws Exception {
            mockMvc.perform(post("/api/tokens")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"serviceId":%d,"userId":%d}
                                    """.formatted(service.getId(), userB.getId())))
                    .andExpect(status().isCreated());

            assertThat(tokenRepository.findAll()).hasSize(1);
            assertThat(tokenRepository.findAll().get(0).getUser().getId()).isEqualTo(userA.getId());
        }
    }

    @Nested
    @DisplayName("counter operations")
    class CounterOperations {

        @Test
        @DisplayName("STAFF may operate their own counter")
        void staffCanOperateOwnCounter() throws Exception {
            mockMvc.perform(get("/api/staff/dashboard").header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.counter.counterNumber").value("C1"));
        }

        @Test
        @DisplayName("STAFF may not operate a counter assigned to somebody else")
        void staffIsBoundToTheirCounter() throws Exception {
            TokenResponse tokenOfA = generateToken(tokenA);
            mockMvc.perform(post("/api/staff/tokens/call-next")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"counterId":%d,"confirmCounter":true}
                                    """.formatted(counter.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(TokenStatus.CALLED.name()));

            User otherStaff = createUser("other.clerk@example.com", Role.STAFF);
            mockMvc.perform(post("/api/staff/tokens/" + tokenOfA.id() + "/start")
                            .header("Authorization", "Bearer " + tokenFor(otherStaff)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("STAFF cannot pick a counter outside their assignment")
        void staffCannotCallNextOnForeignCounter() throws Exception {
            Counter otherCounter = createCounter(organization, service, "C9", null);
            generateToken(tokenA);

            mockMvc.perform(post("/api/staff/tokens/call-next")
                            .header("Authorization", "Bearer " + staffToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"counterId":%d,"confirmCounter":true}
                                    """.formatted(otherCounter.getId())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN may operate any counter")
        void adminCanUseStaffApi() throws Exception {
            generateToken(tokenA);
            mockMvc.perform(post("/api/staff/tokens/call-next")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"counterId":%d,"confirmCounter":true}
                                    """.formatted(counter.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.counterNumber").value("C1"));
        }

        @Test
        @DisplayName("ADMIN cannot deactivate their own account")
        void adminCannotDeleteSelf() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/admin/users/" + admin.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());
        }
    }
}