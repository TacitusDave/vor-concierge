package com.vorconcierge.ragcore.model;

import java.time.Instant;
import java.util.List;

public record ChatMessage(
        Long id,
        Long threadId,
        Long userId,
        String query,
        String response,
        List<SourceRef> sources,
        int tokensUsed,
        Instant createdAt
) {
    public record SourceRef(Long chunkId, Long docId, String docTitle) {}
}
