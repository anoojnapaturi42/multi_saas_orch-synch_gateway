package com.example.gateway.execution;

import com.example.gateway.domain.Enums;
import com.example.gateway.domain.WorkflowStep;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Executes arbitrary REST/SOAP steps using only database-defined configuration. */
@Service
public class GenericApiExecutorService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper = new XmlMapper();
    private final DynamicAuthenticationResolver auth;
    private final DistributedTokenBucketRateLimiter rateLimiter;

    public GenericApiExecutorService(WebClient.Builder builder, ObjectMapper objectMapper,
                                     DynamicAuthenticationResolver auth,
                                     DistributedTokenBucketRateLimiter rateLimiter) {
        this.webClient = builder.build(); this.objectMapper = objectMapper; this.auth = auth; this.rateLimiter = rateLimiter;
    }

    public Mono<StepExecutionResult> execute(WorkflowStep step, JsonNode normalizedPayload) {
        var app = step.getTargetApp();
        var config = step.getRetryConfig();
        int maxAttempts = ((Number) config.getOrDefault("maxAttempts", 3)).intValue();
        long backoffMs = ((Number) config.getOrDefault("backoffMs", 250)).longValue();
        Retry retry = Retry.of("workflow-step-" + step.getId(), RetryConfig.custom()
                .maxAttempts(Math.max(1, maxAttempts)).intervalFunction(IntervalFunction.ofExponentialBackoff(backoffMs, 2))
                .retryOnResult(result -> result instanceof StepExecutionResult r && (r.httpStatus() == 429 || r.httpStatus() >= 500))
                .retryExceptions(java.io.IOException.class, java.util.concurrent.TimeoutException.class)
                .build());
        Instant started = Instant.now();
        return Retry.decorateCompletionStage(retry, () -> executeOnce(step, normalizedPayload, started)).get().toCompletableFuture()
                .thenApply(result -> result);
    }

    private java.util.concurrent.CompletionStage<StepExecutionResult> executeOnce(WorkflowStep step, JsonNode payload, Instant started) {
        var app = step.getTargetApp();
        Mono<StepExecutionResult> call = awaitPermit(app).then(Mono.defer(() -> {
            String format = String.valueOf(step.getTransformSchema().getOrDefault("_requestFormat", "JSON"));
            boolean xml = "XML".equalsIgnoreCase(format) || "SOAP".equalsIgnoreCase(format);
            String body;
            try {
                body = xml ? xmlMapper.writeValueAsString(payload) : objectMapper.writeValueAsString(payload);
                if ("SOAP".equalsIgnoreCase(format)) body = soapEnvelope(body);
            }
            catch (JsonProcessingException ex) { return Mono.just(StepExecutionResult.failure(null, 0, Duration.between(started, Instant.now()), null, "Unable to serialize request: " + ex.getMessage())); }
            WebClient.RequestBodySpec request = webClient.method(org.springframework.http.HttpMethod.valueOf(step.getHttpMethod().name()))
                    .uri(app.getBaseUrl() + step.getEndpointPath());
            app.getDefaultHeaders().forEach((key, value) -> request.header(key, String.valueOf(value)));
            auth.apply(request, app);
            request.header(HttpHeaders.CONTENT_TYPE, xml ? MediaType.APPLICATION_XML_VALUE : MediaType.APPLICATION_JSON_VALUE);
            if ("SOAP".equalsIgnoreCase(format) && step.getTransformSchema().containsKey("_soapAction")) {
                request.header("SOAPAction", String.valueOf(step.getTransformSchema().get("_soapAction")));
            }
            return request.bodyValue(body).exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                    .map(raw -> StepExecutionResult.success(raw, response.statusCode().value(), Duration.between(started, Instant.now()), response.headers().contentType().map(MediaType::toString).orElse(null))));
        })).onErrorResume(ex -> Mono.just(StepExecutionResult.failure(null, 0, Duration.between(started, Instant.now()), null, ex.getMessage())));
        return call.toFuture();
    }

    private Mono<Void> awaitPermit(com.example.gateway.domain.IntegrationApp app) {
        return Mono.defer(() -> {
            Duration wait = rateLimiter.acquire(app.getId().toString(), app.getRateLimitConfig());
            return wait.isZero() ? Mono.empty() : Mono.delay(wait).then(awaitPermit(app));
        });
    }

    private String soapEnvelope(String xmlBody) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + xmlBody + "</soap:Body></soap:Envelope>";
    }
}
