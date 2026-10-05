package com.harsha.queueflow.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.dto.OrganizationRequest;
import com.harsha.queueflow.entity.Organization;
import com.harsha.queueflow.exception.ConflictException;
import com.harsha.queueflow.exception.NotFoundException;
import com.harsha.queueflow.repository.OrganizationRepository;
import com.harsha.queueflow.repository.QueueServiceRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final QueueServiceRepository serviceRepository;
    private final QueueTokenRepository tokenRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                               QueueServiceRepository serviceRepository,
                               QueueTokenRepository tokenRepository) {
        this.organizationRepository = organizationRepository;
        this.serviceRepository = serviceRepository;
        this.tokenRepository = tokenRepository;
    }

    @Transactional(readOnly = true)
    public List<OrganizationRequest.Response> listActive() {
        Map<Long, Long> counts = countsByOrganization();
        return organizationRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(org -> OrganizationRequest.Response.from(org, counts.getOrDefault(org.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<OrganizationRequest.Response> search(String query, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : "%" + query.trim().toLowerCase() + "%";
        Map<Long, Long> counts = countsByOrganization();
        return organizationRepository.search(q, pageable)
                .map(org -> OrganizationRequest.Response.from(org, counts.getOrDefault(org.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public Organization require(Long id) {
        return organizationRepository.findById(id).orElseThrow(() -> NotFoundException.of("Organization", id));
    }

    @Transactional
    public OrganizationRequest.Response create(OrganizationRequest request) {
        String name = request.name().trim();
        if (organizationRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("An organization named '" + name + "' already exists");
        }
        Organization organization = new Organization();
        apply(organization, request);
        return OrganizationRequest.Response.from(organizationRepository.save(organization), 0L);
    }

    @Transactional
    public OrganizationRequest.Response update(Long id, OrganizationRequest request) {
        Organization organization = require(id);
        String name = request.name().trim();
        organizationRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("An organization named '" + name + "' already exists");
                });
        apply(organization, request);
        long serviceCount = serviceRepository.countByOrganizationId(id);
        return OrganizationRequest.Response.from(organizationRepository.save(organization), serviceCount);
    }

    /**
     * Organizations are soft deleted when they still own services or tokens so
     * historical records keep a valid reference; otherwise the row is removed.
     */
    @Transactional
    public void delete(Long id) {
        Organization organization = require(id);
        if (serviceRepository.existsByOrganizationId(id) || tokenRepository.existsByQueueService_Organization_Id(id)) {
            organization.setActive(false);
            organizationRepository.save(organization);
            serviceRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(id)
                    .forEach(service -> service.setActive(false));
            serviceRepository.flush();
            return;
        }
        organizationRepository.delete(organization);
    }

    private void apply(Organization organization, OrganizationRequest request) {
        organization.setName(request.name().trim());
        organization.setLocation(blankToNull(request.location()));
        organization.setDescription(blankToNull(request.description()));
        organization.setActive(request.activeOrDefault());
    }

    private Map<Long, Long> countsByOrganization() {
        return serviceRepository.findAll().stream()
                .collect(Collectors.groupingBy(s -> s.getOrganization().getId(), Collectors.counting()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
