package com.vorconcierge.ragcore.repository;

import com.vorconcierge.ragcore.model.AuthProfile;
import com.vorconcierge.ragcore.model.OrgMember;
import com.vorconcierge.ragcore.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<User> MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getLong("org_id"),
            rs.getObject("department_id", Long.class),
            rs.getLong("role_id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("totp_secret"),
            rs.getBoolean("is_platform_operator"),
            rs.getBoolean("enabled"),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final RowMapper<AuthProfile> AUTH_PROFILE_MAPPER = (rs, rowNum) -> new AuthProfile(
            rs.getLong("id"),
            rs.getLong("org_id"),
            rs.getObject("department_id", Long.class),
            rs.getLong("role_id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("totp_secret"),
            rs.getBoolean("is_platform_operator"),
            rs.getBoolean("enabled"),
            rs.getInt("hierarchy_level"),
            rs.getLong("capability_mask")
    );

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AuthProfile> findAuthProfileByEmail(String email) {
        var results = jdbc.query(
                """
                SELECT u.id, u.org_id, u.department_id, u.role_id, u.username, u.email,
                       u.password_hash, u.totp_secret, u.is_platform_operator, u.enabled,
                       r.hierarchy_level, r.capability_mask
                FROM users u
                JOIN roles r ON u.role_id = r.id
                WHERE u.email = ?
                """,
                AUTH_PROFILE_MAPPER, email
        );
        return results.stream().findFirst();
    }

    public Optional<AuthProfile> findAuthProfileById(Long userId) {
        var results = jdbc.query(
                """
                SELECT u.id, u.org_id, u.department_id, u.role_id, u.username, u.email,
                       u.password_hash, u.totp_secret, u.is_platform_operator, u.enabled,
                       r.hierarchy_level, r.capability_mask
                FROM users u
                JOIN roles r ON u.role_id = r.id
                WHERE u.id = ?
                """,
                AUTH_PROFILE_MAPPER, userId
        );
        return results.stream().findFirst();
    }

    public Optional<User> findById(Long id) {
        var results = jdbc.query("SELECT * FROM users WHERE id = ?", MAPPER, id);
        return results.stream().findFirst();
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    public boolean existsByOrgIdAndUsername(Long orgId, String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE org_id = ? AND username = ?", Integer.class, orgId, username);
        return count != null && count > 0;
    }

    public boolean existsPlatformOperator() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE is_platform_operator = TRUE", Integer.class);
        return count != null && count > 0;
    }

    public long insert(Long orgId, Long departmentId, Long roleId, String username, String email,
                       String passwordHash, boolean isPlatformOperator) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO users (org_id, department_id, role_id, username, email, password_hash, is_platform_operator)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[]{"id"}
            );
            ps.setLong(1, orgId);
            if (departmentId != null) {
                ps.setLong(2, departmentId);
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setLong(3, roleId);
            ps.setString(4, username);
            ps.setString(5, email);
            ps.setString(6, passwordHash);
            ps.setBoolean(7, isPlatformOperator);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : -1;
    }

    public void insertSession(Long orgId, Long userId, String jwtId, Timestamp expiresAt, String ip, String userAgent) {
        jdbc.update(
                "INSERT INTO sessions (org_id, user_id, jwt_id, expires_at, ip_address, user_agent) VALUES (?, ?, ?, ?, ?::inet, ?)",
                orgId, userId, jwtId, expiresAt, ip, userAgent
        );
    }

    public int countActiveSessions() {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM sessions WHERE expires_at > NOW()", Integer.class);
        return c != null ? c : 0;
    }

    public List<OrgMember> findOrgMembers(Long orgId) {
        RowMapper<OrgMember> mapper = (rs, rowNum) -> new OrgMember(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getBoolean("enabled"),
                rs.getObject("department_id", Long.class),
                rs.getString("department_name"),
                rs.getLong("role_id"),
                rs.getString("role_name"),
                rs.getTimestamp("created_at").toInstant()
        );
        return jdbc.query(
                """
                SELECT u.id, u.username, u.email, u.enabled, u.department_id, d.name AS department_name,
                       u.role_id, r.name AS role_name, u.created_at
                FROM users u
                JOIN roles r ON u.role_id = r.id
                LEFT JOIN departments d ON u.department_id = d.id
                WHERE u.org_id = ?
                ORDER BY u.created_at
                """,
                mapper, orgId
        );
    }

    public void updateMembership(Long userId, Long departmentId, Long roleId) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE users SET department_id = ?, role_id = ?, updated_at = NOW() WHERE id = ?");
            if (departmentId != null) {
                ps.setLong(1, departmentId);
            } else {
                ps.setNull(1, java.sql.Types.BIGINT);
            }
            ps.setLong(2, roleId);
            ps.setLong(3, userId);
            return ps;
        });
    }

    public void updateEnabled(Long userId, boolean enabled) {
        jdbc.update("UPDATE users SET enabled = ?, updated_at = NOW() WHERE id = ?", enabled, userId);
    }
}
