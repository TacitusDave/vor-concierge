package com.vorconcierge.ragcore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteUserRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        Long departmentId,
        @NotNull Long roleId
) {}
