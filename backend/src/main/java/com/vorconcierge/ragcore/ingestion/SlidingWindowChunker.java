package com.vorconcierge.ragcore.ingestion;

import com.vorconcierge.ragcore.config.ConciergeProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class SlidingWindowChunker {

    private final int maxTokens;
    private final int overlapTokens;

    public SlidingWindowChunker(ConciergeProperties properties) {
        this.maxTokens = properties.chunker().maxTokens();
        this.overlapTokens = properties.chunker().overlapTokens();
    }

    public List<String> chunk(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String normalized = rawText.replaceAll("\\s+", " ").trim();
        String[] tokens = normalized.split(" ");
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int step = maxTokens - overlapTokens;
        if (step <= 0) {
            throw new IllegalStateException("overlap must be less than max tokens");
        }
        while (start < tokens.length) {
            int end = Math.min(start + maxTokens, tokens.length);
            String chunk = String.join(" ", Arrays.copyOfRange(tokens, start, end));
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= tokens.length) {
                break;
            }
            start += step;
        }
        return chunks;
    }
}
