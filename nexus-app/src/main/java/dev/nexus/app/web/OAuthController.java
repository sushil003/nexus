package dev.nexus.app.web;

import dev.nexus.core.auth.KeyManager;
import dev.nexus.core.auth.KeyManagerFactory;
import dev.nexus.core.auth.TokenRefreshService;
import dev.nexus.core.plugin.NexusPlugin;
import dev.nexus.core.plugin.OAuthConfig;
import dev.nexus.core.plugin.PluginRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final PluginRegistry pluginRegistry;
    private final KeyManagerFactory keyManagerFactory;
    private final TokenRefreshService tokenRefreshService;

    public OAuthController(PluginRegistry pluginRegistry,
                           KeyManagerFactory keyManagerFactory,
                           TokenRefreshService tokenRefreshService) {
        this.pluginRegistry = pluginRegistry;
        this.keyManagerFactory = keyManagerFactory;
        this.tokenRefreshService = tokenRefreshService;
    }

    @GetMapping("/{pluginId}/start")
    public ResponseEntity<Map<String, String>> startOAuth(
            @PathVariable String pluginId,
            HttpServletRequest request) {

        NexusPlugin plugin = pluginRegistry.get(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plugin: " + pluginId));

        OAuthConfig oauthConfig = plugin.getOAuthConfig()
                .orElseThrow(() -> new IllegalArgumentException("Plugin does not support OAuth: " + pluginId));

        String redirectUri = buildRedirectUri(request, pluginId);

        String authUrl = UriComponentsBuilder.fromUriString(oauthConfig.authUrl())
                .queryParam("client_id", getClientId(pluginId))
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", oauthConfig.scopes()))
                .build()
                .toUriString();

        // Add any extra auth params
        if (oauthConfig.authParams() != null) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authUrl);
            oauthConfig.authParams().forEach(builder::queryParam);
            authUrl = builder.build().toUriString();
        }

        log.info("Starting OAuth flow for plugin: {}", pluginId);

        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    @GetMapping("/{pluginId}/callback")
    public ResponseEntity<Map<String, String>> handleCallback(
            @PathVariable String pluginId,
            @RequestParam String code,
            HttpServletRequest request) {

        NexusPlugin plugin = pluginRegistry.get(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown plugin: " + pluginId));

        OAuthConfig oauthConfig = plugin.getOAuthConfig()
                .orElseThrow(() -> new IllegalArgumentException("Plugin does not support OAuth: " + pluginId));

        String redirectUri = buildRedirectUri(request, pluginId);
        KeyManager keyManager = keyManagerFactory.createForPlugin(pluginId, "default");

        String clientId = getClientId(pluginId);
        String clientSecret = getClientSecret(pluginId);

        TokenRefreshService.TokenResult result = tokenRefreshService.exchangeCode(
                oauthConfig.tokenUrl(), code, redirectUri, clientId, clientSecret);

        // Store tokens
        keyManager.setField("access_token", result.accessToken());
        keyManager.setField("token_expires_at", result.expiresAt().toString());
        if (result.refreshToken() != null) {
            keyManager.setField("refresh_token", result.refreshToken());
        }

        log.info("OAuth flow completed for plugin: {}", pluginId);

        return ResponseEntity.ok(Map.of(
                "status", "configured",
                "plugin", pluginId,
                "message", "OAuth tokens stored successfully"));
    }

    private String getClientId(String pluginId) {
        KeyManager keyManager = keyManagerFactory.createForPlugin(pluginId, "default");
        return keyManager.getField("client_id")
                .orElseThrow(() -> new IllegalStateException(
                        "client_id not configured for plugin: " + pluginId +
                        ". Set it via POST /api/credentials/" + pluginId));
    }

    private String getClientSecret(String pluginId) {
        KeyManager keyManager = keyManagerFactory.createForPlugin(pluginId, "default");
        return keyManager.getField("client_secret")
                .orElseThrow(() -> new IllegalStateException(
                        "client_secret not configured for plugin: " + pluginId +
                        ". Set it via POST /api/credentials/" + pluginId));
    }

    private String buildRedirectUri(HttpServletRequest request, String pluginId) {
        return UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                .replacePath("/oauth/" + pluginId + "/callback")
                .build()
                .toUriString();
    }
}
