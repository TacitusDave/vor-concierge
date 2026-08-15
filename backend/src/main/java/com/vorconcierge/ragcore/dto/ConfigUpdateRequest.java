package com.vorconcierge.ragcore.dto;

import java.util.Map;

public record ConfigUpdateRequest(Map<String, String> settings) {}
