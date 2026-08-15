package com.vorconcierge.ragcore.repository;

import com.vorconcierge.ragcore.model.DocumentRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbc;

    private static final String SELECT_BASE =
            "SELECT d.*, (SELECT COUNT(*) FROM chunks c WHERE c.doc_id = d.id) AS chunk_count FROM documents d";

    private static final RowMapper<DocumentRecord> MAPPER = (rs, rowNum) -> new DocumentRecord(
            rs.getLong("id"),
            rs.getLong("org_id"),
            rs.getObject("department_id", Long.class),
            rs.getString("title"),
            rs.getString("file_hash"),
            rs.getString("file_uri"),
            rs.getLong("file_size"),
            rs.getLong("uploaded_by"),
            rs.getTimestamp("uploaded_at").toInstant(),
            rs.getString("status"),
            rs.getTimestamp("processed_at") != null ? rs.getTimestamp("processed_at").toInstant() : null,
            rs.getString("error_message"),
            rs.getInt("minimum_role_level"),
            rs.getInt("chunk_count")
    );

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DocumentRecord> findByHash(Long orgId, String hash) {
        var results = jdbc.query(SELECT_BASE + " WHERE d.org_id = ? AND d.file_hash = ?", MAPPER, orgId, hash);
        return results.stream().findFirst();
    }

    public Optional<DocumentRecord> findById(Long id) {
        var results = jdbc.query(SELECT_BASE + " WHERE d.id = ?", MAPPER, id);
        return results.stream().findFirst();
    }

    public List<DocumentRecord> findByOrgId(Long orgId) {
        return jdbc.query(SELECT_BASE + " WHERE d.org_id = ? ORDER BY d.uploaded_at DESC", MAPPER, orgId);
    }

    public long insert(Long orgId, Long departmentId, String title, String hash, String uri, long size,
                       Long userId, int minimumRoleLevel) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO documents (org_id, department_id, title, file_hash, file_uri, file_size, uploaded_by, minimum_role_level, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """,
                    new String[]{"id"}
            );
            ps.setLong(1, orgId);
            if (departmentId != null) {
                ps.setLong(2, departmentId);
            } else {
                ps.setNull(2, java.sql.Types.BIGINT);
            }
            ps.setString(3, title);
            ps.setString(4, hash);
            ps.setString(5, uri);
            ps.setLong(6, size);
            ps.setLong(7, userId);
            ps.setInt(8, minimumRoleLevel);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : -1;
    }

    public void updateStatus(Long id, String status, String errorMessage) {
        if ("SYNCED".equals(status)) {
            jdbc.update("UPDATE documents SET status = ?, processed_at = NOW(), error_message = NULL WHERE id = ?",
                    status, id);
        } else {
            jdbc.update("UPDATE documents SET status = ?, error_message = ? WHERE id = ?",
                    status, errorMessage, id);
        }
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM documents WHERE id = ?", id);
    }

    public int count() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM documents", Integer.class);
        return c != null ? c : 0;
    }

    public int countByOrgId(Long orgId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE org_id = ?", Integer.class, orgId);
        return c != null ? c : 0;
    }

    public int countByStatus(String status) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE status = ?", Integer.class, status);
        return c != null ? c : 0;
    }
}
