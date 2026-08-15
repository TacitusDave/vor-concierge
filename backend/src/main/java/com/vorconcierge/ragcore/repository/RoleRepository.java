package com.vorconcierge.ragcore.repository;

import com.vorconcierge.ragcore.model.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class RoleRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Role> MAPPER = (rs, rowNum) -> new Role(
            rs.getLong("id"),
            rs.getLong("org_id"),
            rs.getString("name"),
            rs.getInt("hierarchy_level"),
            rs.getLong("capability_mask"),
            rs.getBoolean("is_default")
    );

    public RoleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Role> findById(Long id) {
        var results = jdbc.query("SELECT * FROM roles WHERE id = ?", MAPPER, id);
        return results.stream().findFirst();
    }

    public List<Role> findByOrgId(Long orgId) {
        return jdbc.query("SELECT * FROM roles WHERE org_id = ? ORDER BY hierarchy_level DESC", MAPPER, orgId);
    }

    public Optional<Role> findByOrgIdAndName(Long orgId, String name) {
        var results = jdbc.query("SELECT * FROM roles WHERE org_id = ? AND name = ?", MAPPER, orgId, name);
        return results.stream().findFirst();
    }

    public long insert(Long orgId, String name, int hierarchyLevel, long capabilityMask, boolean isDefault) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO roles (org_id, name, hierarchy_level, capability_mask, is_default) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setLong(1, orgId);
            ps.setString(2, name);
            ps.setInt(3, hierarchyLevel);
            ps.setLong(4, capabilityMask);
            ps.setBoolean(5, isDefault);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : -1;
    }

    public void update(Long id, String name, int hierarchyLevel, long capabilityMask) {
        jdbc.update(
                "UPDATE roles SET name = ?, hierarchy_level = ?, capability_mask = ?, updated_at = NOW() WHERE id = ?",
                name, hierarchyLevel, capabilityMask, id
        );
    }

    /** Returns false if any user still holds this role (roles.id is ON DELETE RESTRICT). */
    public boolean delete(Long id) {
        try {
            jdbc.update("DELETE FROM roles WHERE id = ?", id);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
