package com.harsha.queueflow.repository;

import java.util.Optional;

import com.harsha.queueflow.entity.TokenSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenSequenceRepository extends JpaRepository<TokenSequence, Long> {

    Optional<TokenSequence> findByServiceId(Long serviceId);

    /**
     * Acquires a row level write lock, serialising token number allocation for
     * the given service until the surrounding transaction completes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TokenSequence s where s.serviceId = :serviceId")
    Optional<TokenSequence> findByServiceIdForUpdate(@Param("serviceId") Long serviceId);
}
