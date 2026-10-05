package com.harsha.queueflow.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.QueueServiceRequest;
import com.harsha.queueflow.entity.Organization;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.enums.TokenStatus;
import com.harsha.queueflow.exception.BadRequestException;
import com.harsha.queueflow.exception.ConflictException;
import com.harsha.queueflow.exception.NotFoundException;
import com.harsha.queueflow.repository.OrganizationRepository;
import com.harsha.queueflow.repository.QueueServiceRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;

@Service
public class CatalogService {

    private final QueueServiceRepository serviceRepository;
    private final OrganizationRepository organizationRepository;
    private final QueueTokenRepository tokenRepository;

    public CatalogService(QueueServiceRepository serviceRepository,
                          OrganizationRepository organizationRepository,
                          QueueTokenRepository tokenRepository) {
        this.serviceRepository = serviceRepository;
        this.organizationRepository = organizationRepository;
        this.tokenRepository = tokenRepository;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<QueueServiceRequest.Response> listActive() {
        Map<Long, Long> waiting = waitingCounts();
        return serviceRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(service -> service.getOrganization().isActive())
                .map(service -> QueueServiceRequest.Response.from(service, waiting.getOrDefault(service.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueueServiceRequest.Response> listAll() {
        Map<Long, Long> waiting = waitingCounts();
        return serviceRepository.findAllByOrderByNameAsc().stream()
                .map(service -> QueueServiceRequest.Response.from(service, waiting.getOrDefault(service.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueueServiceRequest.Response> listByOrganization(Long organizationId) {
        Map<Long, Long> waiting = waitingCounts();
        return serviceRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(organizationId).stream()
                .map(service -> QueueServiceRequest.Response.from(service, waiting.getOrDefault(service.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<QueueServiceRequest.Response> search(String query, Long organizationId, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : "%" + query.trim().toLowerCase() + "%";
        Map<Long, Long> waiting = waitingCounts();
        return serviceRepository.search(q, organizationId, pageable)
                .map(service -> QueueServiceRequest.Response.from(service, waiting.getOrDefault(service.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public QueueService require(Long id) {
        return serviceRepository.findById(id).orElseThrow(() -> NotFoundException.of("Service", id));
    }

    @Transactional(readOnly = true)
    public QueueService requireActive(Long id) {
        QueueService service = require(id);
        if (!service.isActive() || !service.getOrganization().isActive()) {
            throw new BadRequestException("This service is not currently accepting tokens");
        }
        return service;
    }

    @Transactional(readOnly = true)
    public QueueServiceRequest.Response describe(Long id) {
        QueueService service = require(id);
        long waiting = tokenRepository.findByQueueServiceIdAndStatusOrderByCreatedAtAsc(id, TokenStatus.WAITING).size();
        return QueueServiceRequest.Response.from(service, waiting);
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public QueueServiceRequest.Response create(QueueServiceRequest request) {
        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> NotFoundException.of("Organization", request.organizationId()));
        String name = request.name().trim();
        if (serviceRepository.findByNameIgnoreCaseAndOrganizationId(name, organization.getId()).isPresent()) {
            throw new ConflictException("This organization already has a service named '" + name + "'");
        }
        QueueService service = new QueueService();
        service.setOrganization(organization);
        service.setName(name);
        service.setDescription(blankToNull(request.description()));
        service.setAverageServiceTime(request.averageServiceTime());
        service.setActive(request.activeOrDefault());
        return QueueServiceRequest.Response.from(serviceRepository.save(service), 0L);
    }

    @Transactional
    public QueueServiceRequest.Response update(Long id, QueueServiceRequest request) {
        QueueService service = require(id);
        Organization organization = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> NotFoundException.of("Organization", request.organizationId()));
        String name = request.name().trim();
        serviceRepository.findByNameIgnoreCaseAndOrganizationId(name, organization.getId())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("This organization already has a service named '" + name + "'");
                });
        service.setOrganization(organization);
        service.setName(name);
        service.setDescription(blankToNull(request.description()));
        service.setAverageServiceTime(request.averageServiceTime());
        service.setActive(request.activeOrDefault());
        long waiting = tokenRepository.findByQueueServiceIdAndStatusOrderByCreatedAtAsc(id, TokenStatus.WAITING).size();
        return QueueServiceRequest.Response.from(serviceRepository.save(service), waiting);
    }

    /** Soft deletes when the service already has tokens, hard deletes otherwise. */
    @Transactional
    public void delete(Long id) {
        QueueService service = require(id);
        if (tokenRepository.existsByQueueServiceId(id)) {
            service.setActive(false);
            serviceRepository.save(service);
            return;
        }
        serviceRepository.delete(service);
    }

    private Map<Long, Long> waitingCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : tokenRepository.countByServiceAndStatus(TokenStatus.WAITING)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
