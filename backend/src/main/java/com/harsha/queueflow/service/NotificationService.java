package com.harsha.queueflow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.NotificationResponse;
import com.harsha.queueflow.dto.RealtimeEvent;
import com.harsha.queueflow.entity.Notification;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.NotificationType;
import com.harsha.queueflow.exception.NotFoundException;
import com.harsha.queueflow.repository.NotificationRepository;
import com.harsha.queueflow.repository.UserRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RealtimePublisher realtimePublisher;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               RealtimePublisher realtimePublisher) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public Notification notify(User user, QueueToken token, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setToken(token);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRead(false);
        Notification saved = notificationRepository.save(notification);

        // The freshly created notification is part of the unread set.
        long unread = notificationRepository.countUnread(user.getId()) + (saved.isRead() ? 0 : 1);
        realtimePublisher.publishNotification(user.getId(),
                RealtimeEvent.notification(user.getId(), token == null ? null : token.getId(), type, message,
                        (int) unread));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countUnread(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        if (!notification.getUser().getId().equals(userId)) {
            throw new com.harsha.queueflow.exception.ForbiddenOperationException(
                    "You may only access your own notifications");
        }
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public long markAllAsRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Transactional(readOnly = true)
    public boolean alreadyNotified(Long userId, Long tokenId, NotificationType type) {
        return notificationRepository.existsByUserIdAndTokenIdAndType(userId, tokenId, type);
    }

    /** Sends a general notice to a set of users (used by admin broadcast). */
    @Transactional
    public int notifyUsers(Iterable<User> users, NotificationType type, String message) {
        int count = 0;
        for (User user : users) {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setType(type);
            notification.setMessage(message);
            notification.setRead(false);
            notificationRepository.save(notification);
            count++;
        }
        return count;
    }

    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
    }
}
