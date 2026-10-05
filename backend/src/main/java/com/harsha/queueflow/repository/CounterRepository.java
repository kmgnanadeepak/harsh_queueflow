package com.harsha.queueflow.repository;

import java.util.List;
import java.util.Optional;

import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.enums.CounterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CounterRepository extends JpaRepository<Counter, Long> {

    List<Counter> findByQueueServiceIdAndActiveTrueOrderByCounterNumberAsc(Long serviceId);

    List<Counter> findByQueueServiceIdAndActiveTrueAndStatusNotOrderByCounterNumberAsc(Long serviceId, CounterStatus status);

    List<Counter> findByOrganizationIdAndActiveTrueOrderByCounterNumberAsc(Long organizationId);

    List<Counter> findByStaffIdAndActiveTrue(Long staffId);

    Optional<Counter> findByOrganizationIdAndCounterNumberIgnoreCase(Long organizationId, String counterNumber);

    long countByActiveTrue();

    long countByActiveTrueAndStatusNot(CounterStatus status);

    @Query("""
            select c from Counter c
            where (:q is null
                   or lower(c.counterNumber) like :q
                   or lower(coalesce(c.name,'')) like :q
                   or lower(c.queueService.name) like :q)
              and (:serviceId is null or c.queueService.id = :serviceId)
              and (:staffId is null or c.staff.id = :staffId)
            order by c.counterNumber asc
            """)
    Page<Counter> search(@Param("q") String q, @Param("serviceId") Long serviceId,
                         @Param("staffId") Long staffId, Pageable pageable);
}
