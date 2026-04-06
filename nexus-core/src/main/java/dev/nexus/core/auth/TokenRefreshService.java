package dev.nexus.core.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class TokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshService.class);
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final WebClient webClient;

    public TokenRefreshService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Optional<TokenResult> refreshIfNeeded(KeyManager keyManager, String tokenUrl,
                                                  String clientId, String clientSecret) {
        Optional<String> expiresAtStr = keyManager.getField("token_expires_at");
        if (expiresAtStr.isEmpty()) {
            return Optional.empty();
        }

        Instant expiresAt = Instant.parse(expiresAtStr.get());
        if (Instant.now().plusSeconds(TOKEN_EXPIRY_BUFFER_SECONDS).isBefore(expiresAt)) {
            return Optional.empty(); // Token still valid
        }

        Optional<String> refreshToken = keyManager.getField("refresh_token");
        if (refreshToken.isEmpty()) {
            log.warn("Token expired but no refresh token available");
            return Optional.empty();
        }

        return Optional.of(refreshToken(tokenUrl, clientId, clientSecret, refreshToken.get(), keyManager));
    }

    public TokenResult exchangeCode(String tokenUrl, String code, String redirectUri,
                                     String clientId, String clientSecret) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        return executeTokenRequest(tokenUrl, body);
    }

    private TokenResult refreshToken(String tokenUrl, String clientId, String clientSecret,
                                     String refreshToken, KeyManager keyManager) {
        log.info("Refreshing OAuth token");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        TokenResult result = executeTokenRequest(tokenUrl, body);

        // Store refreshed tokens
        keyManager.setField("access_token", result.accessToken());
        keyManager.setField("token_expires_at", result.expiresAt().toString());
        if (result.refreshToken() != null) {
            keyManager.setField("refresh_token", result.refreshToken());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private TokenResult executeTokenRequest(String tokenUrl, MultiValueMap<String, String> body) {
        Map<String, Object> response = webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("Empty token response from: " + tokenUrl);
        }

        String accessToken = (String) response.get("access_token");
        String refreshToken = (String) response.get("refresh_token");
        int expiresIn = response.containsKey("expires_in")
                ? ((Number) response.get("expires_in")).intValue()
                : 3600;

        return new TokenResult(accessToken, refreshToken, Instant.now().plusSeconds(expiresIn));
    }

    public record TokenResult(String accessToken, String refreshToken, Instant expiresAt) {}
}
