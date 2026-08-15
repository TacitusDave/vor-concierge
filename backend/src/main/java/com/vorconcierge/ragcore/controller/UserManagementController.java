package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.dto.InviteUserRequest;
import com.vorconcierge.ragcore.dto.UpdateMembershipRequest;
import com.vorconcierge.ragcore.model.OrgMember;
import com.vorconcierge.ragcore.repository.UserRepository;
import com.vorconcierge.ragcore.security.Capability;
import com.vorconcierge.ragcore.service.UserManagementService;
import com.vorconcierge.ragcore.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {

    private final UserRepository userRepository;
    private final UserManagementService userManagementService;

    public UserManagementController(UserRepository userRepository, UserManagementService userManagementService) {
        this.userRepository = userRepository;
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public ResponseEntity<List<OrgMember>> list() {
        SecurityUtils.requireCapability(Capability.MANAGE_USERS);
        var user = SecurityUtils.currentUser();
        return ResponseEntity.ok(userRepository.findOrgMembers(user.orgId()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> invite(@Valid @RequestBody InviteUserRequest request) {
        SecurityUtils.requireCapability(Capability.INVITE_USERS);
        var actor = SecurityUtils.currentUser();
        var result = userManagementService.inviteUser(actor, request);
        return ResponseEntity.ok(Map.of(
                "userId", result.userId(),
                "temporaryPassword", result.temporaryPassword()
        ));
    }

    @PutMapping("/{id}/membership")
    public ResponseEntity<Map<String, String>> updateMembership(@PathVariable Long id,
                                                                 @Valid @RequestBody UpdateMembershipRequest request) {
        SecurityUtils.requireCapability(Capability.MANAGE_USERS);
        var actor = SecurityUtils.currentUser();
        var target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        SecurityUtils.requireSameOrg(target.orgId());
        userManagementService.updateMembership(actor, id, request);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<Map<String, String>> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        SecurityUtils.requireCapability(Capability.MANAGE_USERS);
        var target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        SecurityUtils.requireSameOrg(target.orgId());
        boolean enabled = body.getOrDefault("enabled", true);
        userRepository.updateEnabled(id, enabled);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }
}
