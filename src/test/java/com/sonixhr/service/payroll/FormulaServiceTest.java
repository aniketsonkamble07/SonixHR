package com.sonixhr.service.payroll;

import com.sonixhr.exceptions.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class FormulaServiceTest {

    private FormulaService formulaService;

    @BeforeEach
    public void setUp() {
        SandboxedSpELEngine engine = new SandboxedSpELEngine();
        formulaService = new FormulaService(engine);
    }

    @Test
    public void testValidMathFormulas() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("BASIC", BigDecimal.valueOf(50000));
        variables.put("HRA_RATE", BigDecimal.valueOf(0.40));

        BigDecimal result = formulaService.evaluate("#BASIC * #HRA_RATE", variables);
        assertEquals(BigDecimal.valueOf(20000.00).setScale(2), result);
    }

    @Test
    public void testRegisteredFunctions() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("A", BigDecimal.valueOf(10));
        variables.put("B", BigDecimal.valueOf(20));

        BigDecimal resultMin = formulaService.evaluate("#min(#A, #B)", variables);
        assertEquals(BigDecimal.valueOf(10.00).setScale(2), resultMin);

        BigDecimal resultMax = formulaService.evaluate("#max(#A, #B)", variables);
        assertEquals(BigDecimal.valueOf(20.00).setScale(2), resultMax);
    }

    @Test
    public void testBlockedTypeReferences() {
        Map<String, Object> variables = new HashMap<>();
        
        // Blocking T(...) expression evaluation (wrapped in TechnicalException)
        TechnicalException exception = assertThrows(TechnicalException.class, () -> {
            formulaService.evaluate("T(java.lang.Runtime).getRuntime().exec('calc')", variables);
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void testBlockedConstructorCalls() {
        Map<String, Object> variables = new HashMap<>();
        
        // Blocking constructor calls (wrapped in TechnicalException)
        TechnicalException exception = assertThrows(TechnicalException.class, () -> {
            formulaService.evaluate("new java.lang.String('test')", variables);
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void testBlockedUnregisteredMethodCalls() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("STR", "hello");
        
        TechnicalException exception = assertThrows(TechnicalException.class, () -> {
            formulaService.evaluate("#STR.toUpperCase()", variables);
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void testFunctionReferenceValidation() {
        Map<String, Object> variables = new HashMap<>();
        
        // Assert that a non-whitelisted function reference is blocked
        TechnicalException exception = assertThrows(TechnicalException.class, () -> {
            formulaService.evaluate("#unauthorizedFunction(1, 2)", variables);
        });
        assertTrue(exception.getCause().getMessage().contains("Unauthorized function reference"));
    }

    @Test
    public void testExpressionCacheLRUBound() {
        Map<String, Object> variables = new HashMap<>();
        
        // Load expressions up to cache cap + 50
        for (int i = 0; i < 1050; i++) {
            formulaService.evaluate("1 + " + i, variables);
        }
        
        // Since capacity is capped at 1000, cache size must not exceed 1000
        assertTrue(formulaService.getExpressionCache().size() <= 1000);
    }
}
