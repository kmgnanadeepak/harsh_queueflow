package com.harsha.queueflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.harsha.queueflow.entity.Counter;
import com.harsha.queueflow.entity.Organization;
import com.harsha.queueflow.entity.QueueService;
import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.AuthProvider;
import com.harsha.queueflow.enums.CounterStatus;
import com.harsha.queueflow.enums.Role;
import com.harsha.queueflow.repository.CounterRepository;
import com.harsha.queueflow.repository.OrganizationRepository;
import com.harsha.queueflow.repository.QueueServiceRepository;
import com.harsha.queueflow.repository.UserRepository;

/**
 * Development seed data.
 *
 * <p>Creates the demo organization, its services/counters and one account per
 * role. All passwords are BCrypt hashed before they touch the database, and no
 * Google identity is fabricated - federated accounts are only ever created by a
 * real OAuth2 login.</p>
 *
 * <p>Seeding is idempotent: it is skipped when accounts already exist and can be
 * switched off with {@code queueflow.seed.enabled=false}.</p>
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final QueueServiceRepository serviceRepository;
    private final CounterRepository counterRepository;
    private final PasswordEncoder passwordEncoder;
    private final QueueFlowProperties properties;

    public DataSeeder(UserRepository userRepository,
                      OrganizationRepository organizationRepository,
                      QueueServiceRepository serviceRepository,
                      CounterRepository counterRepository,
                      PasswordEncoder passwordEncoder,
                      QueueFlowProperties properties) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.serviceRepository = serviceRepository;
        this.counterRepository = counterRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        if (!properties.getSeed().isEnabled()) {
            log.info("Seed data disabled (queueflow.seed.enabled=false)");
            return;
        }
        if (userRepository.count() > 0) {
            log.info("Seed data skipped - the database already contains accounts");
            return;
        }

        User admin = createUser("QueueFlow Admin", "admin@queueflow.com", "+919000000001", "admin123",
                Role.ADMIN);
        User staff = createUser("Ravi Kumar", "staff@queueflow.com", "+919000000002", "staff123", Role.STAFF);
        createUser("Demo Customer", "user@queueflow.com", "+919000000003", "user123", Role.USER);

        Organization hospital = new Organization();
        hospital.setName("Kuppam General Hospital");
        hospital.setLocation("Kuppam, Chennai, Tamil Nadu");
        hospital.setDescription("Multi-specialty hospital offering outpatient consultations, billing and pharmacy.");
        hospital.setActive(true);
        Organization savedHospital = organizationRepository.save(hospital);

        QueueService consultation = createService(savedHospital, "General Consultation",
                "Routine outpatient consultation with a general physician.", 10);
        QueueService billing = createService(savedHospital, "Billing",
                "Payment collection, insurance claims and receipt printing.", 5);
        QueueService pharmacy = createService(savedHospital, "Pharmacy",
                "Prescription collection and medicine dispensing.", 3);

        createCounter(savedHospital, consultation, "C1", "Counter 1", staff);
        createCounter(savedHospital, consultation, "C2", "Counter 2", null);
        createCounter(savedHospital, billing, "B1", "Billing Counter", null);
        createCounter(savedHospital, pharmacy, "P1", "Pharmacy Counter", null);

        log.info("Seed data created: organization '{}', 3 services, {} counters, admin/staff/user accounts "
                        + "(admin@queueflow.com / staff@queueflow.com / user@queueflow.com)",
                savedHospital.getName(), counterRepository.count());
        log.debug("Seeded administrator id={} and staff id={}", admin.getId(), staff.getId());
    }

    private User createUser(String name, String email, String phone, String rawPassword, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    private QueueService createService(Organization organization, String name, String description, int minutes) {
        QueueService service = new QueueService();
        service.setOrganization(organization);
        service.setName(name);
        service.setDescription(description);
        service.setAverageServiceTime(minutes);
        service.setActive(true);
        return serviceRepository.save(service);
    }

    private Counter createCounter(Organization organization, QueueService service, String number, String label,
                                  User staff) {
        Counter counter = new Counter();
        counter.setOrganization(organization);
        counter.setQueueService(service);
        counter.setCounterNumber(number);
        counter.setName(label);
        counter.setStatus(CounterStatus.AVAILABLE);
        counter.setStaff(staff);
        counter.setActive(true);
        return counterRepository.save(counter);
    }
}
