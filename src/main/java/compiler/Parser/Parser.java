package compiler.Parser;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;

public class Parser {
    private final Lexer lexer;
    private Symbol current;
    private Symbol next;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
        this.current = lexer.getNextSymbol();
        this.next = lexer.getNextSymbol();
    }

    private void advance() {
        current = next;
        next = lexer.getNextSymbol();
    }

    private boolean check(Symbol.TokenType type) {
        return current != null && current.getType() == type;
    }

    private boolean checkNext(Symbol.TokenType type) {
        return next != null && next.getType() == type;
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

    private boolean isEOF() {
        return current != null && current.getType() == Symbol.TokenType.EOF;
    }

    private String lexemeOf(Symbol s) {
        return s == null ? null : s.getLexeme();
    }

    private boolean hasLexeme(Symbol s, String lexeme) {
        return s != null && lexeme != null && lexeme.equals(lexemeOf(s));
    }

    private boolean isKeyword(Symbol s, Symbol.TokenType tokenType, String lexeme) {
        if (s == null) return false;
        return s.getType() == tokenType || hasLexeme(s, lexeme);
    }

    private boolean matchKeyword(Symbol.TokenType tokenType, String lexeme) {
        if (isKeyword(current, tokenType, lexeme)) {
            advance();
            return true;
        }
        return false;
    }

    private void expectKeyword(Symbol.TokenType tokenType, String lexeme, String message) {
        if (!matchKeyword(tokenType, lexeme)) {
            throw new ParseException(message + " | found: " + current);
        }
    }

    private boolean isTypeToken(Symbol s) {
        String lex = lexemeOf(s);
        return "INT".equals(lex)
                || "FLOAT".equals(lex)
                || "STRING".equals(lex)
                || "BOOLEAN".equals(lex)
                || "BOOL".equals(lex);
    }

    private boolean isLiteral(Symbol s) {
        if (s == null) return false;

        Symbol.TokenType t = s.getType();

        if ((t == Symbol.TokenType.INT
                || t == Symbol.TokenType.FLOAT
                || t == Symbol.TokenType.STRING
                || t == Symbol.TokenType.BOOL) && !isTypeToken(s)) {
            return true;
        }

        String lex = lexemeOf(s);
        return "true".equals(lex) || "false".equals(lex);
    }

    private boolean startsVarDeclaration() {
        if (isKeyword(current, Symbol.TokenType.KW_FINAL, "final")) return true;
        return isTypeToken(current) && checkNext(Symbol.TokenType.IDENTIFIER);
    }

    private boolean startsAssignment() {
        return check(Symbol.TokenType.IDENTIFIER) && checkNext(Symbol.TokenType.ASSIGN);
    }

    private boolean startsExpression() {
        return check(Symbol.TokenType.LPAREN)
                || check(Symbol.TokenType.IDENTIFIER)
                || isLiteral(current)
                || check(Symbol.TokenType.MINUS)
                || isKeyword(current, Symbol.TokenType.KW_NOT, "not");
    }

    public Object getAST() {
        parseProgram();
        return "Syntax OK";
    }

    private void parseProgram() {
        while (!isEOF()) {
            parseStatement();
        }
        expect(Symbol.TokenType.EOF, "Expected end of file");
    }

    private void parseStatement() {
        if (startsVarDeclaration()) {
            parseVarDeclaration();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after variable declaration");
            return;
        }

        if (startsAssignment()) {
            parseAssignment();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after assignment");
            return;
        }

        if (isKeyword(current, Symbol.TokenType.KW_IF, "if")) {
            parseIfStatement();
            return;
        }

        if (isKeyword(current, Symbol.TokenType.KW_WHILE, "while")) {
            parseWhileStatement();
            return;
        }

        if (isKeyword(current, Symbol.TokenType.KW_RETURN, "return")) {
            parseReturnStatement();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after return statement");
            return;
        }

        if (startsExpression()) {
            parseExpression();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after expression");
            return;
        }

        throw new ParseException("Invalid statement start: " + current);
    }

    private void parseVarDeclaration() {
        matchKeyword(Symbol.TokenType.KW_FINAL, "final");
        parseType();
        expect(Symbol.TokenType.IDENTIFIER, "Expected identifier in variable declaration");

        if (match(Symbol.TokenType.ASSIGN)) {
            parseExpression();
        }
    }

    private void parseType() {
        if (isTypeToken(current)) {
            advance();
            return;
        }
        throw new ParseException("Expected type keyword | found: " + current);
    }

    private void parseAssignment() {
        expect(Symbol.TokenType.IDENTIFIER, "Expected identifier on left side of assignment");
        expect(Symbol.TokenType.ASSIGN, "Expected '=' in assignment");
        parseExpression();
    }

    private void parseIfStatement() {
        expectKeyword(Symbol.TokenType.KW_IF, "if", "Expected 'if'");
        expect(Symbol.TokenType.LPAREN, "Expected '(' after if");
        parseExpression();
        expect(Symbol.TokenType.RPAREN, "Expected ')' after if condition");
        parseBlock();

        if (matchKeyword(Symbol.TokenType.KW_ELSE, "else")) {
            parseBlock();
        }
    }

    private void parseWhileStatement() {
        expectKeyword(Symbol.TokenType.KW_WHILE, "while", "Expected 'while'");
        expect(Symbol.TokenType.LPAREN, "Expected '(' after while");
        parseExpression();
        expect(Symbol.TokenType.RPAREN, "Expected ')' after while condition");
        parseBlock();
    }

    private void parseReturnStatement() {
        expectKeyword(Symbol.TokenType.KW_RETURN, "return", "Expected 'return'");
        if (!check(Symbol.TokenType.SEMICOLON)) {
            parseExpression();
        }
    }

    private void parseBlock() {
        expect(Symbol.TokenType.LBRACE, "Expected '{' to start block");
        while (!check(Symbol.TokenType.RBRACE) && !isEOF()) {
            parseStatement();
        }
        expect(Symbol.TokenType.RBRACE, "Expected '}' to close block");
    }

    private void parseExpression() {
        parseOr();
    }

    private void parseOr() {
        parseAnd();
        while (match(Symbol.TokenType.OR)) {
            parseAnd();
        }
    }

    private void parseAnd() {
        parseEquality();
        while (match(Symbol.TokenType.AND)) {
            parseEquality();
        }
    }

    private void parseEquality() {
        parseComparison();
        while (check(Symbol.TokenType.EQ) || check(Symbol.TokenType.NEQ)) {
            advance();
            parseComparison();
        }
    }

    private void parseComparison() {
        parseAdditive();
        while (check(Symbol.TokenType.LT)
                || check(Symbol.TokenType.LE)
                || check(Symbol.TokenType.GT)
                || check(Symbol.TokenType.GE)) {
            advance();
            parseAdditive();
        }
    }

    private void parseAdditive() {
        parseMultiplicative();
        while (check(Symbol.TokenType.PLUS) || check(Symbol.TokenType.MINUS)) {
            advance();
            parseMultiplicative();
        }
    }

    private void parseMultiplicative() {
        parseUnary();
        while (check(Symbol.TokenType.STAR)
                || check(Symbol.TokenType.SLASH)
                || check(Symbol.TokenType.MOD)) {
            advance();
            parseUnary();
        }
    }

    private void parseUnary() {
        if (match(Symbol.TokenType.MINUS) || matchKeyword(Symbol.TokenType.KW_NOT, "not")) {
            parseUnary();
            return;
        }
        parsePrimary();
    }

    private void parsePrimary() {
        if (match(Symbol.TokenType.LPAREN)) {
            parseExpression();
            expect(Symbol.TokenType.RPAREN, "Expected ')' after expression");
            return;
        }

        if (isLiteral(current)) {
            advance();
            return;
        }

        if (check(Symbol.TokenType.IDENTIFIER)) {
            advance();
            return;
        }

        throw new ParseException("Expected primary expression, found: " + current);
    }
}
