package com.harsha.queueflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.harsha.queueflow.dto.CallNextRequest;
import com.harsha.queueflow.dto.CounterResponse;
import com.harsha.queueflow.dto.StaffDashboardResponse;
import com.harsha.queueflow.dto.StaffTokenResponse;
import com.harsha.queueflow.dto.TokenResponse;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.security.QueueFlowPrincipal;
import com.harsha.queueflow.service.CounterService;
import com.harsha.queueflow.service.StaffQueueService;

import jakarta.validation.Valid;

/**
 * Counter operations. The whole {@code /api/staff/**} tree is restricted to
 * STAFF and ADMIN by Spring Security; individual actions additionally verify
 * that the caller is assigned to the counter being operated.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffQueueService staffQueueService;
    private final CounterService counterService;

    public StaffController(StaffQueueService staffQueueService, CounterService counterService) {
        this.staffQueueService = staffQueueService;
        this.counterService = counterService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<StaffDashboardResponse> dashboard(
            @AuthenticationPrincipal QueueFlowPrincipal principal,
            @RequestParam(required = false) Long counterId) {
        return ResponseEntity.ok(
                staffQueueService.dashboard(principal.getId(), principal.getRole(), counterId));
    }

    @GetMapping("/queue")
    public ResponseEntity<List<StaffTokenResponse>> queue(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                                          @RequestParam(required = false) Long counterId) {
        return ResponseEntity.ok(staffQueueService.dashboard(principal.getId(), principal.getRole(), counterId)
                .waitingQueue());
    }

    @GetMapping("/counters")
    public ResponseEntity<List<CounterResponse>> counters(@AuthenticationPrincipal QueueFlowPrincipal principal) {
        return ResponseEntity.ok(counterService.listForStaff(principal.getId()));
    }

    @PostMapping("/tokens/call-next")
    public ResponseEntity<TokenResponse> callNext(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                                  @Valid @RequestBody CallNextRequest request) {
        return ResponseEntity.ok(staffQueueService.callNext(principal.getId(), principal.getRole(), request));
    }

    @PostMapping("/tokens/{id}/start")
    public ResponseEntity<TokenResponse> start(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                               @PathVariable Long id) {
        return ResponseEntity.ok(staffQueueService.start(principal.getId(), principal.getRole(), id));
    }

    @PostMapping("/tokens/{id}/complete")
    public ResponseEntity<TokenResponse> complete(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                                  @PathVariable Long id) {
        return ResponseEntity.ok(staffQueueService.complete(principal.getId(), principal.getRole(), id));
    }

    @PostMapping("/tokens/{id}/skip")
    public ResponseEntity<TokenResponse> skip(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                              @PathVariable Long id) {
        return ResponseEntity.ok(staffQueueService.skip(principal.getId(), principal.getRole(), id));
    }

    @PostMapping("/counters/{id}/status")
    public ResponseEntity<CounterStatus> setCounterStatus(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                                          @PathVariable Long id,
                                                          @RequestParam CounterStatus status) {
        return ResponseEntity.ok(staffQueueService.setCounterStatus(principal.getId(), principal.getRole(), id, status));
    }
}
