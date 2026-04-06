package dev.nexus.core.http;

import dev.nexus.core.error.NexusException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Function;

@Component
public class NexusHttpClient {

    private final WebClient.Builder webClientBuilder;

    public NexusHttpClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public WebClient clientFor(String baseUrl) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public WebClient clientFor(String baseUrl, String bearerToken) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .build();
    }

    public WebClient clientFor(String baseUrl, Function<WebClient.Builder, WebClient.Builder> customizer) {
        return customizer.apply(webClientBuilder.baseUrl(baseUrl)).build();
    }

    public static Retry defaultRetry() {
        return Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .filter(throwable -> {
                    if (throwable instanceof NexusException ne) {
                        return ne.isRetryable();
                    }
                    return throwable instanceof java.net.ConnectException
                            || throwable instanceof java.util.concurrent.TimeoutException;
                })
                .onRetryExhaustedThrow((spec, signal) ->
                        new NexusException("Retry exhausted after " + signal.totalRetries() + " attempts",
                                signal.failure()));
    }
}
