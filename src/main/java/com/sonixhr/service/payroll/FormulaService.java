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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormulaService {

    private final SandboxedSpELEngine spelEngine;
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    // Whitelist of allowed AST node class names
    private static final Set<String> ALLOWED_NODE_CLASSES = Set.of(
            "CompoundExpression",
            "Literal",
            "IntLiteral",
            "LongLiteral",
            "FloatLiteral",
            "DoubleLiteral",
            "RealLiteral",
            "StringLiteral",
            "BooleanLiteral",
            "NullLiteral",
            "VariableReference",
            "PropertyOrFieldReference",
            "OpPlus", "OperatorPlus",
            "OpMinus", "OperatorMinus",
            "OpMultiply", "OperatorMultiply",
            "OpDivide", "OperatorDivide",
            "OpPower", "OperatorPower",
            "OpModulus", "OperatorModulus",
            "OpEQ", "OperatorEquals",
            "OpNE", "OperatorNotEquals",
            "OpLT", "OperatorLessThan",
            "OpLE", "OperatorLessThanOrEqual",
            "OpGT", "OperatorGreaterThan",
            "OpGE", "OperatorGreaterThanOrEqual",
            "OpAnd", "OperatorAnd",
            "OpOr", "OperatorOr",
            "OpNot", "OperatorNot",
            "OperatorBetween",
            "OperatorInstanceof",
            "OperatorMatches",
            "MethodReference", // Only for whitelisted methods
            "FunctionReference",
            "Ternary",
            "Elvis"
    );

    // Whitelist of allowed method names
    private static final Set<String> ALLOWED_METHOD_NAMES = Set.of(
            "min", "max", "round"
    );

    // Whitelist of allowed variable name patterns
    private static final String ALLOWED_VAR_PATTERN = "^[A-Z][A-Z0-9_]*$";

    public BigDecimal evaluate(String formula, Map<String, Object> variables) {
        if (formula == null || formula.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            Expression expression = expressionCache.computeIfAbsent(formula, spelEngine::parse);
            validateExpressionAst(expression);
            
            Map<String, Object> doubleVars = convertToDoubleVariables(variables);
            Double rawResult = spelEngine.evaluate(expression, doubleVars);

            if (rawResult == null) {
                return BigDecimal.ZERO;
            }

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
     * Validates SpEL AST with strict whitelist to prevent code injection.
     */
    private void validateExpressionAst(Expression expression) {
        if (!(expression instanceof SpelExpression)) {
            return;
        }
        
        SpelExpression spelExpression = (SpelExpression) expression;
        String originalExpression = spelExpression.getExpressionString();
        
        // Check max expression length
        if (originalExpression != null && originalExpression.length() > 500) {
            throw new IllegalArgumentException("Formula expression exceeds maximum allowed length of 500 characters.");
        }
        
        // Validate AST nodes recursively
        validateAstNode(spelExpression.getAST());
    }

    private void validateAstNode(SpelNode node) {
        if (node == null) return;

        String nodeClass = node.getClass().getSimpleName();
        
        // Check if node class is whitelisted
        if (!ALLOWED_NODE_CLASSES.contains(nodeClass)) {
            throw new IllegalArgumentException("Disallowed expression element: " + nodeClass);
        }

        // Special validation for VariableReference
        if ("VariableReference".equals(nodeClass)) {
            String varName = node.toStringAST();
            if (varName.startsWith("#")) {
                String name = varName.substring(1);
                // Check if it's a function call
                if (ALLOWED_METHOD_NAMES.contains(name)) {
                    // Allowed function
                } else {
                    // Variable must follow uppercase alphanumeric pattern
                    if (!name.matches(ALLOWED_VAR_PATTERN)) {
                        throw new IllegalArgumentException("Unauthorized variable name: " + varName);
                    }
                }
            }
        }

        // Special validation for MethodReference
        if ("MethodReference".equals(nodeClass)) {
            String methodName = node.toStringAST();
            // Extract method name from the AST string representation
            // The format is typically "methodName" or "methodName()"
            String cleanName = methodName.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
            
            // Check if it's a whitelisted method
            boolean isWhitelisted = ALLOWED_METHOD_NAMES.stream()
                    .anyMatch(cleanName::equalsIgnoreCase);
                    
            if (!isWhitelisted) {
                throw new IllegalArgumentException("Unauthorized method reference: " + methodName);
            }
        }

        // Special validation for PropertyOrFieldReference
        if ("PropertyOrFieldReference".equals(nodeClass)) {
            String propName = node.toStringAST();
            // Only allow uppercase alphanumeric with underscore
            if (!propName.matches(ALLOWED_VAR_PATTERN)) {
                throw new IllegalArgumentException("Unauthorized property reference: " + propName);
            }
        }

        // Recursively validate children
        for (int i = 0; i < node.getChildCount(); i++) {
            validateAstNode(node.getChild(i));
        }
    }
}
