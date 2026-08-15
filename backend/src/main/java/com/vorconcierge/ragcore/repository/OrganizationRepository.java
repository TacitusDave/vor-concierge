package com.vorconcierge.ragcore.repository;

import com.vorconcierge.ragcore.model.Organization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Optional;

@Repository
public class OrganizationRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Organization> MAPPER = (rs, rowNum) -> new Organization(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getLong("plan_id"),
            rs.getString("subscription_status"),
            rs.getTimestamp("trial_ends_at") != null ? rs.getTimestamp("trial_ends_at").toInstant() : null,
            rs.getString("stripe_customer_id"),
            rs.getString("stripe_subscription_id"),
            rs.getTimestamp("created_at").toInstant()
    );

    public OrganizationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Organization> findById(Long id) {
        var results = jdbc.query("SELECT * FROM organizations WHERE id = ?", MAPPER, id);
        return results.stream().findFirst();
    }

    public Optional<Organization> findBySlug(String slug) {
        var results = jdbc.query("SELECT * FROM organizations WHERE slug = ?", MAPPER, slug);
        return results.stream().findFirst();
    }

    public long insert(String name, String slug, Long planId, String subscriptionStatus) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO organizations (name, slug, plan_id, subscription_status) VALUES (?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setString(1, name);
            ps.setString(2, slug);
            ps.setLong(3, planId);
            ps.setString(4, subscriptionStatus);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : -1;
    }

    public record PlanCapability(long capabilityMask, String subscriptionStatus) {}

    /** Joined lookup of an org's current plan capability ceiling + subscription status. */
    public Optional<PlanCapability> findPlanCapability(Long orgId) {
        var results = jdbc.query(
                """
                SELECT p.capability_mask, o.subscription_status
                FROM organizations o
                JOIN subscription_plans p ON o.plan_id = p.id
                WHERE o.id = ?
                """,
                (rs, rowNum) -> new PlanCapability(rs.getLong("capability_mask"), rs.getString("subscription_status")),
                orgId
        );
        return results.stream().findFirst();
    }
}
