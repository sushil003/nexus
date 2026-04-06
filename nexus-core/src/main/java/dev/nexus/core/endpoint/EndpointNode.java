package dev.nexus.core.endpoint;

import dev.nexus.core.hook.EndpointHooks;

import java.util.Map;

public sealed interface EndpointNode {

    record Group(Map<String, EndpointNode> children) implements EndpointNode {
    }

    record Leaf(
            EndpointHandler handler,
            Class<?> inputType,
            Class<?> outputType,
            EndpointHooks hooks
    ) implements EndpointNode {
    }
}
