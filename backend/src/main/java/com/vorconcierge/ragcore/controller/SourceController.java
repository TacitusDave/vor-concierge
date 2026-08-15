package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.ingestion.DocumentIngestionService;
import com.vorconcierge.ragcore.model.DocumentRecord;
import com.vorconcierge.ragcore.repository.DocumentRepository;
import com.vorconcierge.ragcore.security.Capability;
import com.vorconcierge.ragcore.service.StorageService;
import com.vorconcierge.ragcore.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final DocumentRepository documentRepository;
    private final DocumentIngestionService ingestionService;
    private final StorageService storageService;

    public SourceController(DocumentRepository documentRepository,
                            DocumentIngestionService ingestionService,
                            StorageService storageService) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentRecord>> list() {
        var user = SecurityUtils.currentUser();
        return ResponseEntity.ok(documentRepository.findByOrgId(user.orgId()));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "minimumRoleLevel", defaultValue = "0") int minimumRoleLevel) {
        SecurityUtils.requireCapability(Capability.UPLOAD_DOCUMENTS);
        try {
            byte[] content = file.getBytes();
            String docTitle = title != null && !title.isBlank() ? title : file.getOriginalFilename();
            var user = SecurityUtils.currentUser();
            var result = ingestionService.upload(docTitle, content, file.getOriginalFilename(),
                    user.orgId(), departmentId, user.userId(), minimumRoleLevel);
            return ResponseEntity.ok(Map.of("documentId", result.documentId(), "status", result.status()));
        } catch (DocumentIngestionService.DuplicateDocumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to read uploaded file: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        SecurityUtils.requireCapability(Capability.UPLOAD_DOCUMENTS);
        var doc = documentRepository.findById(id).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        SecurityUtils.requireSameOrg(doc.orgId());
        storageService.delete(doc.fileUri());
        documentRepository.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<Map<String, String>> reprocess(@PathVariable Long id) {
        SecurityUtils.requireCapability(Capability.UPLOAD_DOCUMENTS);
        var doc = documentRepository.findById(id).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        SecurityUtils.requireSameOrg(doc.orgId());
        ingestionService.reprocess(id);
        return ResponseEntity.ok(Map.of("status", "reprocessing"));
    }

    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progress(@PathVariable Long id) {
        SecurityUtils.requireCapability(Capability.UPLOAD_DOCUMENTS);
        var doc = documentRepository.findById(id).orElse(null);
        if (doc != null) {
            SecurityUtils.requireSameOrg(doc.orgId());
        }
        return ingestionService.subscribeProgress(id);
    }
}
