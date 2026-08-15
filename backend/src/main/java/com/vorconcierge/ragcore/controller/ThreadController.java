package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.model.ChatMessage;
import com.vorconcierge.ragcore.model.ThreadRecord;
import com.vorconcierge.ragcore.repository.ThreadRepository;
import com.vorconcierge.ragcore.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/threads")
public class ThreadController {

    private final ThreadRepository threadRepository;

    public ThreadController(ThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    @GetMapping
    public ResponseEntity<List<ThreadRecord>> listThreads() {
        var user = SecurityUtils.currentUser();
        return ResponseEntity.ok(threadRepository.findByUserId(user.userId()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(@PathVariable Long id) {
        var user = SecurityUtils.currentUser();
        if (!threadRepository.belongsToUser(id, user.userId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(threadRepository.findMessages(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> createThread(@RequestBody Map<String, String> body) {
        var user = SecurityUtils.currentUser();
        String title = body.getOrDefault("title", "New conversation");
        long id = threadRepository.create(user.orgId(), user.userId(), title);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> renameThread(@PathVariable Long id, @RequestBody Map<String, String> body) {
        var user = SecurityUtils.currentUser();
        if (!threadRepository.belongsToUser(id, user.userId())) {
            return ResponseEntity.notFound().build();
        }
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title must not be blank"));
        }
        threadRepository.updateTitle(id, title.trim());
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteThread(@PathVariable Long id) {
        var user = SecurityUtils.currentUser();
        if (!threadRepository.belongsToUser(id, user.userId())) {
            return ResponseEntity.notFound().build();
        }
        threadRepository.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
