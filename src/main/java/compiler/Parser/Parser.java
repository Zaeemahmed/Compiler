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

    private boolean check(Symbol.TokenType type) {
        return current != null && current.getType() == type;
    }

    private boolean match(Symbol.TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private void expect(Symbol.TokenType type, String message) {
        if (!match(type)) {
            throw new ParseException(message + " | found: " + current);
        }
    }

    public Object getAST() {
        return parseProgram();
    }

    private Object parseProgram() {
        // TODO: parse top-level declarations/functions based on grammar.txt
        return null;
    }
}
