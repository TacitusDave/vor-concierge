package com.vorconcierge.ragcore.service;

import com.vorconcierge.ragcore.model.ChatMessage;
import com.vorconcierge.ragcore.model.RetrievedChunk;
import com.vorconcierge.ragcore.repository.ChunkRepository;
import com.vorconcierge.ragcore.repository.ThreadRepository;
import com.vorconcierge.ragcore.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RagService {

    private static final String SYSTEM_TEMPLATE = """
            [SYSTEM CONTROL INSTRUCTION] You are a secure corporate data concierge. You must answer the user's inquiry utilizing ONLY the factual data blocks provided below. If the answer cannot be explicitly derived from the context blocks, state clearly: "Insufficient secure local data." Do not extrapolate or hallucinate.

            [SECURE CONTEXT BLOCKS]
            %s

            [USER PROMPT] %s
            """;

    private final OllamaClient ollamaClient;
    private final ChunkRepository chunkRepository;
    private final ThreadRepository threadRepository;
    private final RuntimeConfigService configService;
    private final SseRegistryService sseRegistry;

    public RagService(OllamaClient ollamaClient, ChunkRepository chunkRepository,
                      ThreadRepository threadRepository, RuntimeConfigService configService,
                      SseRegistryService sseRegistry) {
        this.ollamaClient = ollamaClient;
        this.chunkRepository = chunkRepository;
        this.threadRepository = threadRepository;
        this.configService = configService;
        this.sseRegistry = sseRegistry;
    }

    public SseEmitter chat(AuthenticatedUser user, Long threadId, String query) {
        long resolvedThreadId = resolveThread(user, threadId, query);
        List<RetrievedChunk> chunks = retrieveChunks(user, query);

        String context = buildContext(chunks);
        String prompt = SYSTEM_TEMPLATE.formatted(context, query);

        SseEmitter emitter = new SseEmitter(300000L);
        sseRegistry.register(emitter);

        StringBuilder responseBuilder = new StringBuilder();
        AtomicReference<List<ChatMessage.SourceRef>> sourcesRef = new AtomicReference<>(toSources(chunks));

        try {
            emitter.send(SseEmitter.event().name("sources").data(sourcesRef.get()));
        } catch (Exception ignored) {
        }

        ollamaClient.generateStream(
                prompt,
                configService.getTemperature(),
                configService.getTopP(),
                configService.getFrequencyPenalty(),
                token -> {
                    responseBuilder.append(token);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("complete"));
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                    threadRepository.saveMessage(
                            user.orgId(),
                            resolvedThreadId,
                            user.userId(),
                            query,
                            responseBuilder.toString(),
                            sourcesRef.get(),
                            responseBuilder.length() / 4
                    );
                    threadRepository.touch(resolvedThreadId);
                }
        );

        return emitter;
    }

    private long resolveThread(AuthenticatedUser user, Long threadId, String query) {
        if (threadId != null && threadRepository.belongsToUser(threadId, user.userId())) {
            return threadId;
        }
        String title = query.length() > 60 ? query.substring(0, 57) + "..." : query;
        return threadRepository.create(user.orgId(), user.userId(), title);
    }

    private List<RetrievedChunk> retrieveChunks(AuthenticatedUser user, String query) {
        try {
            float[] queryVec = ollamaClient.embed(query);
            return chunkRepository.searchSimilar(queryVec, user.orgId(), user.hierarchyLevel(), user.departmentId(), 5);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildContext(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "(No relevant context found)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append("--- Block ").append(i + 1).append(" (").append(c.docTitle()).append(") ---\n");
            sb.append(c.content()).append("\n\n");
        }
        return sb.toString();
    }

    private List<ChatMessage.SourceRef> toSources(List<RetrievedChunk> chunks) {
        List<ChatMessage.SourceRef> sources = new ArrayList<>();
        for (RetrievedChunk c : chunks) {
            sources.add(new ChatMessage.SourceRef(c.chunkId(), c.docId(), c.docTitle()));
        }
        return sources;
    }
}
