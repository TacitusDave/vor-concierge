package com.vorconcierge.ragcore.ingestion;

import com.vorconcierge.ragcore.model.DocumentRecord;
import com.vorconcierge.ragcore.repository.ChunkRepository;
import com.vorconcierge.ragcore.repository.DocumentRepository;
import com.vorconcierge.ragcore.service.OllamaClient;
import com.vorconcierge.ragcore.service.StorageService;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final StorageService storageService;
    private final OllamaClient ollamaClient;
    private final SlidingWindowChunker chunker;
    private final Map<Long, SseEmitter> progressEmitters = new ConcurrentHashMap<>();

    public DocumentIngestionService(DocumentRepository documentRepository, ChunkRepository chunkRepository,
                                    StorageService storageService, OllamaClient ollamaClient,
                                    SlidingWindowChunker chunker) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storageService = storageService;
        this.ollamaClient = ollamaClient;
        this.chunker = chunker;
    }

    public record UploadResult(long documentId, String status) {}

    public UploadResult upload(String title, byte[] content, String filename, Long orgId, Long departmentId,
                               Long userId, int minimumRoleLevel) {
        String hash = sha256(content);
        var existing = documentRepository.findByHash(orgId, hash);
        if (existing.isPresent()) {
            throw new DuplicateDocumentException("Document already exists: " + existing.get().id());
        }
        try (InputStream in = new java.io.ByteArrayInputStream(content)) {
            String uri = storageService.store(hash, filename, in, content.length);
            long docId = documentRepository.insert(orgId, departmentId, title, hash, uri, content.length, userId, minimumRoleLevel);
            processAsync(docId);
            return new UploadResult(docId, "PENDING");
        } catch (DuplicateDocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Upload failed", e);
        }
    }

    public void reprocess(Long docId) {
        documentRepository.findById(docId).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        chunkRepository.deleteByDocId(docId);
        documentRepository.updateStatus(docId, "PENDING", null);
        processAsync(docId);
    }

    @Async("ingestionExecutor")
    public void processAsync(Long docId) {
        processDocument(docId);
    }

    public void processDocument(Long docId) {
        DocumentRecord doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) return;

        documentRepository.updateStatus(docId, "PROCESSING", null);
        emitProgress(docId, "PROCESSING", 10);

        try (InputStream in = storageService.retrieve(doc.fileUri())) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            parser.parse(in, handler, metadata);

            String text = handler.toString();
            List<String> chunks = chunker.chunk(text);
            if (chunks.isEmpty()) {
                documentRepository.updateStatus(docId, "ERROR", "No text content extracted");
                emitProgress(docId, "ERROR", 100);
                return;
            }

            chunkRepository.deleteByDocId(docId);
            int total = chunks.size();
            for (int i = 0; i < total; i++) {
                String chunkText = chunks.get(i);
                long chunkId = chunkRepository.insertChunk(docId, doc.orgId(), doc.departmentId(), i, chunkText, doc.minimumRoleLevel());
                float[] embedding = ollamaClient.embed(chunkText);
                chunkRepository.insertEmbedding(chunkId, embedding);
                int progress = 20 + (int) ((i + 1) * 70.0 / total);
                emitProgress(docId, "PROCESSING", progress);
            }

            documentRepository.updateStatus(docId, "SYNCED", null);
            emitProgress(docId, "SYNCED", 100);
        } catch (Exception e) {
            log.error("Document processing failed for {}", docId, e);
            documentRepository.updateStatus(docId, "ERROR", e.getMessage());
            emitProgress(docId, "ERROR", 100);
        }
    }

    public SseEmitter subscribeProgress(Long docId) {
        SseEmitter emitter = new SseEmitter(3600000L);
        progressEmitters.put(docId, emitter);
        emitter.onCompletion(() -> progressEmitters.remove(docId));
        emitter.onTimeout(() -> progressEmitters.remove(docId));
        emitter.onError(e -> progressEmitters.remove(docId));
        return emitter;
    }

    private void emitProgress(Long docId, String status, int progress) {
        SseEmitter emitter = progressEmitters.get(docId);
        if (emitter == null) return;
        try {
            String data = String.format("{\"docId\":%d,\"status\":\"%s\",\"progress\":%d}", docId, status, progress);
            emitter.send(SseEmitter.event().name("progress").data(data));
            if ("SYNCED".equals(status) || "ERROR".equals(status)) {
                emitter.complete();
                progressEmitters.remove(docId);
            }
        } catch (Exception e) {
            progressEmitters.remove(docId);
        }
    }

    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }

    public static class DuplicateDocumentException extends RuntimeException {
        public DuplicateDocumentException(String message) {
            super(message);
        }
    }
}
