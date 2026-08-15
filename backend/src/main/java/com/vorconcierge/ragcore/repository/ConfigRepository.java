package com.vorconcierge.ragcore.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ConfigRepository {

    private final JdbcTemplate jdbc;

    public ConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> findAll() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT key, value FROM platform_config");
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("key"), (String) row.get("value"));
        }
        return result;
    }

    public void upsert(String key, String value) {
        jdbc.update(
                "INSERT INTO platform_config (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()",
                key, value
        );
    }
}
