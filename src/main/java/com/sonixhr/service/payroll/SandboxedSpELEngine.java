package com.sonixhr.service.payroll;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class SandboxedSpELEngine {
    private final ExpressionParser parser = new SpelExpressionParser();
    private static Method minMethod;
    private static Method maxMethod;
    private static Method roundMethod;

    static {
        try {
            minMethod = SandboxedSpELEngine.class.getDeclaredMethod("min", double.class, double.class);
            maxMethod = SandboxedSpELEngine.class.getDeclaredMethod("max", double.class, double.class);
            roundMethod = SandboxedSpELEngine.class.getDeclaredMethod("round", double.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Static Math wrappers for SpEL injection
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    public static double round(double a) {
        return Math.round(a);
    }

    public BigDecimal evaluate(String formula, Map<String, Object> variables) {
        if (formula == null || formula.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Setup the secure, sandboxed read-only evaluation context
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .build();

        // Bind standard math helper functions as variables with the '#' prefix
        context.setVariable("min", minMethod);
        context.setVariable("max", maxMethod);
        context.setVariable("round", roundMethod);

        // Populate context variables (e.g. BASIC, CTC, etc.)
        if (variables != null) {
            variables.forEach(context::setVariable);
        }

        try {
            Expression expression = parser.parseExpression(formula);
            Double result = expression.getValue(context, variables, Double.class);
            if (result == null) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(result).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid salary component formula: " + formula, e);
        }
    }


    public void validateFormula(String formula, Map<String, Object> testVariables) {
        evaluate(formula, testVariables);
    }
}
