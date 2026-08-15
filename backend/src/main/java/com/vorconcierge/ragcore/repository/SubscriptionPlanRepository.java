package com.vorconcierge.ragcore.repository;

import com.vorconcierge.ragcore.model.SubscriptionPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SubscriptionPlanRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<SubscriptionPlan> MAPPER = (rs, rowNum) -> new SubscriptionPlan(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getLong("capability_mask"),
            (Integer) rs.getObject("max_users"),
            (Long) rs.getObject("max_storage_bytes"),
            (Integer) rs.getObject("max_documents"),
            rs.getString("stripe_price_id")
    );

    public SubscriptionPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SubscriptionPlan> findByCode(String code) {
        var results = jdbc.query("SELECT * FROM subscription_plans WHERE code = ?", MAPPER, code);
        return results.stream().findFirst();
    }

    public Optional<SubscriptionPlan> findById(Long id) {
        var results = jdbc.query("SELECT * FROM subscription_plans WHERE id = ?", MAPPER, id);
        return results.stream().findFirst();
    }

    public List<SubscriptionPlan> findAll() {
        return jdbc.query("SELECT * FROM subscription_plans ORDER BY id", MAPPER);
    }
}
