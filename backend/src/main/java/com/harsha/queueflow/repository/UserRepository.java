package com.harsha.queueflow.repository;

import java.util.List;
import java.util.Optional;

import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    @Query("""
            select u from User u
            where (:q is null
                   or lower(u.name) like :q
                   or lower(u.email) like :q
                   or lower(coalesce(u.phone,'')) like :q)
              and (:role is null or u.role = :role)
            order by u.createdAt desc, u.id desc
            """)
    Page<User> search(@Param("q") String q, @Param("role") Role role, Pageable pageable);

    List<User> findByRoleInAndActiveTrueOrderByNameAsc(List<Role> roles);

    @Modifying
    @Query("update User u set u.active = :active where u.id = :id")
    int updateActive(@Param("id") Long id, @Param("active") boolean active);

    long countByRole(Role role);
}
