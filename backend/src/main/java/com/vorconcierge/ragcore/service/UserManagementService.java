package com.vorconcierge.ragcore.service;

import com.vorconcierge.ragcore.dto.InviteUserRequest;
import com.vorconcierge.ragcore.dto.UpdateMembershipRequest;
import com.vorconcierge.ragcore.model.Role;
import com.vorconcierge.ragcore.repository.DepartmentRepository;
import com.vorconcierge.ragcore.repository.RoleRepository;
import com.vorconcierge.ragcore.repository.UserRepository;
import com.vorconcierge.ragcore.security.AuthenticatedUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * There's no email provider wired up yet (that's Phase 5), so "inviting" a user creates a real
 * account with a real bcrypt-hashed random password immediately and hands the plaintext back to
 * the inviting admin once, to relay out-of-band — not a fake/placeholder account. The same
 * escalation guard as role editing applies: you cannot hand out a role more capable or more
 * senior than your own.
 */
@Service
public class UserManagementService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepository, RoleRepository roleRepository,
                                 DepartmentRepository departmentRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record InviteResult(long userId, String temporaryPassword) {}

    public InviteResult inviteUser(AuthenticatedUser actor, InviteUserRequest request) {
        Role role = resolveAssignableRole(actor, request.roleId());
        validateDepartment(actor, request.departmentId());

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("A user with this email already exists");
        }
        if (userRepository.existsByOrgIdAndUsername(actor.orgId(), request.username())) {
            throw new IllegalArgumentException("That username is already taken in this organization");
        }

        String tempPassword = generatePassword();
        long userId = userRepository.insert(actor.orgId(), request.departmentId(), role.id(),
                request.username(), request.email(), passwordEncoder.encode(tempPassword), false);
        return new InviteResult(userId, tempPassword);
    }

    public void updateMembership(AuthenticatedUser actor, Long targetUserId, UpdateMembershipRequest request) {
        resolveAssignableRole(actor, request.roleId());
        validateDepartment(actor, request.departmentId());
        userRepository.updateMembership(targetUserId, request.departmentId(), request.roleId());
    }

    private Role resolveAssignableRole(AuthenticatedUser actor, Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role"));
        if (!role.orgId().equals(actor.orgId())) {
            throw new SecurityException("Role does not belong to your organization");
        }
        if (!actor.isPlatformOperator()) {
            if ((role.capabilityMask() & ~actor.roleCapabilityMask()) != 0) {
                throw new SecurityException("Cannot assign a role with capabilities you do not hold yourself");
            }
            if (role.hierarchyLevel() > actor.hierarchyLevel()) {
                throw new SecurityException("Cannot assign a role more senior than your own");
            }
        }
        return role;
    }

    private void validateDepartment(AuthenticatedUser actor, Long departmentId) {
        if (departmentId == null) {
            return;
        }
        var department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown department"));
        if (!department.orgId().equals(actor.orgId())) {
            throw new SecurityException("Department does not belong to your organization");
        }
    }

    private String generatePassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
