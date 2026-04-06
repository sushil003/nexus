package dev.nexus.core.http;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class NexusHttpClient {

    private final WebClient.Builder webClientBuilder;

    public NexusHttpClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public WebClient clientFor(String baseUrl) {
        return webClientBuilder.baseUrl(baseUrl).build();
    }
}
