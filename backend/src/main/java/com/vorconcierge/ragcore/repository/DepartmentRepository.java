package com.vorconcierge.ragcore.repository;

import com.vorconcierge.ragcore.model.Department;
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
public class DepartmentRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Department> MAPPER = (rs, rowNum) -> new Department(
            rs.getLong("id"),
            rs.getLong("org_id"),
            rs.getObject("parent_department_id", Long.class),
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant()
    );

    public DepartmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Department> findByOrgId(Long orgId) {
        return jdbc.query("SELECT * FROM departments WHERE org_id = ? ORDER BY name", MAPPER, orgId);
    }

    public Optional<Department> findById(Long id) {
        var results = jdbc.query("SELECT * FROM departments WHERE id = ?", MAPPER, id);
        return results.stream().findFirst();
    }

    public long insert(Long orgId, Long parentDepartmentId, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO departments (org_id, parent_department_id, name) VALUES (?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setLong(1, orgId);
            if (parentDepartmentId != null) {
                ps.setLong(2, parentDepartmentId);
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setString(3, name);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : -1;
    }

    public void rename(Long id, String name) {
        jdbc.update("UPDATE departments SET name = ?, updated_at = NOW() WHERE id = ?", name, id);
    }

    /** Returns false if the department has sub-departments (must be removed/reparented first). */
    public boolean delete(Long id) {
        try {
            jdbc.update("DELETE FROM departments WHERE id = ?", id);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
