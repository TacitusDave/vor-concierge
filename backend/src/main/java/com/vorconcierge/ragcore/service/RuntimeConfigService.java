package com.vorconcierge.ragcore.service;

import com.vorconcierge.ragcore.repository.ConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RuntimeConfigService {

    private final ConfigRepository configRepository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public RuntimeConfigService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @PostConstruct
    public void load() {
        cache.clear();
        cache.putAll(configRepository.findAll());
    }

    public Map<String, String> getAll() {
        return Map.copyOf(cache);
    }

    public double getTemperature() {
        return parseDouble(cache.getOrDefault("temperature", "0.7"));
    }

    public double getTopP() {
        return parseDouble(cache.getOrDefault("top_p", "0.9"));
    }

    public double getFrequencyPenalty() {
        return parseDouble(cache.getOrDefault("frequency_penalty", "0.0"));
    }

    public void update(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
            configRepository.upsert(entry.getKey(), entry.getValue());
        }
    }

    public void reload() {
        load();
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.7;
        }
    }
}
