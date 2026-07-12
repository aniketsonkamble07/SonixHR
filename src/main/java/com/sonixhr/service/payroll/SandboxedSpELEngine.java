package com.sonixhr.service.payroll;

import com.sonixhr.exceptions.TechnicalException;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
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

    // Static Math wrappers for SpEL injection using double
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    public static double round(double a) {
        return Math.round(a);
    }

    public Expression parse(String formula) {
        if (formula == null) {
            return null;
        }
        return parser.parseExpression(formula);
    }

    public Double evaluate(Expression expression, Map<String, Object> variables) {
        if (expression == null) {
            return 0.0;
        }

        // Setup the secure, sandboxed read-only evaluation context
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor())
                .build();

        // Bind standard math helper functions as variables with the '#' prefix
        context.setVariable("min", minMethod);
        context.setVariable("max", maxMethod);
        context.setVariable("round", roundMethod);

        // Populate context variables
        if (variables != null) {
            variables.forEach(context::setVariable);
        }

        try {
            Double result = expression.getValue(context, variables, Double.class);
            return result != null ? result : 0.0;
        } catch (Exception e) {
            throw new TechnicalException("TECH_FORMULA", "Salary formula evaluation failed", 
                    "Invalid formula expression: " + expression.getExpressionString(), e);
        }
    }
}
