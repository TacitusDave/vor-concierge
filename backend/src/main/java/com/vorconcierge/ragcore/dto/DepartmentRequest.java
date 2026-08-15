package com.vorconcierge.ragcore.dto;

import jakarta.validation.constraints.NotBlank;

public record DepartmentRequest(
        @NotBlank String name,
        Long parentDepartmentId
) {}
