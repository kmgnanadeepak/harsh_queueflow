package com.harsha.queueflow.repository;

import java.util.List;

import com.harsha.queueflow.entity.Notification;
import com.harsha.queueflow.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select count(n) from Notification n where n.user.id = :userId and n.read = false")
    long countUnread(@Param("userId") Long userId);

    boolean existsByUserIdAndTokenIdAndType(Long userId, Long tokenId, NotificationType type);

    @Modifying
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);

    List<Notification> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
