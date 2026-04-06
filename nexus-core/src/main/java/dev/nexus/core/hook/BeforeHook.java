package dev.nexus.core.hook;

import dev.nexus.core.context.PluginContext;

@FunctionalInterface
public interface BeforeHook {
    BeforeHookResult execute(PluginContext ctx, Object args);
}
