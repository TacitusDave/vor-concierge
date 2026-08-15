package com.vorconcierge.ragcore.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        Long threadId,
        @NotBlank String query
) {}
