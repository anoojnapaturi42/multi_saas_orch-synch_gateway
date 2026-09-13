package com.example.gateway.execution;

import com.example.gateway.domain.Enums;
import com.example.gateway.domain.IntegrationApp;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Resolves provider auth from IntegrationApp JSON configuration at request time. */
@Component
public class DynamicAuthenticationResolver {
    private final OAuthTokenService oauthTokenService;

    public DynamicAuthenticationResolver(OAuthTokenService oauthTokenService) { this.oauthTokenService = oauthTokenService; }

    public void apply(WebClient.RequestHeadersSpec<?> request, IntegrationApp app) {
        var config = app.getAuthConfig();
        switch (app.getAuthType()) {
            case API_KEY -> {
                String key = required(config, "value");
                String header = (String) config.getOrDefault("headerName", "X-API-Key");
                request.header(header, key);
            }
            case BASIC -> {
                String credentials = required(config, "username") + ":" + required(config, "password");
                request.header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
            }
            case OAUTH2 -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthTokenService.getToken(app));
        }
    }

    private String required(java.util.Map<String, Object> config, String key) {
        String value = String.valueOf(config.getOrDefault(key, ""));
        if (!StringUtils.hasText(value)) throw new IllegalStateException("Missing auth_config." + key);
        return value;
    }
}
