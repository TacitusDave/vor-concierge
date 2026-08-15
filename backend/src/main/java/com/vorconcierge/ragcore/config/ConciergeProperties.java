package com.vorconcierge.ragcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "concierge")
public record ConciergeProperties(
        Jwt jwt,
        Ollama ollama,
        Storage storage,
        Chunker chunker,
        Cors cors,
        Bootstrap bootstrap
) {
    public record Jwt(String privateKeyPath, String publicKeyPath, String generatedKeyPath,
                       String cookieName, int expirationHours, boolean cookieSecure) {}
    public record Ollama(String baseUrl, String llmModel, String embeddingModel, int embeddingDimension) {}
    public record Storage(String type, String localPath, Minio minio) {
        public record Minio(String endpoint, String accessKey, String secretKey, String bucket) {}
    }
    public record Chunker(int maxTokens, int overlapTokens) {}
    public record Cors(String allowedOrigins) {}
    public record Bootstrap(String adminUsername, String adminEmail, String adminPassword,
                             boolean createTestOrg, String testOrgName, String testOrgSlug,
                             String testAdminEmail, String testAdminPassword) {}
}
