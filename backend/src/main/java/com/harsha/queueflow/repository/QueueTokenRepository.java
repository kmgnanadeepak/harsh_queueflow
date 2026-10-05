package com.harsha.queueflow.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.enums.TokenStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueTokenRepository extends JpaRepository<QueueToken, Long> {

    Optional<QueueToken> findByIdAndUserId(Long id, Long userId);

    Optional<QueueToken> findByTokenNumberAndQueueServiceId(String tokenNumber, Long serviceId);

    List<QueueToken> findByQueueServiceIdAndStatusOrderByCreatedAtAsc(Long serviceId, TokenStatus status);

    List<QueueToken> findByQueueServiceIdAndStatusInOrderByCreatedAtAsc(Long serviceId, List<TokenStatus> statuses);

    List<QueueToken> findByCounterIdAndStatus(Long counterId, TokenStatus status);

    List<QueueToken> findByStatusInAndCounterIsNotNull(List<TokenStatus> statuses);

    boolean existsByCounterId(Long counterId);

    boolean existsByQueueServiceId(Long serviceId);

    List<QueueToken> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<QueueToken> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<TokenStatus> statuses);

    Page<QueueToken> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
            select t from QueueToken t
            where t.user.id = :userId
              and t.status in :statuses
            order by t.createdAt desc, t.id desc
            """)
    Page<QueueToken> findHistory(@Param("userId") Long userId,
                                 @Param("statuses") List<TokenStatus> statuses,
                                 Pageable pageable);

    @Query("select t from QueueToken t where t.user.id = :userId order by t.createdAt desc, t.id desc")
    List<QueueToken> findAllForUser(@Param("userId") Long userId);

    long countByStatus(TokenStatus status);

    boolean existsByQueueService_Organization_Id(Long organizationId);

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByStatusAndCreatedAtBetween(TokenStatus status, Instant from, Instant to);

    long countByQueueServiceIdAndCreatedAtBetween(Long serviceId, Instant from, Instant to);

    @Query("""
            select t.queueService.id, count(t) from QueueToken t
            where t.status = :status
            group by t.queueService.id
            """)
    List<Object[]> countByServiceAndStatus(@Param("status") TokenStatus status);

    @Query("""
            select t.status, count(t) from QueueToken t
            where t.createdAt between :from and :to
            group by t.status
            """)
    List<Object[]> countByStatusBetween(Instant from, Instant to);

    @Query("""
            select t.queueService.id, t.queueService.name, count(t) from QueueToken t
            where t.createdAt between :from and :to
            group by t.queueService.id, t.queueService.name
            order by count(t) desc
            """)
    List<Object[]> countByServiceBetween(Instant from, Instant to);

    @Query("""
            select t from QueueToken t
            where t.calledAt is not null and t.completedAt is not null
              and t.createdAt between :from and :to
            """)
    List<QueueToken> completedBetween(Instant from, Instant to);

    @Query("""
            select t from QueueToken t
            where t.servingAt is not null and t.completedAt is not null
              and t.createdAt between :from and :to
            """)
    List<QueueToken> servedBetween(Instant from, Instant to);
}
