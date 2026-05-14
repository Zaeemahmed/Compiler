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
    private final Map<String, Map<String, String>> collectionFieldTypes = new HashMap<>();

    private Scope currentScope;
    private String currentFunctionReturnType;

    public SemanticAnalyzer() {
        knownTypes.add("INT");
        knownTypes.add("FLOAT");
        knownTypes.add("STRING");
        knownTypes.add("BOOLEAN");
        knownTypes.add("BOOL");
        knownTypes.add("void");

        functions.put("read_INT", new FunctionSignature("read_INT", "INT", List.of()));
        functions.put("read_FLOAT", new FunctionSignature("read_FLOAT", "FLOAT", List.of()));
        functions.put("read_STRING", new FunctionSignature("read_STRING", "STRING", List.of()));
        functions.put("read_BOOL", new FunctionSignature("read_BOOL", "BOOLEAN", List.of()));
        functions.put("println", new FunctionSignature("println", "void", List.of("ANY")));
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
        Map<String, String> fieldTypeMap = new HashMap<>();
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
            fieldTypeMap.put(field.getIdentifier(), fieldType);
        }

        collectionConstructorTypes.put(name, fieldTypes);
        collectionFieldTypes.put(name, fieldTypeMap);
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

        if (expr instanceof ArrayCreationNode arrayCreationNode) {
            String sizeType = normalizeType(inferExpressionType(arrayCreationNode.size));
            if (!sameType("INT", sizeType)) {
                throw new SemanticException("TypeError: array size must be INT, got " + sizeType);
            }
            String elementType = normalizeType(arrayCreationNode.elementType);
            if (!typeExists(elementType)) {
                throw new SemanticException("TypeError: unknown array element type " + elementType);
            }
            return elementType + "[]";
        }

        if (expr instanceof ArrayAccessNode arrayAccessNode) {
            return inferArrayAccessType(arrayAccessNode);
        }

        if (expr instanceof FieldAccessNode fieldAccessNode) {
            return inferFieldAccessType(fieldAccessNode);
        }

        throw new SemanticException("TypeError: unsupported expression in semantic analysis: " + expr.getClass().getName());
    }

    private String inferFunctionCallType(FunctionCallNode functionCallNode) {
        
        if ("length".equals(functionCallNode.name)) {
            if (functionCallNode.arguments.size() != 1) {
                throw new SemanticException("ArgumentError: length expects 1 argument");
            }
            String actual = normalizeType(inferExpressionType(functionCallNode.arguments.get(0)));
            if (!actual.endsWith("[]")) {
                throw new SemanticException("ArgumentError: length expects an array but got " + actual);
            }
            return "INT";
        }

        if ("print_INT".equals(functionCallNode.name)) {
            if (functionCallNode.arguments.size() != 1) {
                throw new SemanticException("ArgumentError: print_INT expects 1 argument");
            }
            String actual = inferExpressionType(functionCallNode.arguments.get(0));
            if (!"INT".equals(actual) && !"int".equals(actual)) {
                throw new SemanticException("ArgumentError: print_INT expects INT but got " + actual);
            }
            return "void";
        }

        if ("print_FLOAT".equals(functionCallNode.name)) {
            if (functionCallNode.arguments.size() != 1) {
                throw new SemanticException("ArgumentError: print_FLOAT expects 1 argument");
            }
            String actual = inferExpressionType(functionCallNode.arguments.get(0));
            if (!"FLOAT".equals(actual) && !"float".equals(actual)) {
                throw new SemanticException("ArgumentError: print_FLOAT expects FLOAT but got " + actual);
            }
            return "void";
        }

        if ("print".equals(functionCallNode.name)) {
            if (functionCallNode.arguments.size() != 1) {
                throw new SemanticException("ArgumentError: print expects 1 argument");
            }
            inferExpressionType(functionCallNode.arguments.get(0));
            return "void";
        }

        if ("println".equals(functionCallNode.name)) {
            if (functionCallNode.arguments.size() > 1) {
                throw new SemanticException("ArgumentError: println expects 0 or 1 argument");
            }
            if (functionCallNode.arguments.size() == 1) {
                inferExpressionType(functionCallNode.arguments.get(0));
            }
            return "void";
        }

        if (functions.containsKey(functionCallNode.name)) {
            FunctionSignature signature = functions.get(functionCallNode.name);

            if (signature.getParameterTypes().size() != functionCallNode.arguments.size()) {
                throw new SemanticException("ArgumentError: wrong number of arguments for function " + functionCallNode.name);
            }

            for (int i = 0; i < functionCallNode.arguments.size(); i++) {
                String expected = signature.getParameterTypes().get(i);
                String actual = normalizeType(inferExpressionType(functionCallNode.arguments.get(i)));

                if ("ANY".equals(expected)) {
                    continue;
                }

                expected = normalizeType(expected);
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
                throw new SemanticException("OperatorError: arithmetic operator '" + op + "' requires numeric operands, got " + leftType + " and " + rightType);
            }
            return numericResultType(leftType, rightType);
        }

        if (isLogicalOperator(op)) {
            if (!isBooleanType(leftType) || !isBooleanType(rightType)) {
                throw new SemanticException("OperatorError: logical operator '" + op + "' requires BOOLEAN operands");
            }
            return "BOOLEAN";
        }

        if (isComparisonOperator(op)) {
            if (!sameComparableType(leftType, rightType)) {
                throw new SemanticException("OperatorError: comparison operator '" + op + "' requires operands of same type");
            }
            return "BOOLEAN";
        }

        throw new SemanticException("OperatorError: unsupported operator '" + op + "'");
    }

    private String inferArrayCreationType(ArrayCreationNode arrayCreationNode) {
        String elementType = normalizeType(arrayCreationNode.elementType);
        if (!typeExists(elementType)) {
            throw new SemanticException("TypeError: unknown array element type '" + elementType + "'");
        }

        String sizeType = normalizeType(inferExpressionType(arrayCreationNode.size));
        if (!sameType("INT", sizeType)) {
            throw new SemanticException("TypeError: array size must be INT, got " + sizeType);
        }

        return elementType + "[]";
    }

    private String inferArrayAccessType(ArrayAccessNode arrayAccessNode) {
        String arrayType = normalizeType(inferExpressionType(arrayAccessNode.array));
        if (arrayType == null || !arrayType.endsWith("[]")) {
            throw new SemanticException("TypeError: cannot index non-array type " + arrayType);
        }

        String indexType = normalizeType(inferExpressionType(arrayAccessNode.index));
        if (!sameType("INT", indexType)) {
            throw new SemanticException("TypeError: array index must be INT, got " + indexType);
        }

        return arrayType.substring(0, arrayType.length() - 2);
    }

    private String inferFieldAccessType(FieldAccessNode fieldAccessNode) {
        String objectType = normalizeType(inferExpressionType(fieldAccessNode.object));
        if (objectType == null || objectType.endsWith("[]")) {
            throw new SemanticException("TypeError: field access on non-collection type " + objectType);
        }

        Map<String, String> fields = collectionFieldTypes.get(objectType);
        if (fields == null) {
            throw new SemanticException("TypeError: field access on non-collection type " + objectType);
        }

        String fieldType = fields.get(fieldAccessNode.field);
        if (fieldType == null) {
            throw new SemanticException("TypeError: unknown field '" + fieldAccessNode.field + "' for type " + objectType);
        }

        return fieldType;
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

    private boolean isNumericType(String type) {
        String normalized = normalizeType(type);
        return "INT".equals(normalized) || "FLOAT".equals(normalized);
    }

    private String numericResultType(String left, String right) {
        left = normalizeType(left);
        right = normalizeType(right);
        if ("FLOAT".equals(left) || "FLOAT".equals(right)) {
            return "FLOAT";
        }
        return "INT";
    }

    private boolean sameNumericType(String left, String right) {
        return isNumericType(left) && isNumericType(right);
    }

    private boolean sameComparableType(String left, String right) {
        if (isNumericType(left) && isNumericType(right)) {
            return true;
        }
        return sameType(left, right);
    }

    private boolean sameType(String left, String right) {
        return normalizeType(left).equals(normalizeType(right));
    }

    private boolean typeExists(String type) {
        String normalized = normalizeType(type);

        if (knownTypes.contains(normalized)) {
            return true;
        }

        if (normalized.endsWith("[]")) {
            String elementType = normalized.substring(0, normalized.length() - 2);
            return typeExists(elementType);
        }

        return false;
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "void";
        }

        if ("BOOL".equals(type)) {
            return "BOOLEAN";
        }

        if ("BOOL[]".equals(type)) {
            return "BOOLEAN[]";
        }

        return type;
    }

    private boolean isReservedName(String name) {
        if (name == null) {
            return false;
        }

        return name.equals("def")
                || name.equals("coll")
                || name.equals("if")
                || name.equals("else")
                || name.equals("while")
                || name.equals("for")
                || name.equals("return")
                || name.equals("true")
                || name.equals("false")
                || name.equals("not")
                || name.equals("INT")
                || name.equals("FLOAT")
                || name.equals("STRING")
                || name.equals("BOOLEAN")
                || name.equals("BOOL")
                || name.equals("ARRAY")
                || name.equals("void");
    }


}
