package dev.nexus.core.hook;

import dev.nexus.core.context.PluginContext;

@FunctionalInterface
public interface AfterHook {
    void execute(PluginContext ctx, Object result, String passToAfter);
}
