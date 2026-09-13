package com.example.gateway.execution;

import com.example.gateway.domain.IntegrationApp;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Small extension point for OAuth token acquisition and secret-manager integration. */
@Component
public class OAuthTokenService {
    private final WebClient webClient = WebClient.builder().build();

    public String getToken(IntegrationApp app) {
        var config = app.getAuthConfig();
        String configuredToken = String.valueOf(config.getOrDefault("accessToken", ""));
        if (!configuredToken.isBlank()) return configuredToken;
        String tokenUrl = String.valueOf(config.getOrDefault("tokenUrl", ""));
        if (tokenUrl.isBlank()) throw new IllegalStateException("Missing OAuth accessToken or tokenUrl");
        String clientId = String.valueOf(config.getOrDefault("clientId", ""));
        String clientSecret = String.valueOf(config.getOrDefault("clientSecret", ""));
        return webClient.post().uri(tokenUrl).bodyValue(java.util.Map.of(
                        "grant_type", config.getOrDefault("grantType", "client_credentials"),
                        "client_id", clientId, "client_secret", clientSecret))
                .retrieve().bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                .map(node -> node.path("access_token").asText())
                .block(java.time.Duration.ofSeconds(10));
    }
}
