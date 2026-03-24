package compiler.Parser;

import java.util.ArrayList;
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

class NewObjectNode extends ExpressionNode {
    String className;
    List<ExpressionNode> arguments;

    public NewObjectNode(String className) {
        this.className = className;
        this.arguments = new ArrayList<>();
    }

    public NewObjectNode(String className, List<ExpressionNode> arguments) {
        this.className = className;
        this.arguments = arguments != null ? arguments : new ArrayList<>();
    }

    @Override
    public void print() {
        System.out.println("New Object: " + className + "(" + arguments.size() + " args)");
        for (ExpressionNode arg : arguments) {
            arg.print();
        }
    }
}

class ArrayLiteralNode extends ExpressionNode {
    ExpressionNode size;
    String type;

    public ArrayLiteralNode(String type, ExpressionNode size) {
        this.type = type;
        this.size = size;
    }

    @Override
    public void print() {
        System.out.println("Array of type: " + type + ", size: ");
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
        System.out.println("Array Access:");
        array.print();
        System.out.println("Index:");
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
        System.out.println("Field Access: " + field);
        object.print();
    }
}