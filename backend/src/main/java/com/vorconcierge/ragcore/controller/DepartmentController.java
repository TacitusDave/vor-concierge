package com.vorconcierge.ragcore.controller;

import com.vorconcierge.ragcore.dto.DepartmentRequest;
import com.vorconcierge.ragcore.model.Department;
import com.vorconcierge.ragcore.repository.DepartmentRepository;
import com.vorconcierge.ragcore.security.Capability;
import com.vorconcierge.ragcore.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @GetMapping
    public ResponseEntity<List<Department>> list() {
        var user = SecurityUtils.currentUser();
        return ResponseEntity.ok(departmentRepository.findByOrgId(user.orgId()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody DepartmentRequest request) {
        SecurityUtils.requireCapability(Capability.MANAGE_DEPARTMENTS);
        var user = SecurityUtils.currentUser();
        if (request.parentDepartmentId() != null) {
            var parent = departmentRepository.findById(request.parentDepartmentId()).orElse(null);
            if (parent == null || !parent.orgId().equals(user.orgId())) {
                return ResponseEntity.badRequest().build();
            }
        }
        long id = departmentRepository.insert(user.orgId(), request.parentDepartmentId(), request.name());
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> rename(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        SecurityUtils.requireCapability(Capability.MANAGE_DEPARTMENTS);
        var dept = departmentRepository.findById(id).orElse(null);
        if (dept == null) {
            return ResponseEntity.notFound().build();
        }
        SecurityUtils.requireSameOrg(dept.orgId());
        departmentRepository.rename(id, request.name());
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        SecurityUtils.requireCapability(Capability.MANAGE_DEPARTMENTS);
        var dept = departmentRepository.findById(id).orElse(null);
        if (dept == null) {
            return ResponseEntity.notFound().build();
        }
        SecurityUtils.requireSameOrg(dept.orgId());
        if (!departmentRepository.delete(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Department still has sub-departments, members, or documents assigned to it"));
        }
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
