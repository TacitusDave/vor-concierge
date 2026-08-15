package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.dto.ChatRequest;
import com.vorconcierge.ragcore.service.RagService;
import com.vorconcierge.ragcore.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        var user = SecurityUtils.currentUser();
        return ragService.chat(user, request.threadId(), request.query());
    }
}
