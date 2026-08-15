package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.repository.*;
import com.vorconcierge.ragcore.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Org-scoped overview for the executive/owner dashboard landing page. */
@RestController
@RequestMapping("/api/v1/organization")
public class OrganizationController {

    private final OrganizationRepository organizationRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final ThreadRepository threadRepository;

    public OrganizationController(OrganizationRepository organizationRepository,
                                  SubscriptionPlanRepository subscriptionPlanRepository,
                                  UserRepository userRepository, DepartmentRepository departmentRepository,
                                  DocumentRepository documentRepository, ChunkRepository chunkRepository,
                                  ThreadRepository threadRepository) {
        this.organizationRepository = organizationRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.threadRepository = threadRepository;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        var user = SecurityUtils.currentUser();
        var org = organizationRepository.findById(user.orgId())
                .orElseThrow(() -> new IllegalStateException("Current user's organization not found"));
        var plan = subscriptionPlanRepository.findById(org.planId())
                .orElseThrow(() -> new IllegalStateException("Organization's plan not found"));

        Map<String, Object> body = new HashMap<>();
        body.put("organizationName", org.name());
        body.put("subscriptionStatus", org.subscriptionStatus());
        body.put("planCode", plan.code());
        body.put("planName", plan.name());
        body.put("maxUsers", plan.maxUsers());
        body.put("maxDocuments", plan.maxDocuments());
        body.put("maxStorageBytes", plan.maxStorageBytes());
        body.put("memberCount", userRepository.findOrgMembers(user.orgId()).size());
        body.put("departmentCount", departmentRepository.findByOrgId(user.orgId()).size());
        body.put("documentCount", documentRepository.countByOrgId(user.orgId()));
        body.put("chunkCount", chunkRepository.countByOrgId(user.orgId()));
        body.put("tokensToday", threadRepository.sumTokensTodayForOrg(user.orgId()));
        return ResponseEntity.ok(body);
    }
}
