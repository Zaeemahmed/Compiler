package compiler.Parser;

import java.util.List;

public abstract class StatementNode {
    public abstract void print();
}

class VarDeclarationNode extends StatementNode {
    private final boolean isFinal;
    private final String type;
    private final String identifier;
    private final ExpressionNode value;

    public VarDeclarationNode(boolean isFinal, String type, String identifier, ExpressionNode value) {
        this.isFinal = isFinal;
        this.type = type;
        this.identifier = identifier;
        this.value = value;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public String getType() {
        return type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public ExpressionNode getValue() {
        return value;
    }

    @Override
    public void print() {
        System.out.println("VarDeclaration: " + (isFinal ? "final " : "") + type + " " + identifier);
        if (value != null) {
            value.print();
        }
    }
}

class AssignmentNode extends StatementNode {
    private final String identifier;
    private final ExpressionNode value;

    public AssignmentNode(String identifier, ExpressionNode value) {
        this.identifier = identifier;
        this.value = value;
    }

    public String getIdentifier() {
        return identifier;
    }

    public ExpressionNode getValue() {
        return value;
    }

    @Override
    public void print() {
        System.out.println("Assignment: " + identifier);
        value.print();
    }
}

class IfNode extends StatementNode {
    private final ExpressionNode condition;
    private final List<StatementNode> thenBranch;
    private final List<StatementNode> elseBranch;

    public IfNode(ExpressionNode condition,
                  List<StatementNode> thenBranch,
                  List<StatementNode> elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    public ExpressionNode getCondition() {
        return condition;
    }

    public List<StatementNode> getThenBranch() {
        return thenBranch;
    }

    public List<StatementNode> getElseBranch() {
        return elseBranch;
    }

    @Override
    public void print() {
        System.out.println("If Statement:");

        System.out.println("Condition:");
        condition.print();

        System.out.println("Then:");
        for (StatementNode stmt : thenBranch) {
            stmt.print();
        }

        if (elseBranch != null) {
            System.out.println("Else:");
            for (StatementNode stmt : elseBranch) {
                stmt.print();
            }
        }
    }
}

class WhileNode extends StatementNode {
    private final ExpressionNode condition;
    private final List<StatementNode> body;

    public WhileNode(ExpressionNode condition, List<StatementNode> body) {
        this.condition = condition;
        this.body = body;
    }

    public ExpressionNode getCondition() {
        return condition;
    }

    public List<StatementNode> getBody() {
        return body;
    }

    @Override
    public void print() {
        System.out.println("While Statement:");
        condition.print();

        for (StatementNode stmt : body) {
            stmt.print();
        }
    }
}

class ForNode extends StatementNode {
    private final String identifier;
    private final ExpressionNode rangeStart;
    private final ExpressionNode rangeEnd;
    private final ExpressionNode update;
    private final List<StatementNode> body;

    public ForNode(String identifier,
                   ExpressionNode rangeStart,
                   ExpressionNode rangeEnd,
                   ExpressionNode update,
                   List<StatementNode> body) {
        this.identifier = identifier;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.update = update;
        this.body = body;
    }

    public String getIdentifier() {
        return identifier;
    }

    public ExpressionNode getRangeStart() {
        return rangeStart;
    }

    public ExpressionNode getRangeEnd() {
        return rangeEnd;
    }

    public ExpressionNode getUpdate() {
        return update;
    }

    public List<StatementNode> getBody() {
        return body;
    }

    @Override
    public void print() {
        System.out.println("For Statement: " + identifier);
        System.out.println("Range Start:");
        rangeStart.print();
        System.out.println("Range End:");
        rangeEnd.print();
        System.out.println("Update:");
        update.print();
        System.out.println("Body:");
        for (StatementNode stmt : body) {
            stmt.print();
        }
    }
}

class ReturnNode extends StatementNode {
    private final ExpressionNode value;

    public ReturnNode(ExpressionNode value) {
        this.value = value;
    }

    public ExpressionNode getValue() {
        return value;
    }

    @Override
    public void print() {
        System.out.println("Return Statement:");
        if (value != null) {
            value.print();
        }
    }
}

class ExpressionStatementNode extends StatementNode {
    private final ExpressionNode expression;

    public ExpressionStatementNode(ExpressionNode expression) {
        this.expression = expression;
    }

    public ExpressionNode getExpression() {
        return expression;
    }

    @Override
    public void print() {
        System.out.println("Expression Statement:");
        expression.print();
    }
}

class FunctionNode extends StatementNode {
    String name;
    String returnType;
    List<VarDeclarationNode> parameters;
    List<StatementNode> body;

    public FunctionNode(String name, String returnType, List<VarDeclarationNode> parameters, List<StatementNode> body) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.body = body;
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<VarDeclarationNode> getParameters() {
        return parameters;
    }

    public List<StatementNode> getBody() {
        return body;
    }

    @Override
    public void print() {
        System.out.println("Function: " + name + " returns " + returnType);
        System.out.println("Parameters:");
        for (VarDeclarationNode param : parameters) {
            param.print();
        }
        System.out.println("Body:");
        for (StatementNode stmt : body) {
            stmt.print();
        }
    }
}

class CollectionNode extends StatementNode {
    String name;
    List<VarDeclarationNode> fields;

    public CollectionNode(String name, List<VarDeclarationNode> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public List<VarDeclarationNode> getFields() {
        return fields;
    }

    @Override
    public void print() {
        System.out.println("Collection: " + name);
        for (VarDeclarationNode field : fields) {
            field.print();
        }
    }
}
