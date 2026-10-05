package com.harsha.queueflow.repository;

import java.util.List;
import java.util.Optional;

import com.harsha.queueflow.entity.QueueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueServiceRepository extends JpaRepository<QueueService, Long> {

    List<QueueService> findByOrganizationIdAndActiveTrueOrderByNameAsc(Long organizationId);

    List<QueueService> findByActiveTrueOrderByNameAsc();

    List<QueueService> findAllByOrderByNameAsc();

    Optional<QueueService> findByNameIgnoreCaseAndOrganizationId(String name, Long organizationId);

    long countByActiveTrue();

    long countByOrganizationId(Long organizationId);

    boolean existsByOrganizationId(Long organizationId);

    @Query("""
            select s from QueueService s
            where (:q is null
                   or lower(s.name) like :q
                   or lower(s.organization.name) like :q)
              and (:orgId is null or s.organization.id = :orgId)
            order by s.name asc
            """)
    Page<QueueService> search(@Param("q") String q, @Param("orgId") Long orgId, Pageable pageable);
}
