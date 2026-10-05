package com.harsha.queueflow.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.harsha.queueflow.dto.AuthResponse;
import com.harsha.queueflow.dto.GoogleAuthStatusResponse;
import com.harsha.queueflow.dto.LoginRequest;
import com.harsha.queueflow.dto.RegisterRequest;
import com.harsha.queueflow.dto.UserResponse;
import com.harsha.queueflow.security.QueueFlowPrincipal;
import com.harsha.queueflow.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public AuthController(AuthService authService, ClientRegistrationRepository clientRegistrationRepository) {
        this.authService = authService;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /** Public self-service registration. Always creates a USER account. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Session probe used by the SPA on reload to restore the authenticated state. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal QueueFlowPrincipal principal) {
        return ResponseEntity.ok(authService.currentUser());
    }

    /**
     * Stateless JWT sessions: the client discards the token. The endpoint exists
     * so the SPA has one explicit logout call to make.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    /** Tells the login page whether Google sign-in is configured on this server. */
    @GetMapping("/google/status")
    public ResponseEntity<GoogleAuthStatusResponse> googleStatus() {
        boolean enabled = clientRegistrationRepository.findByRegistrationId("google") != null;
        return ResponseEntity.ok(authService.googleStatus(enabled));
    }
}
