package com.harsha.queueflow.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.CounterRequest;
import com.harsha.queueflow.dto.CounterResponse;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.Organization;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.QueueToken;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.exception.BadRequestException;
import com.harsha.queueflow.exception.ConflictException;
import com.harsha.queueflow.exception.ForbiddenOperationException;
import com.harsha.queueflow.exception.NotFoundException;
import com.harsha.queueflow.repository.CounterRepository;
import com.harsha.queueflow.repository.OrganizationRepository;
import com.harsha.queueflow.repository.QueueServiceRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;
import com.harsha.queueflow.repository.UserRepository;

@Service
public class CounterService {

    private final CounterRepository counterRepository;
    private final OrganizationRepository organizationRepository;
    private final QueueServiceRepository serviceRepository;
    private final QueueTokenRepository tokenRepository;
    private final UserRepository userRepository;

    public CounterService(CounterRepository counterRepository,
                          OrganizationRepository organizationRepository,
                          QueueServiceRepository serviceRepository,
                          QueueTokenRepository tokenRepository,
                          UserRepository userRepository) {
        this.counterRepository = counterRepository;
        this.organizationRepository = organizationRepository;
        this.serviceRepository = serviceRepository;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<CounterResponse> search(String query, Long serviceId, Long staffId, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : "%" + query.trim().toLowerCase() + "%";
        return counterRepository.search(q, serviceId, staffId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<CounterResponse> listAll() {
        return counterRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CounterResponse> listForService(Long serviceId) {
        Map<Long, String> serving = servingTokensByCounter();
        return counterRepository.findByQueueServiceIdAndActiveTrueOrderByCounterNumberAsc(serviceId).stream()
                .map(counter -> CounterResponse.from(counter, serving.get(counter.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CounterResponse> listForStaff(Long staffId) {
        Map<Long, String> serving = servingTokensByCounter();
        return counterRepository.findByStaffIdAndActiveTrue(staffId).stream()
                .map(counter -> CounterResponse.from(counter, serving.get(counter.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Counter require(Long id) {
        return counterRepository.findById(id).orElseThrow(() -> NotFoundException.of("Counter", id));
    }

    /** Map of counter id to the token number it is currently occupied with. */
    @Transactional(readOnly = true)
    public Map<Long, String> servingTokensByCounter() {
        Map<Long, String> map = new HashMap<>();
        for (QueueToken token : tokenRepository.findByStatusInAndCounterIsNotNull(
                List.of(TokenStatus.CALLED, TokenStatus.SERVING))) {
            map.put(token.getCounter().getId(), token.getTokenNumber());
        }
        return map;
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public CounterResponse create(CounterRequest request) {
        Counter counter = new Counter();
        counter.setCounterNumber(request.counterNumber().trim());
        apply(counter, request);
        return toResponse(counterRepository.save(counter));
    }

    @Transactional
    public CounterResponse update(Long id, CounterRequest request) {
        Counter counter = require(id);
        counter.setCounterNumber(request.counterNumber().trim());
        apply(counter, request);
        return toResponse(counterRepository.save(counter));
    }

    @Transactional
    public void delete(Long id) {
        Counter counter = require(id);
        boolean occupied = tokenRepository.findByCounterIdAndStatus(id, TokenStatus.SERVING).stream().findAny().isPresent()
                || tokenRepository.findByCounterIdAndStatus(id, TokenStatus.CALLED).stream().findAny().isPresent();
        if (occupied) {
            throw new ConflictException("This counter is currently serving a token");
        }
        if (tokenRepository.existsByCounterId(id)) {
            // Keep historical references intact: retire the counter instead.
            counter.setActive(false);
            counter.setStatus(CounterStatus.OFFLINE);
            counter.setStaff(null);
            counterRepository.save(counter);
            return;
        }
        counterRepository.delete(counter);
    }

    private void apply(Counter counter, CounterRequest request) {
        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> NotFoundException.of("Organization", request.organizationId()));
        QueueService service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> NotFoundException.of("Service", request.serviceId()));
        if (!service.getOrganization().getId().equals(organization.getId())) {
            throw new BadRequestException("The selected service does not belong to the selected organization");
        }
        String counterNumber = counter.getCounterNumber();
        counterRepository.findByOrganizationIdAndCounterNumberIgnoreCase(organization.getId(), counterNumber)
                .filter(existing -> !existing.getId().equals(counter.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException("Counter '" + counterNumber + "' already exists in this organization");
                });

        counter.setOrganization(organization);
        counter.setQueueService(service);
        counter.setName(blankToNull(request.name()));
        counter.setStatus(request.statusOrDefault());
        counter.setActive(request.activeOrDefault());
        counter.setStaff(resolveStaff(request.staffId()));
    }

    private CounterResponse toResponse(Counter counter) {
        return CounterResponse.from(counter, null);
    }

    private User resolveStaff(Long staffId) {
        if (staffId == null) {
            return null;
        }
        User staff = userRepository.findById(staffId).orElseThrow(() -> NotFoundException.of("User", staffId));
        if (staff.getRole() == Role.USER) {
            throw new BadRequestException("Counters can only be assigned to STAFF or ADMIN accounts");
        }
        return staff;
    }

    /** Verifies the caller (STAFF) is the counter's assigned operator. ADMIN bypasses. */
    public void assertCanOperate(Counter counter, Long callerId, Role callerRole) {
        if (callerRole == Role.ADMIN) {
            return;
        }
        if (counter.getStaff() == null || !counter.getStaff().getId().equals(callerId)) {
            throw new ForbiddenOperationException("You are not assigned to this counter");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
