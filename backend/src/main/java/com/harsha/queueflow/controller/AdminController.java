package com.harsha.queueflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.harsha.queueflow.config.PageRequests;
import com.harsha.queueflow.dto.AdminDashboardResponse;
import com.harsha.queueflow.dto.AdminUserRequest;
import com.harsha.queueflow.dto.AnalyticsResponse;
import com.harsha.queueflow.dto.CounterRequest;
import com.harsha.queueflow.dto.CounterResponse;
import com.harsha.queueflow.dto.OrganizationRequest;
import com.harsha.queueflow.dto.PagedResponse;
import com.harsha.queueflow.dto.QueueServiceRequest;
import com.harsha.queueflow.dto.UserResponse;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.security.QueueFlowPrincipal;
import com.harsha.queueflow.service.AnalyticsService;
import com.harsha.queueflow.service.CatalogService;
import com.harsha.queueflow.service.CounterService;
import com.harsha.queueflow.service.NotificationService;
import com.harsha.queueflow.service.OrganizationService;
import com.harsha.queueflow.service.UserAdminService;

import jakarta.validation.Valid;

/**
 * Administration API. Every method requires the ADMIN role (enforced by
 * {@code /api/admin/** -> hasRole("ADMIN")}), including the only path that can
 * create or promote privileged accounts.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserAdminService userAdminService;
    private final OrganizationService organizationService;
    private final CatalogService catalogService;
    private final CounterService counterService;
    private final AnalyticsService analyticsService;
    private final NotificationService notificationService;

    public AdminController(UserAdminService userAdminService,
                           OrganizationService organizationService,
                           CatalogService catalogService,
                           CounterService counterService,
                           AnalyticsService analyticsService,
                           NotificationService notificationService) {
        this.userAdminService = userAdminService;
        this.organizationService = organizationService;
        this.catalogService = catalogService;
        this.counterService = counterService;
        this.analyticsService = analyticsService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    public ResponseEntity<PagedResponse<UserResponse>> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                userAdminService.search(q, role, PageRequests.of(page, size, Sort.by("createdAt").descending()))));
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.create(request));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @Valid @RequestBody AdminUserRequest request) {
        return ResponseEntity.ok(userAdminService.update(id, request));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id,
                                           @AuthenticationPrincipal QueueFlowPrincipal principal) {
        if (principal.getId().equals(id)) {
            throw new com.harsha.queueflow.exception.BadRequestException("You cannot deactivate your own account");
        }
        userAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------- organizations

    @GetMapping("/organizations")
    public ResponseEntity<PagedResponse<OrganizationRequest.Response>> organizations(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                organizationService.search(q, PageRequests.of(page, size, Sort.by("name").ascending()))));
    }

    @PostMapping("/organizations")
    public ResponseEntity<OrganizationRequest.Response> createOrganization(
            @Valid @RequestBody OrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.create(request));
    }

    @PutMapping("/organizations/{id}")
    public ResponseEntity<OrganizationRequest.Response> updateOrganization(
            @PathVariable Long id, @Valid @RequestBody OrganizationRequest request) {
        return ResponseEntity.ok(organizationService.update(id, request));
    }

    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long id) {
        organizationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- services

    @GetMapping("/services")
    public ResponseEntity<PagedResponse<QueueServiceRequest.Response>> services(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                catalogService.search(q, organizationId, PageRequests.of(page, size, Sort.by("name").ascending()))));
    }

    @PostMapping("/services")
    public ResponseEntity<QueueServiceRequest.Response> createService(
            @Valid @RequestBody QueueServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.create(request));
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<QueueServiceRequest.Response> updateService(
            @PathVariable Long id, @Valid @RequestBody QueueServiceRequest request) {
        return ResponseEntity.ok(catalogService.update(id, request));
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        catalogService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- counters

    @GetMapping("/counters")
    public ResponseEntity<PagedResponse<CounterResponse>> counters(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                counterService.search(q, serviceId, staffId, PageRequests.of(page, size))));
    }

    @PostMapping("/counters")
    public ResponseEntity<CounterResponse> createCounter(@Valid @RequestBody CounterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(counterService.create(request));
    }

    @PutMapping("/counters/{id}")
    public ResponseEntity<CounterResponse> updateCounter(@PathVariable Long id,
                                                         @Valid @RequestBody CounterRequest request) {
        return ResponseEntity.ok(counterService.update(id, request));
    }

    @DeleteMapping("/counters/{id}")
    public ResponseEntity<Void> deleteCounter(@PathVariable Long id) {
        counterService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------- dashboards/analytics

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> dashboard() {
        return ResponseEntity.ok(analyticsService.dashboard());
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsResponse> analytics() {
        return ResponseEntity.ok(analyticsService.analytics());
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Integer>> broadcast(@RequestParam String message) {
        return ResponseEntity.ok(Map.of("delivered", userAdminService.broadcast(message)));
    }
}
