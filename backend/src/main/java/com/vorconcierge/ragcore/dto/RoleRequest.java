package com.vorconcierge.ragcore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RoleRequest(
        @NotBlank String name,
        @NotNull Integer hierarchyLevel,
        List<String> capabilities
) {}
