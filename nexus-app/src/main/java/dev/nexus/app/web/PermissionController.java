package dev.nexus.app.web;

import dev.nexus.core.db.entity.NexusPermission;
import dev.nexus.core.db.repository.NexusPermissionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final NexusPermissionRepository permissionRepo;

    public PermissionController(NexusPermissionRepository permissionRepo) {
        this.permissionRepo = permissionRepo;
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, String>> approve(@PathVariable UUID id) {
        NexusPermission permission = permissionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + id));

        if (!"PENDING".equals(permission.getStatus())) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Permission is not pending (current status: %s)".formatted(permission.getStatus())));
        }

        permission.setStatus("APPROVED");
        permissionRepo.save(permission);

        return ResponseEntity.ok(Map.of(
                "status", "approved",
                "id", id.toString(),
                "plugin", permission.getPlugin(),
                "endpoint", permission.getEndpoint()));
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<Map<String, String>> deny(@PathVariable UUID id) {
        NexusPermission permission = permissionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + id));

        if (!"PENDING".equals(permission.getStatus())) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Permission is not pending (current status: %s)".formatted(permission.getStatus())));
        }

        permission.setStatus("DENIED");
        permissionRepo.save(permission);

        return ResponseEntity.ok(Map.of(
                "status", "denied",
                "id", id.toString(),
                "plugin", permission.getPlugin(),
                "endpoint", permission.getEndpoint()));
    }
}
