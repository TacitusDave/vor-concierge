package com.vorconcierge.ragcore.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseRegistryService {

    private final Set<SseEmitter> activeEmitters = ConcurrentHashMap.newKeySet();

    public void register(SseEmitter emitter) {
        activeEmitters.add(emitter);
        emitter.onCompletion(() -> activeEmitters.remove(emitter));
        emitter.onTimeout(() -> activeEmitters.remove(emitter));
        emitter.onError(e -> activeEmitters.remove(emitter));
    }

    public void killAll() {
        for (SseEmitter emitter : activeEmitters) {
            try {
                emitter.completeWithError(new IllegalStateException("Kill switch activated"));
            } catch (Exception ignored) {
            }
        }
        activeEmitters.clear();
    }

    public int activeCount() {
        return activeEmitters.size();
    }
}
