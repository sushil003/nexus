package dev.nexus.app.web;

import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.PluginRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    private final PluginRegistry pluginRegistry;

    public CredentialController(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    @PostMapping("/{pluginId}")
    public ResponseEntity<Map<String, String>> setCredentials(
            @PathVariable String pluginId,
            @RequestBody Map<String, String> credentials) {

        NexusPlugin plugin = pluginRegistry.get(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plugin: " + pluginId));

        // TODO: validate against plugin.getAuthConfig().requiredFields()
        // TODO: encrypt with DEK and store in nexus_accounts.config

        return ResponseEntity.ok(Map.of("status", "configured", "plugin", pluginId));
    }
}
