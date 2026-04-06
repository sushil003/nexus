package dev.nexus.core.error;

public class NexusException extends RuntimeException {

    private final boolean retryable;

    public NexusException(String message) {
        super(message);
        this.retryable = false;
    }

    public NexusException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }

    public NexusException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
