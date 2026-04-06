package dev.nexus.core.permission;

import dev.nexus.core.plugin.RiskLevel;
import org.springframework.stereotype.Component;

@Component
public class PermissionEvaluator {

    public PermissionResult evaluate(RiskLevel risk, PermissionMode mode) {
        return switch (mode) {
            case OPEN -> PermissionResult.ALLOW;
            case CAUTIOUS -> switch (risk) {
                case READ, WRITE -> PermissionResult.ALLOW;
                case DESTRUCTIVE -> PermissionResult.REQUIRE_APPROVAL;
            };
            case STRICT -> switch (risk) {
                case READ -> PermissionResult.ALLOW;
                case WRITE -> PermissionResult.REQUIRE_APPROVAL;
                case DESTRUCTIVE -> PermissionResult.DENY;
            };
            case READONLY -> switch (risk) {
                case READ -> PermissionResult.ALLOW;
                case WRITE, DESTRUCTIVE -> PermissionResult.DENY;
            };
        };
    }
}
