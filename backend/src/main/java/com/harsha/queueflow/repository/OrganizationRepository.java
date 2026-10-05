package com.harsha.queueflow.repository;

import java.util.List;
import java.util.Optional;

import com.harsha.queueflow.entity.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Organization> findByActiveTrueOrderByNameAsc();

    List<Organization> findAllByOrderByNameAsc();

    @Query("""
            select o from Organization o
            where (:q is null or lower(o.name) like :q or lower(coalesce(o.location,'')) like :q)
            order by o.name asc
            """)
    Page<Organization> search(@Param("q") String q, Pageable pageable);
}
