package com.vorconcierge.ragcore.service;

import com.sun.management.OperatingSystemMXBean;
import com.vorconcierge.ragcore.config.ConciergeProperties;
import com.vorconcierge.ragcore.repository.ChunkRepository;
import com.vorconcierge.ragcore.repository.DocumentRepository;
import com.vorconcierge.ragcore.repository.ThreadRepository;
import com.vorconcierge.ragcore.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@Service
public class AdminService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final UserRepository userRepository;
    private final ThreadRepository threadRepository;
    private final OllamaClient ollamaClient;
    private final RuntimeConfigService configService;
    private final SseRegistryService sseRegistry;
    private final ConciergeProperties properties;

    public AdminService(DocumentRepository documentRepository, ChunkRepository chunkRepository,
                        UserRepository userRepository, ThreadRepository threadRepository,
                        OllamaClient ollamaClient, RuntimeConfigService configService,
                        SseRegistryService sseRegistry, ConciergeProperties properties) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.userRepository = userRepository;
        this.threadRepository = threadRepository;
        this.ollamaClient = ollamaClient;
        this.configService = configService;
        this.sseRegistry = sseRegistry;
        this.properties = properties;
    }

    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        var runtime = Runtime.getRuntime();
        health.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        health.put("heapUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        health.put("heapMaxMb", runtime.maxMemory() / (1024 * 1024));
        health.put("loadAvg", systemLoad());
        health.put("activeUsers", userRepository.countActiveSessions());
        health.put("tokensToday", threadRepository.sumTokensTodayGlobal());
        health.put("documents", documentRepository.count());
        health.put("chunks", chunkRepository.count());
        health.put("pendingDocuments", documentRepository.countByStatus("PENDING"));
        health.put("processingDocuments", documentRepository.countByStatus("PROCESSING"));
        health.put("activeSseConnections", sseRegistry.activeCount());
        health.put("ollamaHealthy", ollamaClient.isHealthy());
        return health;
    }

    private double systemLoad() {
        var os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        double load = os.getCpuLoad();
        return load >= 0 ? Math.round(load * 100.0) / 100.0 : 0.0;
    }

    public Map<String, String> config() {
        Map<String, String> result = new HashMap<>(configService.getAll());
        result.put("llmModel", properties.ollama().llmModel());
        result.put("embeddingModel", properties.ollama().embeddingModel());
        return result;
    }

    public void updateConfig(Map<String, String> settings) {
        configService.update(settings);
    }

    public void kill() {
        sseRegistry.killAll();
        configService.reload();
    }
}
