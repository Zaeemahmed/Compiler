package compiler.Parser;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;

import java.util.ArrayList;
import java.util.List;

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

    private String consumeOperator() {
        String op = lexemeOf(current);
        if (op == null) {
            op = current.getType().name();
        }
        advance();
        return op;
    }

    public AST getAST() {
        ProgramNode program = parseProgram();
        return new AST(program);
    }

    private ProgramNode parseProgram() {
        List<StatementNode> statements = new ArrayList<>();

        while (!isEOF()) {
            statements.add(parseStatement());
        }

        expect(Symbol.TokenType.EOF, "Expected end of file");
        return new ProgramNode(statements);
    }

    private StatementNode parseStatement() {
        if (startsVarDeclaration()) {
            StatementNode stmt = parseVarDeclaration();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after variable declaration");
            return stmt;
        }

        if (startsAssignment()) {
            StatementNode stmt = parseAssignment();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after assignment");
            return stmt;
        }

        if (isKeyword(current, Symbol.TokenType.KW_IF, "if")) {
            return parseIfStatement();
        }

        if (isKeyword(current, Symbol.TokenType.KW_WHILE, "while")) {
            return parseWhileStatement();
        }

        if (isKeyword(current, Symbol.TokenType.KW_RETURN, "return")) {
            StatementNode stmt = parseReturnStatement();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after return statement");
            return stmt;
        }

        if (startsExpression()) {
            ExpressionNode expr = parseExpression();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after expression");
            return new ExpressionStatementNode(expr);
        }

        throw new ParseException("Invalid statement start: " + current);
    }

    private VarDeclarationNode parseVarDeclaration() {
        boolean isFinal = matchKeyword(Symbol.TokenType.KW_FINAL, "final");
        String type = parseType();

        String identifier = lexemeOf(current);
        expect(Symbol.TokenType.IDENTIFIER, "Expected identifier in variable declaration");

        ExpressionNode value = null;
        if (match(Symbol.TokenType.ASSIGN)) {
            value = parseExpression();
        }

        return new VarDeclarationNode(isFinal, type, identifier, value);
    }

    private String parseType() {
        if (!isTypeToken(current)) {
            throw new ParseException("Expected type keyword | found: " + current);
        }

        String type = lexemeOf(current);
        advance();
        return type;
    }

    private AssignmentNode parseAssignment() {
        String identifier = lexemeOf(current);
        expect(Symbol.TokenType.IDENTIFIER, "Expected identifier on left side of assignment");
        expect(Symbol.TokenType.ASSIGN, "Expected '=' in assignment");
        ExpressionNode value = parseExpression();
        return new AssignmentNode(identifier, value);
    }

    private IfNode parseIfStatement() {
        expectKeyword(Symbol.TokenType.KW_IF, "if", "Expected 'if'");
        expect(Symbol.TokenType.LPAREN, "Expected '(' after if");
        ExpressionNode condition = parseExpression();
        expect(Symbol.TokenType.RPAREN, "Expected ')' after if condition");

        List<StatementNode> thenBranch = parseBlockStatements();
        List<StatementNode> elseBranch = null;

        if (matchKeyword(Symbol.TokenType.KW_ELSE, "else")) {
            elseBranch = parseBlockStatements();
        }

        return new IfNode(condition, thenBranch, elseBranch);
    }

    private WhileNode parseWhileStatement() {
        expectKeyword(Symbol.TokenType.KW_WHILE, "while", "Expected 'while'");
        expect(Symbol.TokenType.LPAREN, "Expected '(' after while");
        ExpressionNode condition = parseExpression();
        expect(Symbol.TokenType.RPAREN, "Expected ')' after while condition");

        List<StatementNode> body = parseBlockStatements();
        return new WhileNode(condition, body);
    }

    private ReturnNode parseReturnStatement() {
        expectKeyword(Symbol.TokenType.KW_RETURN, "return", "Expected 'return'");

        ExpressionNode value = null;
        if (!check(Symbol.TokenType.SEMICOLON)) {
            value = parseExpression();
        }

        return new ReturnNode(value);
    }

    private List<StatementNode> parseBlockStatements() {
        expect(Symbol.TokenType.LBRACE, "Expected '{' to start block");

        List<StatementNode> statements = new ArrayList<>();
        while (!check(Symbol.TokenType.RBRACE) && !isEOF()) {
            statements.add(parseStatement());
        }

        expect(Symbol.TokenType.RBRACE, "Expected '}' to close block");
        return statements;
    }

    private ExpressionNode parseExpression() {
        return parseOr();
    }

    private ExpressionNode parseOr() {
        ExpressionNode expr = parseAnd();

        while (check(Symbol.TokenType.OR)) {
            String op = consumeOperator();
            ExpressionNode right = parseAnd();
            expr = new OperationNode(op, expr, right);
        }

        return expr;
    }

    private ExpressionNode parseAnd() {
        ExpressionNode expr = parseEquality();

        while (check(Symbol.TokenType.AND)) {
            String op = consumeOperator();
            ExpressionNode right = parseEquality();
            expr = new OperationNode(op, expr, right);
        }

        return expr;
    }

    private ExpressionNode parseEquality() {
        ExpressionNode expr = parseComparison();

        while (check(Symbol.TokenType.EQ) || check(Symbol.TokenType.NEQ)) {
            String op = consumeOperator();
            ExpressionNode right = parseComparison();
            expr = new OperationNode(op, expr, right);
        }

        return expr;
    }

    private ExpressionNode parseComparison() {
        ExpressionNode expr = parseAdditive();

        while (check(Symbol.TokenType.LT)
                || check(Symbol.TokenType.LE)
                || check(Symbol.TokenType.GT)
                || check(Symbol.TokenType.GE)) {
            String op = consumeOperator();
            ExpressionNode right = parseAdditive();
            expr = new OperationNode(op, expr, right);
        }

        return expr;
    }

    private ExpressionNode parseAdditive() {
        ExpressionNode expr = parseMultiplicative();

        while (check(Symbol.TokenType.PLUS) || check(Symbol.TokenType.MINUS)) {
            String op = consumeOperator();
            ExpressionNode right = parseMultiplicative();
            expr = new OperationNode(op, expr, right);
        }

        return expr;
    }

    private ExpressionNode parseMultiplicative() {
        ExpressionNode expr = parseUnary();

        while (check(Symbol.TokenType.STAR)
                || check(Symbol.TokenType.SLASH)
                || check(Symbol.TokenType.MOD)) {
            String op = consumeOperator();
            ExpressionNode right = parseUnary();
            expr = new OperationNode(op, expr, right);
        }

        return expr;
    }

    private ExpressionNode parseUnary() {
        if (check(Symbol.TokenType.MINUS)) {
            String op = consumeOperator();
            ExpressionNode right = parseUnary();
            return new OperationNode(op, new IntegerNode(0), right);
        }

        if (isKeyword(current, Symbol.TokenType.KW_NOT, "not")) {
            String op = lexemeOf(current);
            advance();
            ExpressionNode right = parseUnary();
            return new OperationNode(op, new BooleanNode(false), right);
        }

        return parsePrimary();
    }

    private ExpressionNode parsePrimary() {
        if (match(Symbol.TokenType.LPAREN)) {
            ExpressionNode expr = parseExpression();
            expect(Symbol.TokenType.RPAREN, "Expected ')' after expression");
            return expr;
        }

        if (isLiteral(current)) {
            Symbol literal = current;
            advance();
            return buildLiteralNode(literal);
        }

        if (check(Symbol.TokenType.IDENTIFIER)) {
            String name = lexemeOf(current);
            advance();
            return new IdentifierNode(name);
        }

        throw new ParseException("Expected primary expression, found: " + current);
    }

    private ExpressionNode buildLiteralNode(Symbol literal) {
        String lex = lexemeOf(literal);
        Symbol.TokenType type = literal.getType();

        if ("true".equals(lex) || "false".equals(lex)) {
            return new BooleanNode(Boolean.parseBoolean(lex));
        }

        if (type == Symbol.TokenType.INT) {
            try {
                return new IntegerNode(Integer.parseInt(lex));
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid integer literal: " + lex);
            }
        }

        if (type == Symbol.TokenType.FLOAT) {
            try {
                return new FloatNode(Float.parseFloat(lex));
            } catch (NumberFormatException e) {
                throw new ParseException("Invalid float literal: " + lex);
            }
        }

        if (type == Symbol.TokenType.STRING) {
            return new StringNode(stripQuotes(lex));
        }

        if (type == Symbol.TokenType.BOOL) {
            return new BooleanNode(Boolean.parseBoolean(lex));
        }

        throw new ParseException("Unsupported literal: " + literal);
    }

    private String stripQuotes(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
