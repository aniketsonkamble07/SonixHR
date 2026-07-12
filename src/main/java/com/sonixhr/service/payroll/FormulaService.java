package com.sonixhr.service.payroll;

import com.sonixhr.exceptions.TechnicalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.SpelNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormulaService {

    private final SandboxedSpELEngine spelEngine;
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public BigDecimal evaluate(String formula, Map<String, Object> variables) {
        if (formula == null || formula.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            // Retrieve or parse expression from cache
            Expression expression = expressionCache.computeIfAbsent(formula, spelEngine::parse);

            // AST-based validation check before evaluation
            validateExpressionAst(expression);

            // Convert BigDecimal / Numeric inputs to Double for standard SpEL math
            Map<String, Object> doubleVars = convertToDoubleVariables(variables);

            // Evaluate dynamically via low-level SandboxedSpELEngine
            Double rawResult = spelEngine.evaluate(expression, doubleVars);

            if (rawResult == null) {
                return BigDecimal.ZERO;
            }

            // Convert Double output back to BigDecimal with 2 decimal places rounding
            return BigDecimal.valueOf(rawResult).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new TechnicalException("TECH_FORMULA", "Salary formula evaluation failed", 
                    "Invalid formula expression: " + formula, e);
        }
    }

    public void validateFormula(String formula, Map<String, Object> testVariables) {
        evaluate(formula, testVariables);
    }

    private Map<String, Object> convertToDoubleVariables(Map<String, Object> input) {
        if (input == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> output = new HashMap<>();
        input.forEach((k, v) -> {
            if (v instanceof BigDecimal) {
                output.put(k, ((BigDecimal) v).doubleValue());
            } else if (v instanceof Number) {
                output.put(k, ((Number) v).doubleValue());
            } else {
                output.put(k, v);
            }
        });
        return output;
    }

    /**
     * Inspects SpEL AST nodes for security, expression length, and syntax validation.
     */
    private void validateExpressionAst(Expression expression) {
        if (!(expression instanceof SpelExpression)) {
            return;
        }
        
        SpelExpression spelExpression = (SpelExpression) expression;
        String originalExpression = spelExpression.getExpressionString();
        
        // 1. Check max expression length
        if (originalExpression != null && originalExpression.length() > 500) {
            throw new IllegalArgumentException("Formula expression exceeds maximum allowed length of 500 characters.");
        }
        
        // 2. Validate AST nodes recursively
        verifyAstNode(spelExpression.getAST());
    }

    private void verifyAstNode(SpelNode node) {
        if (node == null) return;

        String nodeClass = node.getClass().getSimpleName();
        if ("VariableReference".equals(nodeClass)) {
            String varName = node.toStringAST(); // e.g. "#BASIC" or "#min"
            if (varName.startsWith("#")) {
                String name = varName.substring(1);
                // Allow our registered functions
                if (!name.equals("min") && !name.equals("max") && !name.equals("round")) {
                    // Variable must follow uppercase alphanumeric component/rate code pattern
                    if (!name.matches("^[A-Z][A-Z0-9_]*$")) {
                        throw new IllegalArgumentException("Unauthorized variable name: " + varName);
                    }
                }
            }
        } else if ("PropertyOrFieldReference".equals(nodeClass)) {
            String propName = node.toStringAST();
            if (!propName.matches("^[A-Z][A-Z0-9_]*$")) {
                throw new IllegalArgumentException("Unauthorized property reference: " + propName);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            verifyAstNode(node.getChild(i));
        }
    }
}
