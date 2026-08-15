package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.dto.ConfigUpdateRequest;
import com.vorconcierge.ragcore.service.AdminService;
import com.vorconcierge.ragcore.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        SecurityUtils.requirePlatformOperator();
        return ResponseEntity.ok(adminService.health());
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        SecurityUtils.requirePlatformOperator();
        return ResponseEntity.ok(adminService.config());
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, String>> updateConfig(@RequestBody ConfigUpdateRequest request) {
        SecurityUtils.requirePlatformOperator();
        if (request.settings() != null) {
            adminService.updateConfig(request.settings());
        }
        return ResponseEntity.ok(adminService.config());
    }

    @PostMapping("/kill")
    public ResponseEntity<Map<String, String>> kill() {
        SecurityUtils.requirePlatformOperator();
        adminService.kill();
        return ResponseEntity.ok(Map.of("status", "killed"));
    }
}
