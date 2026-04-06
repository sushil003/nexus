package dev.nexus.core.plugin;

import java.util.List;
import java.util.Map;

public record OAuthConfig(
        String providerName,
        String authUrl,
        String tokenUrl,
        List<String> scopes,
        TokenAuthMethod tokenAuthMethod,
        boolean requiresRegisteredRedirect,
        Map<String, String> authParams
) {
}
