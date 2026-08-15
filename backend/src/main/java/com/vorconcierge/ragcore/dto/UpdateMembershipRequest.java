package com.vorconcierge.ragcore.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMembershipRequest(
        Long departmentId,
        @NotNull Long roleId
) {}
