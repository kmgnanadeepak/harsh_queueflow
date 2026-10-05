package com.harsha.queueflow.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.harsha.queueflow.config.PageRequests;
import com.harsha.queueflow.dto.CreateTokenRequest;
import com.harsha.queueflow.dto.PagedResponse;
import com.harsha.queueflow.dto.TokenResponse;
import com.harsha.queueflow.security.QueueFlowPrincipal;
import com.harsha.queueflow.service.TokenService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /** Generate a token. The owner is taken from the JWT - never from the body. */
    @PostMapping
    public ResponseEntity<TokenResponse> generate(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                                  @Valid @RequestBody CreateTokenRequest request) {
        TokenResponse token = tokenService.generate(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(token);
    }

    @GetMapping("/my")
    public ResponseEntity<PagedResponse<TokenResponse>> myTokens(
            @AuthenticationPrincipal QueueFlowPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                tokenService.myTokens(principal.getId(), PageRequests.of(page, size))));
    }

    @GetMapping("/my/current")
    public ResponseEntity<TokenResponse> current(@AuthenticationPrincipal QueueFlowPrincipal principal) {
        TokenResponse token = tokenService.currentToken(principal.getId());
        return token == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(token);
    }

    /**
     * Ownership is validated inside the service against the authenticated user,
     * so USER A can never read USER B's token.
     */
    @GetMapping("/my/{id}")
    public ResponseEntity<TokenResponse> myTokenById(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                                     @PathVariable Long id) {
        return ResponseEntity.ok(tokenService.myTokenById(principal.getId(), id));
    }

    @PostMapping("/my/{id}/cancel")
    public ResponseEntity<TokenResponse> cancel(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                                @PathVariable Long id) {
        return ResponseEntity.ok(tokenService.cancel(principal.getId(), id));
    }

    @GetMapping("/history")
    public ResponseEntity<PagedResponse<TokenResponse>> history(
            @AuthenticationPrincipal QueueFlowPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                tokenService.history(principal.getId(), PageRequests.of(page, size))));
    }
}
