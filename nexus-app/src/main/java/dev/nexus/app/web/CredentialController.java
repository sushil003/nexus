package dev.nexus.app.web;

import dev.nexus.core.auth.KeyManager;
import dev.nexus.core.auth.KeyManagerFactory;
import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.PluginAuthConfig;
import dev.nexus.core.plugin.PluginRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    private final PluginRegistry pluginRegistry;
    private final KeyManagerFactory keyManagerFactory;

    public CredentialController(PluginRegistry pluginRegistry,
                                KeyManagerFactory keyManagerFactory) {
        this.pluginRegistry = pluginRegistry;
        this.keyManagerFactory = keyManagerFactory;
    }

    @PostMapping("/{pluginId}")
    public ResponseEntity<Map<String, String>> setCredentials(
            @PathVariable String pluginId,
            @RequestBody Map<String, String> credentials) {

        NexusPlugin plugin = pluginRegistry.get(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plugin: " + pluginId));

        // Validate against plugin auth config
        PluginAuthConfig authConfig = plugin.getAuthConfig();
        List<String> missing = new ArrayList<>();
        for (String required : authConfig.requiredFields()) {
            if (!credentials.containsKey(required) || credentials.get(required).isBlank()) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Missing required fields: " + String.join(", ", missing)));
        }

        // Encrypt and store via KeyManager
        KeyManager keyManager = keyManagerFactory.createForPlugin(pluginId, "default");
        credentials.forEach(keyManager::setField);

        return ResponseEntity.ok(Map.of("status", "configured", "plugin", pluginId));
    }
}
