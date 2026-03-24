package compiler.Lexer;

public class Symbol {

    public enum TokenType {
        ASSIGN, EQ, NEQ,
        LT, LE, GT, GE,
        AND, OR,
        PLUS, MINUS, STAR, SLASH, MOD,
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        DOT, SEMICOLON, COMMA,
        EOF,
        IDENTIFIER, INT, FLOAT, STRING, BOOL,
        KW_FINAL, KW_DEF, KW_FOR, KW_WHILE, KW_IF, KW_ELSE, KW_RETURN, KW_NOT, KW_ARRAY, KW_NEW, KW_COLL
    }

    private final TokenType type;
    private final String lexeme;

    public Symbol(TokenType type, String lexeme) {
        this.type = type;
        this.lexeme = lexeme;
    }

    public TokenType getType() {
        return type;
    }

    public String getLexeme() {
        return lexeme;
    }

    @Override
    public String toString() {
        return type + (lexeme == null ? "" : " (" + lexeme + ")");
    }
}
