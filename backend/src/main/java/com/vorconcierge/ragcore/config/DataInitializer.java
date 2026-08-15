package com.vorconcierge.ragcore.config;

import com.vorconcierge.ragcore.repository.OrganizationRepository;
import com.vorconcierge.ragcore.repository.RoleRepository;
import com.vorconcierge.ragcore.repository.SubscriptionPlanRepository;
import com.vorconcierge.ragcore.repository.UserRepository;
import com.vorconcierge.ragcore.security.Capability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Bootstraps two things on an empty database:
 *
 * <p>1. A platform-operator account in the reserved internal "_platform" organization (seeded by
 * the Flyway migration) — for VOR's own operators, not a customer. Real tenant organizations and
 * their owner accounts come from org signup/provisioning, not application boot.
 *
 * <p>2. If concierge.bootstrap.create-test-org is enabled (default on, meant to be turned off
 * before a real launch), one real test organization on the Enterprise plan with a full-capability
 * Owner account, so every real feature can be exercised end-to-end during development without
 * Stripe wired up yet. This is a genuine account with a real bcrypt password — not an auth
 * bypass/backdoor, which would be a real security liability in software sold to enterprise
 * customers. It is simply comped onto the top plan for testing purposes.
 *
 * <p>Both credentials use the same pattern: if a password is configured via env var, that's used
 * (the expected path for automated deployments); otherwise a cryptographically random password is
 * generated and printed once so an operator can retrieve it and change it after first login.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PLATFORM_ORG_SLUG = "_platform";
    private static final String PLATFORM_ROLE_NAME = "Platform Operator";
    private static final String OWNER_ROLE_NAME = "Owner";

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConciergeProperties properties;

    public DataInitializer(UserRepository userRepository, OrganizationRepository organizationRepository,
                           RoleRepository roleRepository, SubscriptionPlanRepository subscriptionPlanRepository,
                           PasswordEncoder passwordEncoder, ConciergeProperties properties) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        bootstrapPlatformOperator();
        if (properties.bootstrap().createTestOrg()) {
            bootstrapTestOrg();
        }
    }

    private void bootstrapPlatformOperator() {
        if (userRepository.existsPlatformOperator()) {
            return;
        }

        var platformOrg = organizationRepository.findBySlug(PLATFORM_ORG_SLUG)
                .orElseThrow(() -> new IllegalStateException(
                        "Reserved platform organization not found; check the Flyway migration seed data"));
        var operatorRole = roleRepository.findByOrgIdAndName(platformOrg.id(), PLATFORM_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Platform Operator role not found; check the Flyway migration seed data"));

        String username = properties.bootstrap().adminUsername();
        String email = properties.bootstrap().adminEmail();
        var credentials = resolveCredentials(properties.bootstrap().adminPassword());

        userRepository.insert(platformOrg.id(), null, operatorRole.id(), username, email,
                passwordEncoder.encode(credentials.password()), true);

        logBootstrap("platform-operator", email, credentials);
    }

    private void bootstrapTestOrg() {
        String slug = properties.bootstrap().testOrgSlug();
        if (organizationRepository.findBySlug(slug).isPresent()) {
            return;
        }

        var enterprisePlan = subscriptionPlanRepository.findByCode("enterprise")
                .orElseThrow(() -> new IllegalStateException(
                        "Enterprise plan not found; check the Flyway migration seed data"));

        long orgId = organizationRepository.insert(properties.bootstrap().testOrgName(), slug,
                enterprisePlan.id(), "ACTIVE");
        long ownerRoleId = roleRepository.insert(orgId, OWNER_ROLE_NAME, 40, Capability.allBits(), true);

        String email = properties.bootstrap().testAdminEmail();
        var credentials = resolveCredentials(properties.bootstrap().testAdminPassword());

        userRepository.insert(orgId, null, ownerRoleId, "owner", email,
                passwordEncoder.encode(credentials.password()), false);

        logBootstrap("test-organization Owner (org: " + slug + ")", email, credentials);
    }

    private record Credentials(String password, boolean generated) {}

    private Credentials resolveCredentials(String configuredPassword) {
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        return new Credentials(generated ? generateSecurePassword() : configuredPassword, generated);
    }

    private void logBootstrap(String accountLabel, String email, Credentials credentials) {
        if (credentials.generated()) {
            log.warn("""

                    ================================================================
                     Bootstrapped {} account.
                     Email: {}
                     Password: {}
                     This password was randomly generated and is shown only once.
                     Sign in and rotate it (or set an env-configured password to
                     skip generation next time the database is recreated).
                    ================================================================
                    """, accountLabel, email, credentials.password());
        } else {
            log.info("Bootstrapped {} account '{}' from configured password", accountLabel, email);
        }
    }

    private String generateSecurePassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
