package dev.nexus.app.web;

import dev.nexus.core.webhook.WebhookResult;
import dev.nexus.core.webhook.WebhookRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookRouter webhookRouter;

    public WebhookController(WebhookRouter webhookRouter) {
        this.webhookRouter = webhookRouter;
    }

    @PostMapping("/{pluginId}")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @PathVariable String pluginId,
            @RequestBody String body,
            HttpServletRequest request) {

        Map<String, String> headers = extractHeaders(request);
        log.info("Webhook received for plugin: {}", pluginId);

        return webhookRouter.route(pluginId, headers, body)
                .map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", result.success());
                    response.put("pluginId", result.pluginId());
                    response.put("webhookId", result.webhookId());
                    if (result.success()) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.internalServerError().body(response);
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }
}
