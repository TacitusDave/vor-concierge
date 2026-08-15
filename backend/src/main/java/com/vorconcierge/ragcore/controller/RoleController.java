package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.dto.RoleRequest;
import com.vorconcierge.ragcore.model.Role;
import com.vorconcierge.ragcore.repository.RoleRepository;
import com.vorconcierge.ragcore.security.Capability;
import com.vorconcierge.ragcore.service.RoleService;
import com.vorconcierge.ragcore.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleRepository roleRepository;
    private final RoleService roleService;

    public RoleController(RoleRepository roleRepository, RoleService roleService) {
        this.roleRepository = roleRepository;
        this.roleService = roleService;
    }

    /** The fixed platform capability catalog, for rendering a role-builder's capability picker. */
    @GetMapping("/capabilities")
    public ResponseEntity<List<String>> capabilities() {
        SecurityUtils.currentUser();
        return ResponseEntity.ok(Arrays.stream(Capability.values()).map(Enum::name).toList());
    }

    @GetMapping
    public ResponseEntity<List<Role>> list() {
        var user = SecurityUtils.currentUser();
        return ResponseEntity.ok(roleRepository.findByOrgId(user.orgId()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody RoleRequest request) {
        SecurityUtils.requireCapability(Capability.MANAGE_ROLES);
        var user = SecurityUtils.currentUser();
        long id = roleService.createRole(user, request);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        SecurityUtils.requireCapability(Capability.MANAGE_ROLES);
        var user = SecurityUtils.currentUser();
        var existing = roleRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        SecurityUtils.requireSameOrg(existing.orgId());
        roleService.updateRole(user, id, request);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        SecurityUtils.requireCapability(Capability.MANAGE_ROLES);
        var existing = roleRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        SecurityUtils.requireSameOrg(existing.orgId());
        if (!roleRepository.delete(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Role is still assigned to one or more users"));
        }
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
