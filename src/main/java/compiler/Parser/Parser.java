package compiler.Parser;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final Lexer lexer;
    private Symbol current;
    private Symbol next;
    private Symbol nextNext;
    private Symbol nextNextNext;

    public Parser(Lexer lexer) {
        this.lexer = lexer;
        this.current = lexer.getNextSymbol();
        this.next = lexer.getNextSymbol();
        this.nextNext = lexer.getNextSymbol();
        this.nextNextNext = lexer.getNextSymbol();
    }

    private void advance() {
        current = next;
        next = nextNext;
        nextNext = nextNextNext;
        nextNextNext = lexer.getNextSymbol();
    }

    private boolean check(Symbol.TokenType type) {
        return current != null && current.getType() == type;
    }

    private boolean checkNext(Symbol.TokenType type) {
        return next != null && next.getType() == type;
    }

    private Symbol lookahead(int distance) {
        return switch (distance) {
            case 0 -> current;
            case 1 -> next;
            case 2 -> nextNext;
            case 3 -> nextNextNext;
            default -> null;
        };
    }

    private boolean checkLookahead(int distance, Symbol.TokenType type) {
        Symbol symbol = lookahead(distance);
        return symbol != null && symbol.getType() == type;
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
        if (s == null) return false;

        String lex = lexemeOf(s);
        if ("def".equals(lex)
                || "final".equals(lex)
                || "for".equals(lex)
                || "while".equals(lex)
                || "if".equals(lex)
                || "else".equals(lex)
                || "return".equals(lex)
                || "not".equals(lex)
                || "new".equals(lex)
                || "coll".equals(lex)
                || "ARRAY".equals(lex)) {
            return false;
        }

        return "INT".equals(lex)
                || "FLOAT".equals(lex)
                || "STRING".equals(lex)
                || "BOOLEAN".equals(lex)
                || "BOOL".equals(lex)
                || s.getType() == Symbol.TokenType.IDENTIFIER;
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
        return isTypeToken(current)
                && (checkNext(Symbol.TokenType.IDENTIFIER)
            || (checkNext(Symbol.TokenType.LBRACKET)
                && checkLookahead(2, Symbol.TokenType.RBRACKET)
                && checkLookahead(3, Symbol.TokenType.IDENTIFIER)));
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

    private boolean startsRangeArrow() {
        return check(Symbol.TokenType.MINUS) && checkNext(Symbol.TokenType.GT);
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
        if (startsCollection()) {
            return parseCollection();
        }

        if (startsFunction()) {
            return parseFunction();
        }

        if (isKeyword(current, Symbol.TokenType.KW_IF, "if")) {
            return parseIfStatement();
        }

        if (isKeyword(current, Symbol.TokenType.KW_WHILE, "while")) {
            return parseWhileStatement();
        }

        if (isKeyword(current, Symbol.TokenType.KW_FOR, "for")) {
            return parseForStatement();
        }

        if (isKeyword(current, Symbol.TokenType.KW_RETURN, "return")) {
            StatementNode stmt = parseReturnStatement();
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after return statement");
            return stmt;
        }

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

        if (match(Symbol.TokenType.LBRACKET)) {
            expect(Symbol.TokenType.RBRACKET, "Expected ']'");
            type += "[]";
        }

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

    private ForNode parseForStatement() {
        expectKeyword(Symbol.TokenType.KW_FOR, "for", "Expected 'for'");
        expect(Symbol.TokenType.LPAREN, "Expected '(' after for");

        String identifier = lexemeOf(current);
        expect(Symbol.TokenType.IDENTIFIER, "Expected loop variable in for statement");
        expect(Symbol.TokenType.SEMICOLON, "Expected ';' after loop variable");

        ExpressionNode rangeStart = parseExpression();
        expect(Symbol.TokenType.MINUS, "Expected '->' in for range");
        expect(Symbol.TokenType.GT, "Expected '->' in for range");
        ExpressionNode rangeEnd = parseExpression();
        expect(Symbol.TokenType.SEMICOLON, "Expected ';' after for range");

        ExpressionNode update = parseExpression();
        expect(Symbol.TokenType.RPAREN, "Expected ')' after for update");

        List<StatementNode> body = parseBlockStatements();
        return new ForNode(identifier, rangeStart, rangeEnd, update, body);
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

        while (check(Symbol.TokenType.PLUS) || (check(Symbol.TokenType.MINUS) && !startsRangeArrow())) {
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

        ExpressionNode expr = parsePrimary();

        while (true) {
            if (check(Symbol.TokenType.LBRACKET)) {
                advance();
                if (check(Symbol.TokenType.RBRACKET)) {
                    throw new ParseException("Array index cannot be empty");
                }

                ExpressionNode index = parseExpression();

                if (!check(Symbol.TokenType.RBRACKET)) {
                    throw new ParseException("Expected ']'");
                }
                advance();

                expr = new ArrayAccessNode(expr, index);
            } else if (match(Symbol.TokenType.DOT)) {
                String field = lexemeOf(current);
                expect(Symbol.TokenType.IDENTIFIER, "Expected field name");
                expr = new FieldAccessNode(expr, field);
            } else {
                break;
            }
        }

        return expr;
    }

    private ExpressionNode parsePrimary() {
        if (isTypeToken(current) && "ARRAY".equals(lexemeOf(next))) {
            String type = lexemeOf(current);
            advance();
            return parseArrayLiteral(type);
        }

        if (isKeyword(current, Symbol.TokenType.KW_NEW, "new")) {
            advance();
            String className = lexemeOf(current);
            expect(Symbol.TokenType.IDENTIFIER, "Expected class name");

            expect(Symbol.TokenType.LPAREN, "Expected '('");
            expect(Symbol.TokenType.RPAREN, "Expected ')'");
            return new NewObjectNode(className);
        }

        if (match(Symbol.TokenType.LPAREN)) {
            ExpressionNode expr = parseExpression();
            expect(Symbol.TokenType.RPAREN, "Expected ')'");
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

            if (match(Symbol.TokenType.LPAREN)) {
                List<ExpressionNode> args = new ArrayList<>();
                if (!check(Symbol.TokenType.RPAREN)) {
                    do {
                        args.add(parseExpression());
                    } while (match(Symbol.TokenType.COMMA));
                }
                expect(Symbol.TokenType.RPAREN, "Expected ')'");
                return new NewObjectNode(name, args);
            }

            return new IdentifierNode(name);
        }

        throw new ParseException("Expected primary expression, found: " + current);
    }

    private CollectionNode parseCollection() {
        expectKeyword(Symbol.TokenType.KW_COLL, "coll", "Expected 'coll'");

        String name = lexemeOf(current);
        expect(Symbol.TokenType.IDENTIFIER, "Expected collection name after 'coll'");

        expect(Symbol.TokenType.LBRACE, "Expected '{' after collection name");

        List<VarDeclarationNode> fields = new ArrayList<>();
        while (!check(Symbol.TokenType.RBRACE) && !isEOF()) {
            fields.add(parseVarDeclaration());
            expect(Symbol.TokenType.SEMICOLON, "Expected ';' after field declaration");
        }

        expect(Symbol.TokenType.RBRACE, "Expected '}' to close collection");

        return new CollectionNode(name, fields);
    }

    private FunctionNode parseFunction() {
        expectKeyword(Symbol.TokenType.KW_DEF, "def", "Expected 'def'");

        String returnType = "void";
        String funcName;

        if (check(Symbol.TokenType.IDENTIFIER) && checkNext(Symbol.TokenType.LPAREN)) {
            funcName = lexemeOf(current);
            expect(Symbol.TokenType.IDENTIFIER, "Expected function name");
        } else {
            returnType = parseType();

            funcName = lexemeOf(current);
            expect(Symbol.TokenType.IDENTIFIER, "Expected function name after return type");
        }

        expect(Symbol.TokenType.LPAREN, "Expected '(' after function name");

        List<VarDeclarationNode> parameters = new ArrayList<>();
        while (!check(Symbol.TokenType.RPAREN) && !isEOF()) {
            parameters.add(parseParameter());
            if (!check(Symbol.TokenType.RPAREN)) {
                expect(Symbol.TokenType.COMMA, "Expected ',' between parameters");
            }
        }

        expect(Symbol.TokenType.RPAREN, "Expected ')' after parameters");

        List<StatementNode> body = parseBlockStatements();

        return new FunctionNode(funcName, returnType, parameters, body);
    }

    private ExpressionNode parseArrayLiteral(String type) {
        expectKeyword(Symbol.TokenType.KW_ARRAY, "ARRAY", "Expected 'ARRAY'");

        expect(Symbol.TokenType.LBRACKET, "Expected '['");

        ExpressionNode size = parseExpression();

        expect(Symbol.TokenType.RBRACKET, "Expected ']'");

        return new ArrayLiteralNode(type, size);
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

    private boolean startsCollection() {
        return isKeyword(current, Symbol.TokenType.KW_COLL, "coll");
    }

    private boolean startsFunction() {
        return isKeyword(current, Symbol.TokenType.KW_DEF, "def");
    }

    private VarDeclarationNode parseParameter() {
        String type = parseType();

        String identifier = lexemeOf(current);
        expect(Symbol.TokenType.IDENTIFIER, "Expected parameter name");

        return new VarDeclarationNode(false, type, identifier, null);
    }
}
