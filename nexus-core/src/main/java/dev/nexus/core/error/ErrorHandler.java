package dev.nexus.core.error;

public interface ErrorHandler {
    boolean isRetryable(Exception ex);

    int getMaxAttempts();
}
