package com.harsha.queueflow.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.Organization;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.repository.CounterRepository;
import com.harsha.queueflow.repository.NotificationRepository;
import com.harsha.queueflow.repository.OrganizationRepository;
import com.harsha.queueflow.repository.QueueServiceRepository;
import com.harsha.queueflow.repository.QueueTokenRepository;
import com.harsha.queueflow.repository.TokenSequenceRepository;
import com.harsha.queueflow.repository.UserRepository;
import com.harsha.queueflow.security.JwtUtil;
import com.harsha.queueflow.security.QueueFlowPrincipal;

/**
 * Base class for the integration tests. Provides fixture builders and a JWT
 * helper so requests can be made exactly the way the React client makes them.
 */
@ActiveProfiles("test")
@Import(TestSupport.FastBcryptConfig.class)
public abstract class TestSupport {

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected OrganizationRepository organizationRepository;

    @Autowired
    protected QueueServiceRepository serviceRepository;

    @Autowired
    protected CounterRepository counterRepository;

    @Autowired
    protected QueueTokenRepository tokenRepository;

    @Autowired
    protected NotificationRepository notificationRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected TokenSequenceRepository sequenceRepository;

    @TestConfiguration
    public static class FastBcryptConfig {
        /** Low cost keeps the suite fast; production uses strength 12. */
        @Bean
        @Primary
        public PasswordEncoder testPasswordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    /**
     * Every test starts from an empty database. The H2 instance is shared by the
     * whole (cached) Spring context, and fixtures use fixed emails, so without
     * this the unique email constraint would fail on the second test method.
     */
    @BeforeEach
    void purgeDatabase() {
        notificationRepository.deleteAllInBatch();
        tokenRepository.deleteAllInBatch();
        sequenceRepository.deleteAllInBatch();
        counterRepository.deleteAllInBatch();
        serviceRepository.deleteAllInBatch();
        organizationRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    protected String tokenFor(User user) {
        return jwtUtil.generateToken(new QueueFlowPrincipal(user));
    }

    protected User createUser(String email, String password, Role role) {
        User user = new User();
        user.setName(label(email));
        user.setEmail(email);
        user.setPhone("+919000000000");
        user.setPassword(passwordEncoder.encode(password));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(role);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    protected User createUser(String email, Role role) {
        return createUser(email, "Password123", role);
    }

    protected Organization createOrganization(String name) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setLocation("Test Location");
        organization.setDescription("Created by the test suite");
        organization.setActive(true);
        return organizationRepository.saveAndFlush(organization);
    }

    protected QueueService createService(Organization organization, String name, int averageServiceTime) {
        QueueService service = new QueueService();
        service.setOrganization(organization);
        service.setName(name);
        service.setDescription("Service created by the test suite");
        service.setAverageServiceTime(averageServiceTime);
        service.setActive(true);
        return serviceRepository.saveAndFlush(service);
    }

    protected Counter createCounter(Organization organization, QueueService service, String number, User staff) {
        return createCounter(organization, service, number, staff, CounterStatus.AVAILABLE);
    }

    protected Counter createCounter(Organization organization, QueueService service, String number, User staff,
                                    CounterStatus status) {
        Counter counter = new Counter();
        counter.setOrganization(organization);
        counter.setQueueService(service);
        counter.setCounterNumber(number);
        counter.setName("Counter " + number);
        counter.setStatus(status);
        counter.setStaff(staff);
        counter.setActive(true);
        return counterRepository.saveAndFlush(counter);
    }

    private String label(String email) {
        String local = email.substring(0, email.indexOf('@'));
        String name = local.replace('.', ' ').replace('_', ' ').replace('-', ' ');
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
