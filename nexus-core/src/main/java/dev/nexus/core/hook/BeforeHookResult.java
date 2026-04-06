package dev.nexus.core.hook;

public record BeforeHookResult(
        boolean shouldContinue,
        Object modifiedArgs,
        String passToAfter
) {
    public static BeforeHookResult proceed(Object args) {
        return new BeforeHookResult(true, args, null);
    }

    public static BeforeHookResult abort() {
        return new BeforeHookResult(false, null, null);
    }
}
