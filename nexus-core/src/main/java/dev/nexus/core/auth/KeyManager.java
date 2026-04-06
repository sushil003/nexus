package dev.nexus.core.auth;

import java.util.Optional;

public interface KeyManager {
    Optional<String> getField(String name);

    void setField(String name, String value);
}
