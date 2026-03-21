package compiler.Parser;

public class AST {
    private final ProgramNode program;

    public AST(ProgramNode program) {
        this.program = program;
    }

    public ProgramNode getProgram() {
        return program;
    }

    public void print() {
        System.out.println("=== AST ===");
        if (program != null) {
            program.print();
        } else {
            System.out.println("Empty program");
        }
    }
}