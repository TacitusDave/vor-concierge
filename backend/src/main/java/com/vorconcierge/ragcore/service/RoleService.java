package com.vorconcierge.ragcore.service;

import com.vorconcierge.ragcore.dto.RoleRequest;
import com.vorconcierge.ragcore.repository.RoleRepository;
import com.vorconcierge.ragcore.security.AuthenticatedUser;
import com.vorconcierge.ragcore.security.Capability;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Roles are org-configurable, but a role editor must never be able to grant a role more
 * capabilities, or more seniority, than they hold themselves — otherwise MANAGE_ROLES becomes a
 * privilege-escalation path (create a role with MANAGE_BILLING, assign it to yourself).
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public long createRole(AuthenticatedUser actor, RoleRequest request) {
        long capabilityMask = parseCapabilities(request.capabilities());
        guardEscalation(actor, request.hierarchyLevel(), capabilityMask);
        return roleRepository.insert(actor.orgId(), request.name(), request.hierarchyLevel(), capabilityMask, false);
    }

    public void updateRole(AuthenticatedUser actor, Long roleId, RoleRequest request) {
        long capabilityMask = parseCapabilities(request.capabilities());
        guardEscalation(actor, request.hierarchyLevel(), capabilityMask);
        roleRepository.update(roleId, request.name(), request.hierarchyLevel(), capabilityMask);
    }

    private void guardEscalation(AuthenticatedUser actor, int hierarchyLevel, long capabilityMask) {
        if (actor.isPlatformOperator()) {
            return;
        }
        if ((capabilityMask & ~actor.roleCapabilityMask()) != 0) {
            throw new SecurityException("Cannot grant capabilities you do not hold yourself");
        }
        if (hierarchyLevel > actor.hierarchyLevel()) {
            throw new SecurityException("Cannot create a role more senior than your own");
        }
    }

    private long parseCapabilities(List<String> names) {
        if (names == null) {
            return 0;
        }
        long mask = 0;
        for (String name : names) {
            try {
                mask |= Capability.valueOf(name.trim().toUpperCase()).getBit();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown capability: " + name);
            }
        }
        return mask;
    }
}
