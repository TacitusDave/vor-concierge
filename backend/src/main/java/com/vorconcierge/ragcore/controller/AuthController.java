package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.dto.LoginRequest;
import com.vorconcierge.ragcore.service.AuthService;
import com.vorconcierge.ragcore.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse response) {
        var result = authService.login(
                request.email(),
                request.password(),
                request.totp(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                response
        );
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "username", result.username(),
                "orgId", result.orgId(),
                "isPlatformOperator", result.isPlatformOperator()
        ));
    }

    @PutMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(HttpServletRequest httpRequest, HttpServletResponse response) {
        var current = SecurityUtils.currentUser();
        var result = authService.refresh(
                current.userId(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                response
        );
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "username", result.username(),
                "orgId", result.orgId(),
                "isPlatformOperator", result.isPlatformOperator()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        var user = SecurityUtils.currentUser();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("userId", user.userId());
        body.put("username", user.username());
        body.put("orgId", user.orgId());
        body.put("departmentId", user.departmentId());
        body.put("roleId", user.roleId());
        body.put("hierarchyLevel", user.hierarchyLevel());
        body.put("roleCapabilityMask", user.roleCapabilityMask());
        body.put("isPlatformOperator", user.isPlatformOperator());
        return ResponseEntity.ok(body);
    }
}
