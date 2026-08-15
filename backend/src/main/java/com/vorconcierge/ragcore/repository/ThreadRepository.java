package com.vorconcierge.ragcore.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vorconcierge.ragcore.model.ChatMessage;
import com.vorconcierge.ragcore.model.ThreadRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;

@Repository
public class ThreadRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ThreadRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<ThreadRecord> findByUserId(Long userId) {
        RowMapper<ThreadRecord> mapper = (rs, rowNum) -> new ThreadRecord(
                rs.getLong("id"),
                rs.getLong("org_id"),
                rs.getLong("user_id"),
                rs.getString("title"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
        return jdbc.query(
                "SELECT * FROM threads WHERE user_id = ? ORDER BY updated_at DESC",
                mapper, userId
        );
    }

    public long create(Long orgId, Long userId, String title) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO threads (org_id, user_id, title) VALUES (?, ?, ?)",
                    new String[]{"id"}
            );
            ps.setLong(1, orgId);
            ps.setLong(2, userId);
            ps.setString(3, title);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : -1;
    }

    public void touch(Long threadId) {
        jdbc.update("UPDATE threads SET updated_at = NOW() WHERE id = ?", threadId);
    }

    public void updateTitle(Long threadId, String title) {
        jdbc.update("UPDATE threads SET title = ?, updated_at = NOW() WHERE id = ?", title, threadId);
    }

    public void delete(Long threadId) {
        jdbc.update("DELETE FROM threads WHERE id = ?", threadId);
    }

    public long sumTokensTodayForOrg(Long orgId) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(tokens_used), 0) FROM chat_history WHERE org_id = ? AND created_at::date = CURRENT_DATE",
                Long.class, orgId);
        return sum != null ? sum : 0L;
    }

    /** Platform-wide total across every organization, for the platform-operator dashboard. */
    public long sumTokensTodayGlobal() {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(tokens_used), 0) FROM chat_history WHERE created_at::date = CURRENT_DATE",
                Long.class);
        return sum != null ? sum : 0L;
    }

    public boolean belongsToUser(Long threadId, Long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM threads WHERE id = ? AND user_id = ?",
                Integer.class, threadId, userId
        );
        return count != null && count > 0;
    }

    public List<ChatMessage> findMessages(Long threadId) {
        RowMapper<ChatMessage> mapper = (rs, rowNum) -> {
            List<ChatMessage.SourceRef> sources = parseSources(rs.getString("source_ids"));
            return new ChatMessage(
                    rs.getLong("id"),
                    rs.getLong("thread_id"),
                    rs.getLong("user_id"),
                    rs.getString("query"),
                    rs.getString("response"),
                    sources,
                    rs.getInt("tokens_used"),
                    rs.getTimestamp("created_at").toInstant()
            );
        };
        return jdbc.query(
                "SELECT * FROM chat_history WHERE thread_id = ? ORDER BY created_at ASC",
                mapper, threadId
        );
    }

    public void saveMessage(Long orgId, Long threadId, Long userId, String query, String response,
                            List<ChatMessage.SourceRef> sources, int tokensUsed) {
        String sourcesJson = toJson(sources);
        jdbc.update(
                "INSERT INTO chat_history (org_id, thread_id, user_id, query, response, source_ids, tokens_used) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)",
                orgId, threadId, userId, query, response, sourcesJson, tokensUsed
        );
    }

    private List<ChatMessage.SourceRef> parseSources(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String toJson(List<ChatMessage.SourceRef> sources) {
        try {
            return objectMapper.writeValueAsString(sources != null ? sources : Collections.emptyList());
        } catch (Exception e) {
            return "[]";
        }
    }
}
