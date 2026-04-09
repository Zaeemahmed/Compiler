package compiler.expression;

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
        left.print();
        right.print();
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