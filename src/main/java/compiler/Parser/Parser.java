package compiler.Parser;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;

public class Parser {
    private final Lexer lexer;
    private Symbol current;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
        advance();
    }

    private void advance() {
        current = lexer.getNextSymbol();
    }

    public Object getAST() {
        return parseProgram();
    }

    private Object parseProgram() {
        return null;
    }
}
