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
    private Scope globalScope;
    private String currentFunctionReturnType;
    private int lambdaCounter;

    public SemanticAnalyzer() {
        knownTypes.add("INT");
        knownTypes.add("FLOAT");
        knownTypes.add("STRING");
        knownTypes.add("BOOLEAN");
        knownTypes.add("BOOL");
        knownTypes.add("void");
        knownTypes.add("FUNC");

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
        globalScope = currentScope;
        lambdaCounter = 0;
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

        String functionTarget = null;
        if (node.getValue() != null) {
            String valueType = inferExpressionType(node.getValue());
            if (!sameType(declaredType, valueType)) {
                throw new SemanticException("TypeError: cannot assign " + valueType + " to " + declaredType + " variable " + node.getIdentifier());
            }

            if (node.getValue() instanceof IdentifierNode identifierNode) {
                functionTarget = resolveFunctionTarget(identifierNode, declaredType);
            } else if (node.getValue() instanceof LambdaNode lambdaNode) {
                if (isFunctionType(declaredType) && !sameType(declaredType, inferExpressionType(lambdaNode))) {
                    throw new SemanticException("TypeError: lambda type does not match declared type " + declaredType);
                }
                functionTarget = lambdaNode.getFunctionName();
            }
        }

        if (functionTarget != null) {
            currentScope.define(node.getIdentifier(), declaredType, functionTarget);
        } else {
            currentScope.define(node.getIdentifier(), declaredType);
        }
    }

    private void analyzeAssignment(AssignmentNode node) {
        String targetType = currentScope.resolve(node.getIdentifier());

        if (targetType == null) {
            throw new SemanticException("ScopeError: variable used out of scope or before definition: " + node.getIdentifier());
        }

        String valueType = inferExpressionType(node.getValue());
        String functionTarget = null;

        if (node.getValue() instanceof IdentifierNode identifierNode) {
            functionTarget = resolveFunctionTarget(identifierNode, targetType);
            if (functionTarget != null) {
                currentScope.assign(node.getIdentifier(), targetType, functionTarget);
                return;
            }
        } else if (node.getValue() instanceof LambdaNode lambdaNode) {
            if (!sameType(targetType, inferExpressionType(lambdaNode))) {
                throw new SemanticException("TypeError: lambda type does not match target type " + targetType);
            }
            currentScope.assign(node.getIdentifier(), targetType, lambdaNode.getFunctionName());
            return;
        }

        if (!sameType(targetType, valueType)) {
            throw new SemanticException("TypeError: cannot assign " + valueType + " to " + targetType + " variable " + node.getIdentifier());
        }

        if ("FUNC".equals(targetType)) {
            currentScope.assign(node.getIdentifier(), targetType, null);
        }
    }

    private String resolveFunctionTarget(IdentifierNode identifierNode, String expectedType) {
        if (functions.containsKey(identifierNode.name)) {
            FunctionSignature signature = functions.get(identifierNode.name);
            String functionType = "(" + String.join(",", signature.getParameterTypes()) + ")->" + signature.getReturnType();
            if ("FUNC".equals(expectedType) || sameType(expectedType, functionType)) {
                return identifierNode.name;
            }
        }

        Scope.VariableInfo sourceInfo = currentScope.resolveVariable(identifierNode.name);
        if (sourceInfo != null && ("FUNC".equals(sourceInfo.type) || isFunctionType(sourceInfo.type))) {
            if (sourceInfo.functionTarget != null) {
                if ("FUNC".equals(expectedType) || sameType(expectedType, sourceInfo.type)) {
                    return sourceInfo.functionTarget;
                }
            }
        }

        return null;
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
            if (type != null) {
                return type;
            }
            if (functions.containsKey(identifierNode.name)) {
                FunctionSignature signature = functions.get(identifierNode.name);
                return "(" + String.join(",", signature.getParameterTypes()) + ")->" + signature.getReturnType();
            }
            throw new SemanticException("ScopeError: variable used out of scope or before definition: " + identifierNode.name);
        }

        if (expr instanceof LambdaNode lambdaNode) {
            return inferLambdaType(lambdaNode);
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

    private String inferLambdaType(LambdaNode lambdaNode) {
        if (lambdaNode.getInferredType() != null) {
            return lambdaNode.getInferredType();
        }

        List<String> parameterTypes = new ArrayList<>();
        for (VarDeclarationNode parameter : lambdaNode.getParameters()) {
            String type = normalizeType(parameter.getType());
            if (!typeExists(type)) {
                throw new SemanticException("TypeError: unknown lambda parameter type '" + type + "'");
            }
            parameterTypes.add(type);
        }

        Scope savedScope = currentScope;
        String savedReturnType = currentFunctionReturnType;
        currentScope = new Scope(globalScope);
        for (VarDeclarationNode parameter : lambdaNode.getParameters()) {
            currentScope.define(parameter.getIdentifier(), normalizeType(parameter.getType()));
        }

        String returnType;
        if (lambdaNode.hasExpressionBody()) {
            returnType = inferExpressionType(lambdaNode.getExpressionBody());
        } else {
            List<String> returnTypes = new ArrayList<>();
            collectReturnTypes(lambdaNode.getBlockBody(), returnTypes);
            if (returnTypes.isEmpty()) {
                returnType = "void";
            } else {
                returnType = normalizeType(returnTypes.get(0));
                for (String type : returnTypes) {
                    if (!sameType(returnType, type)) {
                        throw new SemanticException("TypeError: inconsistent return types in lambda");
                    }
                }
            }

            currentFunctionReturnType = returnType;
            for (StatementNode statement : lambdaNode.getBlockBody()) {
                analyzeStatement(statement);
            }
        }

        currentScope = savedScope;
        currentFunctionReturnType = savedReturnType;

        String normalizedReturnType = normalizeType(returnType);
        String functionType = "(" + String.join(",", parameterTypes) + ")->" + normalizedReturnType;
        lambdaNode.setInferredType(functionType);
        if (lambdaNode.getFunctionName() == null) {
            lambdaNode.setFunctionName("__lambda" + lambdaCounter++);
        }

        functions.put(lambdaNode.getFunctionName(), new FunctionSignature(lambdaNode.getFunctionName(), normalizedReturnType, parameterTypes));
        return functionType;
    }

    private void collectReturnTypes(List<StatementNode> statements, List<String> returnTypes) {
        for (StatementNode statement : statements) {
            if (statement instanceof ReturnNode returnNode) {
                if (returnNode.getValue() == null) {
                    returnTypes.add("void");
                } else {
                    returnTypes.add(inferExpressionType(returnNode.getValue()));
                }
            } else if (statement instanceof IfNode ifNode) {
                collectReturnTypes(ifNode.getThenBranch(), returnTypes);
                if (ifNode.getElseBranch() != null) {
                    collectReturnTypes(ifNode.getElseBranch(), returnTypes);
                }
            } else if (statement instanceof WhileNode whileNode) {
                collectReturnTypes(whileNode.getBody(), returnTypes);
            } else if (statement instanceof ForNode forNode) {
                collectReturnTypes(forNode.getBody(), returnTypes);
            }
        }
    }

    private String inferFunctionCallType(FunctionCallNode functionCallNode) {
        String functionName = functionCallNode.getName();

        if (functionName != null) {
            switch (functionName) {
                case "length":
                    if (functionCallNode.arguments.size() != 1) {
                        throw new SemanticException("ArgumentError: length expects 1 argument");
                    }
                    String actual = normalizeType(inferExpressionType(functionCallNode.arguments.get(0)));
                    if (!actual.endsWith("[]") && !"STRING".equals(actual)) {
                        throw new SemanticException("ArgumentError: length expects an array or string but got " + actual);
                    }
                    return "INT";
                case "print_INT":
                    if (functionCallNode.arguments.size() != 1) {
                        throw new SemanticException("ArgumentError: print_INT expects 1 argument");
                    }
                    actual = inferExpressionType(functionCallNode.arguments.get(0));
                    if (!sameType("INT", actual)) {
                        throw new SemanticException("ArgumentError: print_INT expects INT but got " + actual);
                    }
                    return "void";
                case "print_FLOAT":
                    if (functionCallNode.arguments.size() != 1) {
                        throw new SemanticException("ArgumentError: print_FLOAT expects 1 argument");
                    }
                    actual = inferExpressionType(functionCallNode.arguments.get(0));
                    if (!sameType("FLOAT", actual)) {
                        throw new SemanticException("ArgumentError: print_FLOAT expects FLOAT but got " + actual);
                    }
                    return "void";
                case "print":
                    if (functionCallNode.arguments.size() != 1) {
                        throw new SemanticException("ArgumentError: print expects 1 argument");
                    }
                    inferExpressionType(functionCallNode.arguments.get(0));
                    return "void";
                case "println":
                    if (functionCallNode.arguments.size() > 1) {
                        throw new SemanticException("ArgumentError: println expects 0 or 1 argument");
                    }
                    if (functionCallNode.arguments.size() == 1) {
                        inferExpressionType(functionCallNode.arguments.get(0));
                    }
                    return "void";
                case "read_INT":
                    if (functionCallNode.arguments.size() != 0) {
                        throw new SemanticException("ArgumentError: read_INT expects no arguments");
                    }
                    return "INT";
                case "read_FLOAT":
                    if (functionCallNode.arguments.size() != 0) {
                        throw new SemanticException("ArgumentError: read_FLOAT expects no arguments");
                    }
                    return "FLOAT";
                case "read_STRING":
                    if (functionCallNode.arguments.size() != 0) {
                        throw new SemanticException("ArgumentError: read_STRING expects no arguments");
                    }
                    return "STRING";
                case "read_BOOL":
                    if (functionCallNode.arguments.size() != 0) {
                        throw new SemanticException("ArgumentError: read_BOOL expects no arguments");
                    }
                    return "BOOLEAN";
            }

            if (functions.containsKey(functionName)) {
                FunctionSignature signature = functions.get(functionName);

                if (signature.getParameterTypes().size() != functionCallNode.arguments.size()) {
                    throw new SemanticException("ArgumentError: wrong number of arguments for function " + functionName);
                }

                for (int i = 0; i < functionCallNode.arguments.size(); i++) {
                    String expected = signature.getParameterTypes().get(i);
                    String actual = normalizeType(inferExpressionType(functionCallNode.arguments.get(i)));

                    if ("ANY".equals(expected)) {
                        continue;
                    }

                    expected = normalizeType(expected);
                    if (!sameType(expected, actual)) {
                        throw new SemanticException("ArgumentError: expected " + expected + " but got " + actual + " in call to " + functionName);
                    }
                }

                return normalizeType(signature.getReturnType());
            }

            if (collectionConstructorTypes.containsKey(functionName)) {
                List<String> expectedTypes = collectionConstructorTypes.get(functionName);

                if (expectedTypes.size() != functionCallNode.arguments.size()) {
                    throw new SemanticException("ArgumentError: wrong number of arguments for collection constructor " + functionName);
                }

                for (int i = 0; i < functionCallNode.arguments.size(); i++) {
                    String expected = normalizeType(expectedTypes.get(i));
                    String actual = normalizeType(inferExpressionType(functionCallNode.arguments.get(i)));
                    if (!sameType(expected, actual)) {
                        throw new SemanticException("ArgumentError: expected " + expected + " but got " + actual + " in constructor " + functionName);
                    }
                }

                return functionName;
            }

            Scope.VariableInfo variableInfo = currentScope.resolveVariable(functionName);
            if (variableInfo != null) {
                if ("FUNC".equals(variableInfo.type)) {
                    if (variableInfo.functionTarget != null) {
                        FunctionCallNode resolvedCall = new FunctionCallNode(new IdentifierNode(variableInfo.functionTarget), functionCallNode.arguments);
                        return inferFunctionCallType(resolvedCall);
                    }
                    return inferFunctionCallTypeForUnknownFUNCVariable(functionCallNode);
                }
                if (isFunctionType(variableInfo.type)) {
                    FunctionType functionTypeInfo = parseFunctionType(variableInfo.type);
                    validateFunctionCallArguments(functionCallNode, functionTypeInfo);
                    if (variableInfo.functionTarget != null) {
                        FunctionCallNode resolvedCall = new FunctionCallNode(new IdentifierNode(variableInfo.functionTarget), functionCallNode.arguments);
                        return inferFunctionCallType(resolvedCall);
                    }
                    return functionTypeInfo.returnType;
                }
            }

            throw new SemanticException("ScopeError: unknown function or collection " + functionName);
        }

        String functionType = inferExpressionType(functionCallNode.getFunction());
        if (isFunctionType(functionType)) {
            FunctionType functionTypeInfo = parseFunctionType(functionType);
            validateFunctionCallArguments(functionCallNode, functionTypeInfo);
            return functionTypeInfo.returnType;
        }

        if ("FUNC".equals(functionType)) {
            return "FUNC";
        }

        throw new SemanticException("TypeError: attempted to call non-function expression of type " + functionType);
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
        left = normalizeType(left);
        right = normalizeType(right);

        if ("FUNC".equals(left) && isFunctionType(right)) {
            return true;
        }
        if ("FUNC".equals(right) && isFunctionType(left)) {
            return true;
        }

        return left.equals(right);
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

        if (isFunctionType(normalized)) {
            FunctionType functionType = parseFunctionType(normalized);
            if (functionType == null) {
                return false;
            }
            if (!typeExists(functionType.returnType)) {
                return false;
            }
            for (String parameterType : functionType.parameterTypes) {
                if (!typeExists(parameterType)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "void";
        }

        type = type.trim();
        if ("BOOL".equals(type)) {
            return "BOOLEAN";
        }

        if ("BOOL[]".equals(type)) {
            return "BOOLEAN[]";
        }

        if (type.endsWith("[]")) {
            return normalizeType(type.substring(0, type.length() - 2)) + "[]";
        }

        if (isFunctionType(type)) {
            FunctionType functionType = parseFunctionType(type);
            if (functionType == null) {
                return type;
            }
            StringBuilder builder = new StringBuilder("(");
            builder.append(String.join(",", functionType.parameterTypes));
            builder.append(")->");
            builder.append(functionType.returnType);
            return builder.toString();
        }

        return type;
    }

    private boolean isFunctionType(String type) {
        if (type == null) {
            return false;
        }
        type = type.trim();
        return type.startsWith("(") && type.contains(")->");
    }

    private FunctionType parseFunctionType(String type) {
        type = type.trim();
        if (!type.startsWith("(")) {
            return null;
        }

        int depth = 0;
        int index = 1;
        while (index < type.length()) {
            char c = type.charAt(index);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
            index++;
        }

        if (index >= type.length() || type.charAt(index) != ')') {
            return null;
        }

        int arrowIndex = index + 1;
        if (arrowIndex >= type.length() || type.charAt(arrowIndex) != '-') {
            return null;
        }
        arrowIndex++;
        if (arrowIndex >= type.length() || type.charAt(arrowIndex) != '>') {
            return null;
        }

        String paramsText = type.substring(1, index).trim();
        String returnText = type.substring(arrowIndex + 1).trim();
        if (returnText.isEmpty()) {
            return null;
        }

        List<String> parameterTypes = new ArrayList<>();
        if (!paramsText.isEmpty()) {
            int start = 0;
            depth = 0;
            for (int i = 0; i < paramsText.length(); i++) {
                char c = paramsText.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    parameterTypes.add(normalizeType(paramsText.substring(start, i).trim()));
                    start = i + 1;
                }
            }
            parameterTypes.add(normalizeType(paramsText.substring(start).trim()));
        }

        FunctionType functionType = new FunctionType();
        functionType.parameterTypes = parameterTypes;
        functionType.returnType = normalizeType(returnText);
        return functionType;
    }

    private void validateFunctionCallArguments(FunctionCallNode functionCallNode, FunctionType functionTypeInfo) {
        if (functionTypeInfo.parameterTypes.size() != functionCallNode.arguments.size()) {
            throw new SemanticException("ArgumentError: wrong number of arguments for function type");
        }

        for (int i = 0; i < functionCallNode.arguments.size(); i++) {
            String expected = normalizeType(functionTypeInfo.parameterTypes.get(i));
            String actual = normalizeType(inferExpressionType(functionCallNode.arguments.get(i)));
            if (!sameType(expected, actual)) {
                throw new SemanticException("ArgumentError: expected " + expected + " but got " + actual + " in function type call");
            }
        }
    }

    private String inferFunctionCallTypeForUnknownFUNCVariable(FunctionCallNode functionCallNode) {
        List<String> argumentTypes = new ArrayList<>();
        for (ExpressionNode argument : functionCallNode.arguments) {
            argumentTypes.add(normalizeType(inferExpressionType(argument)));
        }

        String returnType = null;
        for (FunctionSignature function : functions.values()) {
            if (function.getParameterTypes().size() != argumentTypes.size()) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < argumentTypes.size(); i++) {
                String expected = normalizeType(function.getParameterTypes().get(i));
                if (!sameType(expected, argumentTypes.get(i))) {
                    match = false;
                    break;
                }
            }
            if (!match) {
                continue;
            }

            String candidateReturn = normalizeType(function.getReturnType());
            if (returnType == null) {
                returnType = candidateReturn;
            } else if (!sameType(returnType, candidateReturn)) {
                throw new SemanticException("TypeError: ambiguous return type for FUNC call " + functionCallNode.getName());
            }
        }

        if (returnType == null) {
            throw new SemanticException("TypeError: cannot call FUNC variable without a known target for " + functionCallNode.getName());
        }
        return returnType;
    }

    private static class FunctionType {
        List<String> parameterTypes;
        String returnType;
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
