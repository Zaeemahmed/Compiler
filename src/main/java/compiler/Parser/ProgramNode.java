package compiler.Parser;

import java.util.List;

public class ProgramNode {
    private final List<StatementNode> statements;

    public ProgramNode(List<StatementNode> statements) {
        this.statements = statements;
    }

    public List<StatementNode> getStatements() {
        return statements;
    }

    public void print() {
        System.out.println("Program:");
        for (StatementNode stmt : statements) {
            stmt.print();
        }
    }
}