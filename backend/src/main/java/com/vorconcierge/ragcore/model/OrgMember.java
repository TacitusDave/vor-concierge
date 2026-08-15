package com.vorconcierge.ragcore.model;

import java.time.Instant;

/** A user row joined with its department/role names, for org member management screens. */
public record OrgMember(
        Long id,
        String username,
        String email,
        boolean enabled,
        Long departmentId,
        String departmentName,
        Long roleId,
        String roleName,
        Instant createdAt
) {}
