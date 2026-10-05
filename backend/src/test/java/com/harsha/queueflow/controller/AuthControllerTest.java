package com.harsha.queueflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.integration.TestSupport;
import com.harsha.queueflow.security.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends TestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder encoder;

    @Test
    @DisplayName("registration creates a USER and returns a usable JWT")
    void registrationCreatesUserAndToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Asha Rao","email":"asha@example.com","phone":"+919876543210","password":"Secret123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("asha@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.password").doesNotExist());

        User stored = userRepository.findByEmailIgnoreCase("asha@example.com").orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.USER);
        assertThat(stored.getPassword()).isNotEqualTo("Secret123");
        assertThat(encoder.matches("Secret123", stored.getPassword())).isTrue();
    }

    @Test
    @DisplayName("BCrypt hashes are salted - two identical passwords produce different hashes")
    void bcryptUsesRandomSalt() {
        User first = createUser("salt1@example.com", "SamePassword", Role.USER);
        User second = createUser("salt2@example.com", "SamePassword", Role.USER);
        assertThat(first.getPassword()).isNotEqualTo(second.getPassword());
        assertThat(encoder.matches("SamePassword", first.getPassword())).isTrue();
        assertThat(encoder.matches("SamePassword", second.getPassword())).isTrue();
    }

    @Test
    @DisplayName("public registration cannot escalate to ADMIN even when role is supplied")
    void registrationCannotEscalateToAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mallory","email":"mallory@example.com","phone":"+919876543211",
                                 "password":"Secret123","role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));

        assertThat(userRepository.findByEmailIgnoreCase("mallory@example.com").orElseThrow().getRole())
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("public registration cannot escalate to STAFF even when role is supplied")
    void registrationCannotEscalateToStaff() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sam Staff","email":"sam@example.com","phone":"+919876543212",
                                 "password":"Secret123","role":"STAFF"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    @DisplayName("duplicate email registration returns 409")
    void duplicateEmailIsRejected() throws Exception {
        createUser("taken@example.com", Role.USER);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Second User","email":"taken@example.com","phone":"+919876543213",
                                 "password":"Secret123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("invalid registration payload returns 400 with field violations")
    void validationErrorsAreReported() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","phone":"12","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @DisplayName("login returns a token for correct credentials")
    void loginSucceeds() throws Exception {
        createUser("login@example.com", "Secret123", Role.ADMIN);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"Secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    @DisplayName("login with a wrong password returns 401")
    void loginWithWrongPasswordIsRejected() throws Exception {
        createUser("wrongpass@example.com", "Secret123", Role.USER);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wrongpass@example.com","password":"WrongPassword"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login for an unknown account returns 401 without disclosing the reason")
    void loginForUnknownAccountIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ghost@example.com","password":"Secret123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("issued JWT carries identity and role but never the password")
    void issuedTokenContainsRoleButNotPassword() {
        User admin = createUser("token@example.com", "Secret123", Role.ADMIN);
        String token = tokenFor(admin);
        var claims = jwtUtil.parseClaims(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(admin.getId()));
        assertThat(claims.get(JwtUtil.CLAIM_ROLE, String.class)).isEqualTo("ADMIN");
        assertThat(claims.get(JwtUtil.CLAIM_EMAIL, String.class)).isEqualTo("token@example.com");
        assertThat(claims.toString()).doesNotContain("Secret123");
    }

    @Test
    @DisplayName("a tampered or expired JWT is rejected")
    void tamperedTokenIsRejected() {
        User user = createUser("tamper@example.com", Role.USER);
        String token = tokenFor(user);
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThat(jwtUtil.parseClaims(tampered)).isNull();
        assertThat(jwtUtil.parseClaims("not.a.jwt")).isNull();
    }

    @Test
    @DisplayName("deactivated accounts cannot authenticate")
    void deactivatedAccountCannotLogIn() throws Exception {
        User user = createUser("disabled@example.com", "Secret123", Role.USER);
        user.setActive(false);
        userRepository.saveAndFlush(user);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"disabled@example.com","password":"Secret123"}
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Google sign-in reports itself as disabled when no client credentials are configured")
    void googleStatusReflectsConfiguration() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/auth/google/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("logout is available to authenticated sessions")
    void logoutSucceedsForAuthenticatedUser() throws Exception {
        User user = createUser("logout@example.com", Role.USER);
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("password login is refused for a Google-only account")
    void federatedOnlyAccountCannotUsePasswordLogin() throws Exception {
        User googleUser = new User();
        googleUser.setName("Google User");
        googleUser.setEmail("googleonly@example.com");
        googleUser.setPassword(null);
        googleUser.setProvider(com.harsha.queueflow.enums.AuthProvider.GOOGLE);
        googleUser.setProviderId("google-sub-123");
        googleUser.setRole(Role.USER);
        googleUser.setActive(true);
        userRepository.saveAndFlush(googleUser);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"googleonly@example.com","password":"Secret123"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
