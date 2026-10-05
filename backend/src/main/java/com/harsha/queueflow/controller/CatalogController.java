package com.harsha.queueflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.harsha.queueflow.config.PageRequests;
import com.harsha.queueflow.dto.CounterResponse;
import com.harsha.queueflow.dto.OrganizationRequest;
import com.harsha.queueflow.dto.QueueServiceRequest;
import com.harsha.queueflow.dto.QueueStatusResponse;
import com.harsha.queueflow.service.CatalogService;
import com.harsha.queueflow.service.CounterService;
import com.harsha.queueflow.service.OrganizationService;
import com.harsha.queueflow.service.QueueStatusService;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final OrganizationService organizationService;
    private final CatalogService catalogService;
    private final CounterService counterService;
    private final QueueStatusService queueStatusService;

    public CatalogController(OrganizationService organizationService,
                             CatalogService catalogService,
                             CounterService counterService,
                             QueueStatusService queueStatusService) {
        this.organizationService = organizationService;
        this.catalogService = catalogService;
        this.counterService = counterService;
        this.queueStatusService = queueStatusService;
    }

    // --------------------------------------------------------- organizations

    @GetMapping("/organizations/active")
    public ResponseEntity<List<OrganizationRequest.Response>> activeOrganizations() {
        return ResponseEntity.ok(organizationService.listActive());
    }

    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationRequest.Response>> organizations() {
        return ResponseEntity.ok(organizationService.listActive());
    }

    // -------------------------------------------------------------- services

    @GetMapping("/services/active")
    public ResponseEntity<List<QueueServiceRequest.Response>> activeServices() {
        return ResponseEntity.ok(catalogService.listActive());
    }

    @GetMapping("/services")
    public ResponseEntity<List<QueueServiceRequest.Response>> services(
            @RequestParam(required = false) Long organizationId) {
        return ResponseEntity.ok(organizationId == null
                ? catalogService.listActive()
                : catalogService.listByOrganization(organizationId));
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<QueueServiceRequest.Response> service(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.describe(id));
    }

    @GetMapping("/services/{id}/counters")
    public ResponseEntity<List<CounterResponse>> counters(@PathVariable Long id) {
        return ResponseEntity.ok(counterService.listForService(id));
    }

    // ----------------------------------------------------------------- queue

    /** Ordered waiting list for a service (aggregate queue state). */
    @GetMapping("/queue/{serviceId}")
    public ResponseEntity<QueueStatusResponse> queue(@PathVariable Long serviceId) {
        return ResponseEntity.ok(queueStatusService.status(serviceId));
    }

    /** Compact status summary used for polling fallbacks. */
    @GetMapping("/queue/{serviceId}/status")
    public ResponseEntity<Map<String, Object>> queueStatus(@PathVariable Long serviceId) {
        QueueStatusResponse status = queueStatusService.status(serviceId);
        return ResponseEntity.ok(Map.of(
                "serviceId", status.serviceId(),
                "serviceName", status.serviceName(),
                "waitingCount", status.waitingCount(),
                "servingCount", status.servingCount(),
                "calledCount", status.calledCount(),
                "activeCounters", status.activeCounters(),
                "availableCounters", status.availableCounters(),
                "generatedAt", status.generatedAt()
        ));
    }
}
