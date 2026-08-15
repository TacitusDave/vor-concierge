package com.vorconcierge.ragcore.repository;

import com.pgvector.PGvector;
import com.vorconcierge.ragcore.model.RetrievedChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class ChunkRepository {

    private final JdbcTemplate jdbc;

    public ChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insertChunk(Long docId, Long orgId, Long departmentId, int index, String content, int minimumRoleLevel) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO chunks (doc_id, org_id, department_id, chunk_index, content, minimum_role_level)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    new String[]{"id"}
            );
            ps.setLong(1, docId);
            ps.setLong(2, orgId);
            if (departmentId != null) {
                ps.setLong(3, departmentId);
            } else {
                ps.setNull(3, java.sql.Types.BIGINT);
            }
            ps.setInt(4, index);
            ps.setString(5, content);
            ps.setInt(6, minimumRoleLevel);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : -1;
    }

    public void insertEmbedding(Long chunkId, float[] embedding) {
        jdbc.update("INSERT INTO embeddings (chunk_id, embedding) VALUES (?, ?)",
                chunkId, new PGvector(embedding));
    }

    public void deleteByDocId(Long docId) {
        jdbc.update("DELETE FROM chunks WHERE doc_id = ?", docId);
    }

    /**
     * A user can see a chunk if it belongs to their org, the document has finished ingesting,
     * their role's hierarchy_level meets the chunk's minimum_role_level floor, and the chunk is
     * either org-wide (department_id IS NULL) or scoped to their own department.
     */
    public List<RetrievedChunk> searchSimilar(float[] queryVec, Long orgId, int userHierarchyLevel,
                                              Long userDepartmentId, int limit) {
        String sql = """
                SELECT c.id, c.doc_id, d.title, c.content, e.embedding <=> ? AS distance
                FROM embeddings e
                JOIN chunks c ON e.chunk_id = c.id
                JOIN documents d ON c.doc_id = d.id
                WHERE c.org_id = ?
                  AND d.status = 'SYNCED'
                  AND ? >= c.minimum_role_level
                  AND (c.department_id IS NULL OR c.department_id = ?)
                ORDER BY distance
                LIMIT ?
                """;
        RowMapper<RetrievedChunk> mapper = (rs, rowNum) -> new RetrievedChunk(
                rs.getLong("id"),
                rs.getLong("doc_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getDouble("distance")
        );
        return jdbc.query(sql, mapper, new PGvector(queryVec), orgId, userHierarchyLevel, userDepartmentId, limit);
    }

    public int count() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM chunks", Integer.class);
        return c != null ? c : 0;
    }

    public int countByOrgId(Long orgId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM chunks WHERE org_id = ?", Integer.class, orgId);
        return c != null ? c : 0;
    }
}
