package compiler.Parser;

import java.util.List;

abstract class ExpressionNode {
    public abstract void print();
}

class OperationNode extends ExpressionNode {
    String operator;
    ExpressionNode left;
    ExpressionNode right;

    public OperationNode(String operator, ExpressionNode left, ExpressionNode right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public void print() {
        System.out.println("Operation: " + operator);
        if (left != null) left.print();
        if (right != null) right.print();
    }
}

class IdentifierNode extends ExpressionNode {
    String name;

    public IdentifierNode(String name) {
        this.name = name;
    }

    @Override
    public void print() {
        System.out.println("Identifier: " + name);
    }
}

class IntegerNode extends ExpressionNode {
    int value;

    public IntegerNode(int value) {
        this.value = value;
    }

    @Override
    public void print() {
        System.out.println("Integer: " + value);
    }
}

class FloatNode extends ExpressionNode {
    float value;

    public FloatNode(float value) {
        this.value = value;
    }

    @Override
    public void print() {
        System.out.println("Float: " + value);
    }
}

class BooleanNode extends ExpressionNode {
    boolean value;

    public BooleanNode(boolean value) {
        this.value = value;
    }

    @Override
    public void print() {
        System.out.println("Boolean: " + value);
    }
}

class StringNode extends ExpressionNode {
    String value;

    public StringNode(String value) {
        this.value = value;
    }

    @Override
    public void print() {
        System.out.println("String: " + value);
    }
}

class FunctionCallNode extends ExpressionNode {
    ExpressionNode function;
    List<ExpressionNode> arguments;

    public FunctionCallNode(ExpressionNode function, List<ExpressionNode> arguments) {
        this.function = function;
        this.arguments = arguments;
    }

    public String getName() {
        if (function instanceof IdentifierNode) {
            return ((IdentifierNode) function).name;
        }
        return null; // For function variables
    }

    public ExpressionNode getFunction() {
        return function;
    }

    @Override
    public void print() {
        String functionName = getName();
        System.out.println("FunctionCall: " + (functionName != null ? functionName : "<function expression>"));
        for (ExpressionNode arg : arguments) {
            arg.print();
        }
    }
}

class LambdaNode extends ExpressionNode {
    private final List<VarDeclarationNode> parameters;
    private final ExpressionNode expressionBody;
    private final List<StatementNode> blockBody;
    private String functionName;
    private String inferredType;

    public LambdaNode(List<VarDeclarationNode> parameters, ExpressionNode expressionBody, List<StatementNode> blockBody) {
        this.parameters = parameters;
        this.expressionBody = expressionBody;
        this.blockBody = blockBody;
    }

    public List<VarDeclarationNode> getParameters() {
        return parameters;
    }

    public ExpressionNode getExpressionBody() {
        return expressionBody;
    }

    public List<StatementNode> getBlockBody() {
        return blockBody;
    }

    public boolean hasExpressionBody() {
        return expressionBody != null;
    }

    public boolean hasBlockBody() {
        return blockBody != null;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getInferredType() {
        return inferredType;
    }

    public void setInferredType(String inferredType) {
        this.inferredType = inferredType;
    }

    @Override
    public void print() {
        System.out.println("Lambda:");
        for (VarDeclarationNode parameter : parameters) {
            parameter.print();
        }
        if (expressionBody != null) {
            expressionBody.print();
        }
        if (blockBody != null) {
            for (StatementNode stmt : blockBody) {
                stmt.print();
            }
        }
    }
}

class ArrayCreationNode extends ExpressionNode {
    String elementType;
    ExpressionNode size;

    public ArrayCreationNode(String elementType, ExpressionNode size) {
        this.elementType = elementType;
        this.size = size;
    }

    @Override
    public void print() {
        System.out.println("ArrayCreation: " + elementType + "[]");
        size.print();
    }
}

class ArrayAccessNode extends ExpressionNode {
    ExpressionNode array;
    ExpressionNode index;

    public ArrayAccessNode(ExpressionNode array, ExpressionNode index) {
        this.array = array;
        this.index = index;
    }

    @Override
    public void print() {
        System.out.println("ArrayAccess");
        array.print();
        index.print();
    }
}

class FieldAccessNode extends ExpressionNode {
    ExpressionNode object;
    String field;

    public FieldAccessNode(ExpressionNode object, String field) {
        this.object = object;
        this.field = field;
    }

    @Override
    public void print() {
        System.out.println("FieldAccess: " + field);
        object.print();
    }
}
