package com.vorconcierge.ragcore.model;

public record RetrievedChunk(
        Long chunkId,
        Long docId,
        String docTitle,
        String content,
        double distance
) {}
