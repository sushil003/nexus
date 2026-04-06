package dev.nexus.core.endpoint;

import dev.nexus.core.context.PluginContext;
import dev.nexus.core.error.NexusException;

@FunctionalInterface
public interface EndpointHandler {
    Object execute(PluginContext ctx, Object args) throws NexusException;
}
