package dev.nexus.core.permission;

import dev.nexus.core.plugin.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class PermissionEvaluatorTest {

    private PermissionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PermissionEvaluator();
    }

    @Test
    void openMode_allowsEverything() {
        assertEquals(PermissionResult.ALLOW, evaluator.evaluate(RiskLevel.READ, PermissionMode.OPEN));
        assertEquals(PermissionResult.ALLOW, evaluator.evaluate(RiskLevel.WRITE, PermissionMode.OPEN));
        assertEquals(PermissionResult.ALLOW, evaluator.evaluate(RiskLevel.DESTRUCTIVE, PermissionMode.OPEN));
    }

    @ParameterizedTest
    @CsvSource({
            "READ, ALLOW",
            "WRITE, ALLOW",
            "DESTRUCTIVE, REQUIRE_APPROVAL"
    })
    void cautiousMode_matrix(RiskLevel risk, PermissionResult expected) {
        assertEquals(expected, evaluator.evaluate(risk, PermissionMode.CAUTIOUS));
    }

    @ParameterizedTest
    @CsvSource({
            "READ, ALLOW",
            "WRITE, REQUIRE_APPROVAL",
            "DESTRUCTIVE, DENY"
    })
    void strictMode_matrix(RiskLevel risk, PermissionResult expected) {
        assertEquals(expected, evaluator.evaluate(risk, PermissionMode.STRICT));
    }

    @Test
    void readonlyMode_deniesWriteAndDestructive() {
        assertEquals(PermissionResult.ALLOW, evaluator.evaluate(RiskLevel.READ, PermissionMode.READONLY));
        assertEquals(PermissionResult.DENY, evaluator.evaluate(RiskLevel.WRITE, PermissionMode.READONLY));
        assertEquals(PermissionResult.DENY, evaluator.evaluate(RiskLevel.DESTRUCTIVE, PermissionMode.READONLY));
    }
}
