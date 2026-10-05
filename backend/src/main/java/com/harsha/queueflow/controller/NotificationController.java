package com.harsha.queueflow.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.harsha.queueflow.config.PageRequests;
import com.harsha.queueflow.dto.NotificationResponse;
import com.harsha.queueflow.dto.PagedResponse;
import com.harsha.queueflow.security.QueueFlowPrincipal;
import com.harsha.queueflow.service.NotificationService;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> list(
            @AuthenticationPrincipal QueueFlowPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(PagedResponse.from(
                notificationService.listForUser(principal.getId(), PageRequests.of(page, size))));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> unreadCount(@AuthenticationPrincipal QueueFlowPrincipal principal) {
        return ResponseEntity.ok(Map.of("unreadCount",
                (int) notificationService.unreadCount(principal.getId())));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@AuthenticationPrincipal QueueFlowPrincipal principal,
                                                           @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(principal.getId(), id));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@AuthenticationPrincipal QueueFlowPrincipal principal) {
        return ResponseEntity.ok(Map.of("updated", (int) notificationService.markAllAsRead(principal.getId())));
    }
}
