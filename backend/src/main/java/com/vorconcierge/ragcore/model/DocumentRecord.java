package com.vorconcierge.ragcore.model;

import java.time.Instant;

public record DocumentRecord(
        Long id,
        Long orgId,
        Long departmentId,
        String title,
        String fileHash,
        String fileUri,
        long fileSize,
        Long uploadedBy,
        Instant uploadedAt,
        String status,
        Instant processedAt,
        String errorMessage,
        int minimumRoleLevel,
        int chunkCount
) {}
