package com.vorconcierge.ragcore.model;

public record ChunkRecord(
        Long id,
        Long docId,
        Long orgId,
        Long departmentId,
        int chunkIndex,
        String content,
        int minimumRoleLevel
) {}
