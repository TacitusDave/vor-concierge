package com.vorconcierge.ragcore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vorconcierge.ragcore.config.ConciergeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final String baseUrl;
    private final String llmModel;
    private final String embeddingModel;
    private final int embeddingDimension;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaClient(ConciergeProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = properties.ollama().baseUrl();
        this.llmModel = properties.ollama().llmModel();
        this.embeddingModel = properties.ollama().embeddingModel();
        this.embeddingDimension = properties.ollama().embeddingDimension();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public float[] embed(String text) {
        try {
            Map<String, Object> body = Map.of("model", embeddingModel, "prompt", text);
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama embedding failed: " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddingNode = root.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new IllegalStateException("Invalid embedding response");
            }
            float[] vec = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vec[i] = (float) embeddingNode.get(i).asDouble();
            }
            return normalizeDimension(vec);
        } catch (Exception e) {
            log.error("Embedding request failed", e);
            throw new IllegalStateException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    public void generateStream(String prompt, double temperature, double topP, double frequencyPenalty,
                               Consumer<String> onToken, Runnable onComplete) {
        try {
            Map<String, Object> options = Map.of(
                    "temperature", temperature,
                    "top_p", topP,
                    "frequency_penalty", frequencyPenalty
            );
            Map<String, Object> body = Map.of(
                    "model", llmModel,
                    "prompt", prompt,
                    "stream", true,
                    "options", options
            );
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(resp -> {
                        if (resp.statusCode() != 200) {
                            onToken.accept("[Error: Ollama generation failed]");
                            onComplete.run();
                            return;
                        }
                        boolean[] completed = {false};
                        resp.body().forEach(line -> {
                            if (line.isBlank() || completed[0]) return;
                            try {
                                JsonNode node = objectMapper.readTree(line);
                                if (node.has("response")) {
                                    onToken.accept(node.get("response").asText(""));
                                }
                                if (node.has("done") && node.get("done").asBoolean()) {
                                    completed[0] = true;
                                    onComplete.run();
                                }
                            } catch (Exception ex) {
                                log.debug("Skipping non-json line: {}", line);
                            }
                        });
                        if (!completed[0]) {
                            onComplete.run();
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Generation stream failed", ex);
                        onToken.accept("[Error: " + ex.getMessage() + "]");
                        onComplete.run();
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to start generation", e);
            onToken.accept("[Error: " + e.getMessage() + "]");
            onComplete.run();
        }
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private float[] normalizeDimension(float[] vec) {
        if (vec.length == embeddingDimension) {
            return vec;
        }
        float[] result = new float[embeddingDimension];
        System.arraycopy(vec, 0, result, 0, Math.min(vec.length, embeddingDimension));
        return result;
    }
}
