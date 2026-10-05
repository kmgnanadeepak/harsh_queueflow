package com.harsha.queueflow.service;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.AdminUserRequest;
import com.harsha.queueflow.dto.UserResponse;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.NotificationType;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.exception.BadRequestException;
import com.harsha.queueflow.exception.ConflictException;
import com.harsha.queueflow.exception.NotFoundException;
import com.harsha.queueflow.repository.CounterRepository;
import com.harsha.queueflow.repository.UserRepository;

/**
 * Admin-only user management, including the only legitimate way to create or
 * promote STAFF / ADMIN accounts.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final CounterRepository counterRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public UserAdminService(UserRepository userRepository,
                            CounterRepository counterRepository,
                            PasswordEncoder passwordEncoder,
                            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.counterRepository = counterRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String query, Role role, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return userRepository.search(q, role, pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    @Transactional
    public UserResponse create(AdminUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("A password is required when creating an account");
        }
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(request.phone()));
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(request.role());
        user.setActive(request.activeOrDefault());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, AdminUserRequest request) {
        User user = require(id);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Another account already uses this email");
                });

        if (request.role() == Role.USER && isAssignedToCounter(user)) {
            throw new BadRequestException("Reassign the counter before demoting this staff member to USER");
        }

        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(blankToNull(request.phone()));
        user.setRole(request.role());
        user.setActive(request.activeOrDefault());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Users are deactivated rather than removed while they are referenced by a
     * counter or by historical tokens, so the audit trail stays intact.
     */
    @Transactional
    public void delete(Long id) {
        User user = require(id);
        if (isAssignedToCounter(user)) {
            throw new ConflictException("Detach this staff member from their counter first");
        }
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public int broadcast(String message) {
        List<User> users = userRepository.findByRoleInAndActiveTrueOrderByNameAsc(List.of(Role.USER));
        return notificationService.notifyUsers(users, NotificationType.GENERAL, message);
    }

    private boolean isAssignedToCounter(User user) {
        List<Counter> counters = counterRepository.findByStaffIdAndActiveTrue(user.getId());
        return counters.stream().anyMatch(counter -> counter.getStatus() != CounterStatus.OFFLINE)
                || !counters.isEmpty();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
