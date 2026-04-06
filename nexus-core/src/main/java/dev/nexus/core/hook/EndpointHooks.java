package dev.nexus.core.hook;

import java.util.Optional;

public record EndpointHooks(
        Optional<BeforeHook> before,
        Optional<AfterHook> after
) {
    public static EndpointHooks none() {
        return new EndpointHooks(Optional.empty(), Optional.empty());
    }
}
