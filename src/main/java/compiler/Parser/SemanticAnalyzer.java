package compiler.Parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SemanticAnalyzer {
    private final Set<String> knownTypes = new HashSet<>();
    private final Map<String, FunctionSignature> functions = new HashMap<>();
    private final Map<String, List<String>> collectionConstructorTypes = new HashMap<>();

    private Scope currentScope;
    private String currentFunctionReturnType;

    public SemanticAnalyzer() {
        knownTypes.add("INT");
        knownTypes.add("FLOAT");
        knownTypes.add("STRING");
        knownTypes.add("BOOLEAN");
        knownTypes.add("BOOL");
        knownTypes.add("void");
    }

    public void analyze(AST ast) {
        if (ast == null || ast.getProgram() == null) {
            return;
        }

        currentScope = new Scope(null);
        firstPass(ast.getProgram().getStatements());
        secondPass(ast.getProgram().getStatements());
    }

    private void firstPass(List<StatementNode> statements) {
        for (StatementNode stmt : statements) {
            if (stmt instanceof CollectionNode collectionNode) {
                registerCollection(collectionNode);
            } else if (stmt instanceof FunctionNode functionNode) {
                registerFunction(functionNode);
            }
        }
    }

    private void secondPass(List<StatementNode> statements) {
        for (StatementNode stmt : statements) {
            analyzeStatement(stmt);
        }
    }

    private void registerCollection(CollectionNode collectionNode) {
        String name = collectionNode.getName();

        if (name == null || name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
            throw new SemanticException("CollectionError: collection names must begin with a capital letter: " + name);
        }

        if (isReservedName(name) || knownTypes.contains(name) || collectionConstructorTypes.containsKey(name)) {
            throw new SemanticException("CollectionError: invalid or duplicate collection name: " + name);
        }

        knownTypes.add(name);

        List<String> fieldTypes = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();

        for (VarDeclarationNode field : collectionNode.getFields()) {
            String fieldType = normalizeType(field.getType());

            if (!typeExists(fieldType)) {
                throw new SemanticException("CollectionError: unknown field type '" + fieldType + "' in collection " + name);
            }

            if (!fieldNames.add(field.getIdentifier())) {
                throw new SemanticException("CollectionError: duplicate field '" + field.getIdentifier() + "' in collection " + name);
            }

            fieldTypes.add(fieldType);
        }

        collectionConstructorTypes.put(name, fieldTypes);
    }

    private void registerFunction(FunctionNode functionNode) {
        if (functions.containsKey(functionNode.getName())) {
            throw new SemanticException("ScopeError: duplicate function definition: " + functionNode.getName());
        }

        String returnType = normalizeType(functionNode.getReturnType());
        if (!typeExists(returnType)) {
            throw new SemanticException("ReturnError: unknown return type '" + returnType + "' for function " + functionNode.getName());
        }

        List<String> parameterTypes = new ArrayList<>();
        Set<String> parameterNames = new HashSet<>();

        for (VarDeclarationNode param : functionNode.getParameters()) {
            String paramType = normalizeType(param.getType());

            if (!typeExists(paramType)) {
                throw new SemanticException("TypeError: unknown parameter type '" + paramType + "' in function " + functionNode.getName());
            }

            if (!parameterNames.add(param.getIdentifier())) {
                throw new SemanticException("ScopeError: duplicate parameter '" + param.getIdentifier() + "' in function " + functionNode.getName());
            }

            parameterTypes.add(paramType);
        }

        functions.put(functionNode.getName(),
                new FunctionSignature(functionNode.getName(), returnType, parameterTypes));
    }

    private void analyzeStatement(StatementNode stmt) {
        if (stmt instanceof CollectionNode) {
            return;
        }

        if (stmt instanceof FunctionNode functionNode) {
            analyzeFunction(functionNode);
            return;
        }

        if (stmt instanceof VarDeclarationNode varDeclarationNode) {
            analyzeVarDeclaration(varDeclarationNode);
            return;
        }

        if (stmt instanceof AssignmentNode assignmentNode) {
            analyzeAssignment(assignmentNode);
            return;
        }

        if (stmt instanceof IfNode ifNode) {
            analyzeIf(ifNode);
            return;
        }

        if (stmt instanceof WhileNode whileNode) {
            analyzeWhile(whileNode);
            return;
        }

        if (stmt instanceof ForNode forNode) {
            analyzeFor(forNode);
            return;
        }

        if (stmt instanceof ReturnNode returnNode) {
            analyzeReturn(returnNode);
            return;
        }

        if (stmt instanceof ExpressionStatementNode expressionStatementNode) {
            inferExpressionType(expressionStatementNode.getExpression());
        }
    }

    private void analyzeFunction(FunctionNode functionNode) {
        Scope savedScope = currentScope;
        String savedReturnType = currentFunctionReturnType;

        currentScope = new Scope(savedScope);
        currentFunctionReturnType = normalizeType(functionNode.getReturnType());

        for (VarDeclarationNode parameter : functionNode.getParameters()) {
            currentScope.define(parameter.getIdentifier(), normalizeType(parameter.getType()));
        }

        for (StatementNode stmt : functionNode.getBody()) {
            analyzeStatement(stmt);
        }

        currentScope = savedScope;
        currentFunctionReturnType = savedReturnType;
    }

    private void analyzeVarDeclaration(VarDeclarationNode node) {
        String declaredType = normalizeType(node.getType());

        if (!typeExists(declaredType)) {
            throw new SemanticException("TypeError: unknown type '" + declaredType + "' for variable " + node.getIdentifier());
        }

        if (currentScope.containsInCurrentScope(node.getIdentifier())) {
            throw new SemanticException("ScopeError: variable already defined in this scope: " + node.getIdentifier());
        }

        if (node.getValue() != null) {
            String valueType = inferExpressionType(node.getValue());
            if (!sameType(declaredType, valueType)) {
                throw new SemanticException("TypeError: cannot assign " + valueType + " to " + declaredType + " variable " + node.getIdentifier());
            }
        }

        currentScope.define(node.getIdentifier(), declaredType);
    }

    private void analyzeAssignment(AssignmentNode node) {
        String targetType = currentScope.resolve(node.getIdentifier());

        if (targetType == null) {
            throw new SemanticException("ScopeError: variable used out of scope or before definition: " + node.getIdentifier());
        }

        String valueType = inferExpressionType(node.getValue());
        if (!sameType(targetType, valueType)) {
            throw new SemanticException("TypeError: cannot assign " + valueType + " to " + targetType + " variable " + node.getIdentifier());
        }
    }

    private void analyzeIf(IfNode node) {
        String conditionType = inferExpressionType(node.getCondition());
        if (!isBooleanType(conditionType)) {
            throw new SemanticException("MissingConditionError: if condition must be BOOLEAN, got " + conditionType);
        }

        analyzeBlock(node.getThenBranch());
        if (node.getElseBranch() != null) {
            analyzeBlock(node.getElseBranch());
        }
    }

    private void analyzeWhile(WhileNode node) {
        String conditionType = inferExpressionType(node.getCondition());
        if (!isBooleanType(conditionType)) {
            throw new SemanticException("MissingConditionError: while condition must be BOOLEAN, got " + conditionType);
        }

        analyzeBlock(node.getBody());
    }

    private void analyzeFor(ForNode node) {
        String loopVariableType = currentScope.resolve(node.getIdentifier());
        if (loopVariableType == null) {
            throw new SemanticException("ScopeError: loop variable not defined: " + node.getIdentifier());
        }

        String startType = inferExpressionType(node.getRangeStart());
        String endType = inferExpressionType(node.getRangeEnd());
        String updateType = inferExpressionType(node.getUpdate());

        if (!sameType("INT", loopVariableType)
                || !sameType("INT", startType)
                || !sameType("INT", endType)
                || !sameType("INT", updateType)) {
            throw new SemanticException("TypeError: for loop variable, bounds, and update must be INT");
        }

        analyzeBlock(node.getBody());
    }

    private void analyzeReturn(ReturnNode node) {
        if (currentFunctionReturnType == null) {
            throw new SemanticException("ReturnError: return used outside of a function");
        }

        if ("void".equals(currentFunctionReturnType)) {
            if (node.getValue() != null) {
                throw new SemanticException("ReturnError: void function cannot return a value");
            }
            return;
        }

        if (node.getValue() == null) {
            throw new SemanticException("ReturnError: function must return " + currentFunctionReturnType);
        }

        String actualType = inferExpressionType(node.getValue());
        if (!sameType(currentFunctionReturnType, actualType)) {
            throw new SemanticException("ReturnError: expected " + currentFunctionReturnType + " but got " + actualType);
        }
    }

    private void analyzeBlock(List<StatementNode> statements) {
        Scope savedScope = currentScope;
        currentScope = new Scope(savedScope);

        for (StatementNode stmt : statements) {
            analyzeStatement(stmt);
        }

        currentScope = savedScope;
    }

    private String inferExpressionType(ExpressionNode expr) {
        if (expr instanceof IntegerNode) {
            return "INT";
        }

        if (expr instanceof FloatNode) {
            return "FLOAT";
        }

        if (expr instanceof StringNode) {
            return "STRING";
        }

        if (expr instanceof BooleanNode) {
            return "BOOLEAN";
        }

        if (expr instanceof IdentifierNode identifierNode) {
            String type = currentScope.resolve(identifierNode.name);
            if (type == null) {
                throw new SemanticException("ScopeError: variable used out of scope or before definition: " + identifierNode.name);
            }
            return type;
        }

        if (expr instanceof FunctionCallNode functionCallNode) {
            return inferFunctionCallType(functionCallNode);
        }

        if (expr instanceof OperationNode operationNode) {
            return inferOperationType(operationNode);
        }

        throw new SemanticException("TypeError: unsupported expression in semantic analysis");
    }

    private String inferFunctionCallType(FunctionCallNode functionCallNode) {
        if (functions.containsKey(functionCallNode.name)) {
            FunctionSignature signature = functions.get(functionCallNode.name);

            if (signature.getParameterTypes().size() != functionCallNode.arguments.size()) {
                throw new SemanticException("ArgumentError: wrong number of arguments for function " + functionCallNode.name);
            }

            for (int i = 0; i < functionCallNode.arguments.size(); i++) {
                String expected = normalizeType(signature.getParameterTypes().get(i));
                String actual = normalizeType(inferExpressionType(functionCallNode.arguments.get(i)));
                if (!sameType(expected, actual)) {
                    throw new SemanticException("ArgumentError: expected " + expected + " but got " + actual + " in call to " + functionCallNode.name);
                }
            }

            return normalizeType(signature.getReturnType());
        }

        if (collectionConstructorTypes.containsKey(functionCallNode.name)) {
            List<String> expectedTypes = collectionConstructorTypes.get(functionCallNode.name);

            if (expectedTypes.size() != functionCallNode.arguments.size()) {
                throw new SemanticException("ArgumentError: wrong number of arguments for collection constructor " + functionCallNode.name);
            }

            for (int i = 0; i < functionCallNode.arguments.size(); i++) {
                String expected = normalizeType(expectedTypes.get(i));
                String actual = normalizeType(inferExpressionType(functionCallNode.arguments.get(i)));
                if (!sameType(expected, actual)) {
                    throw new SemanticException("ArgumentError: expected " + expected + " but got " + actual + " in constructor " + functionCallNode.name);
                }
            }

            return functionCallNode.name;
        }

        throw new SemanticException("ScopeError: unknown function or collection " + functionCallNode.name);
    }

    private String inferOperationType(OperationNode operationNode) {
        String op = operationNode.operator;

        if ("not".equals(op)) {
            String rightType = inferExpressionType(operationNode.right);
            if (!isBooleanType(rightType)) {
                throw new SemanticException("OperatorError: 'not' expects BOOLEAN, got " + rightType);
            }
            return "BOOLEAN";
        }

        String leftType = inferExpressionType(operationNode.left);
        String rightType = inferExpressionType(operationNode.right);

        if (isArithmeticOperator(op)) {
            if (!sameNumericType(leftType, rightType)) {
                throw new SemanticException("OperatorError: arithmetic operator '" + op + "' requires same numeric types, got " + leftType + " and " + rightType);
            }
            return leftType;
        }

        if (isLogicalOperator(op)) {
            if (!isBooleanType(leftType) || !isBooleanType(rightType)) {
                throw new SemanticException("OperatorError: logical operator '" + op + "' requires BOOLEAN operands");
            }
            return "BOOLEAN";
        }

        if (isComparisonOperator(op)) {
            if (!sameType(leftType, rightType)) {
                throw new SemanticException("OperatorError: comparison operator '" + op + "' requires operands of same type");
            }
            return "BOOLEAN";
        }

        throw new SemanticException("OperatorError: unsupported operator '" + op + "'");
    }

    private boolean isArithmeticOperator(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op) || "%".equals(op);
    }

    private boolean isLogicalOperator(String op) {
        return "&&".equals(op) || "||".equals(op);
    }

    private boolean isComparisonOperator(String op) {
        return "==".equals(op) || "!=".equals(op) || "=/=".equals(op)
                || "<".equals(op) || ">".equals(op) || "<=".equals(op) || ">=".equals(op);
    }

    private boolean isBooleanType(String type) {
        return "BOOLEAN".equals(normalizeType(type));
    }

    private boolean sameNumericType(String left, String right) {
        String normalizedLeft = normalizeType(left);
        String normalizedRight = normalizeType(right);

        return ("INT".equals(normalizedLeft) || "FLOAT".equals(normalizedLeft))
                && normalizedLeft.equals(normalizedRight);
    }

    private boolean sameType(String left, String right) {
        return normalizeType(left).equals(normalizeType(right));
    }

    private boolean typeExists(String type) {
        return knownTypes.contains(normalizeType(type));
    }

    private String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        if ("BOOL".equals(type)) {
            return "BOOLEAN";
        }
        return type;
    }

    private boolean isReservedName(String name) {
        return "INT".equals(name)
                || "FLOAT".equals(name)
                || "STRING".equals(name)
                || "BOOLEAN".equals(name)
                || "BOOL".equals(name)
                || "while".equals(name)
                || "if".equals(name)
                || "else".equals(name)
                || "return".equals(name)
                || "for".equals(name)
                || "def".equals(name)
                || "coll".equals(name)
                || "ARRAY".equals(name)
                || "not".equals(name);
    }
}
