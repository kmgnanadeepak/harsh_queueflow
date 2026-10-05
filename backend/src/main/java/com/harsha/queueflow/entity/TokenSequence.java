package com.harsha.queueflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Per-service token counter used to generate collision-free token numbers.
 *
 * <p>The row is locked pessimistically ({@code SELECT ... FOR UPDATE}) while a
 * token is being created, which serialises concurrent requests for the same
 * service. The unique constraint on {@code (queue_service_id, token_number)} in
 * {@link QueueToken} is the final safety net.</p>
 */
@Entity
@Table(name = "token_sequences")
public class TokenSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "queue_service_id", nullable = false, unique = true)
    private Long serviceId;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public TokenSequence() {
    }

    public TokenSequence(Long serviceId, long lastNumber) {
        this.serviceId = serviceId;
        this.lastNumber = lastNumber;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public long getLastNumber() {
        return lastNumber;
    }

    public void setLastNumber(long lastNumber) {
        this.lastNumber = lastNumber;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public long next() {
        return ++lastNumber;
    }
}
