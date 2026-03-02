package compiler.Lexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

public class Lexer {

    private final PushbackReader reader;
    private int currentChar;

    public Lexer(Reader input) {
        this.reader = new PushbackReader(new BufferedReader(input), 3);
        advance();
    }

    public Symbol getNextSymbol() {
        skipWhitespaceAndComments();

        if (currentChar == -1) {
            return new Symbol(Symbol.TokenType.EOF, "EOF");
        }

        if (isLetter(currentChar) || currentChar == '_' || Character.isDigit(currentChar) || currentChar == '"'
                || (currentChar == '.' && Character.isDigit(peek()))) {
            return readAlphaNumericOrLiteral();
        }

        return readSymbolOrOperator();
    }

    private void skipWhitespaceAndComments() {
        while (true) {
            while (currentChar != -1 && Character.isWhitespace(currentChar)) {
                advance();
            }

            if (currentChar == '#') {
                while (currentChar != -1 && currentChar != '\n' && currentChar != '\r') {
                    advance();
                }
                continue;
            }

            break;
        }
    }

    private Symbol readSymbolOrOperator() {
        switch (currentChar) {
            case '=':
                if (peek() == '=') {
                    advance();
                    advance();
                    return new Symbol(Symbol.TokenType.EQ, "==");
                } else if (peek() == '/') {
                    advance();
                    advance();
                    if (currentChar == '=') {
                        advance();
                        return new Symbol(Symbol.TokenType.NEQ, "=/=");
                    }
                    throw new LexerException("Invalid operator, expected '=/='");
                } else {
                    advance();
                    return new Symbol(Symbol.TokenType.ASSIGN, "=");
                }

            case '<':
                if (peek() == '=') {
                    advance();
                    advance();
                    return new Symbol(Symbol.TokenType.LE, "<=");
                } else {
                    advance();
                    return new Symbol(Symbol.TokenType.LT, "<");
                }

            case '>':
                if (peek() == '=') {
                    advance();
                    advance();
                    return new Symbol(Symbol.TokenType.GE, ">=");
                } else {
                    advance();
                    return new Symbol(Symbol.TokenType.GT, ">");
                }

            case '&':
                if (peek() == '&') {
                    advance();
                    advance();
                    return new Symbol(Symbol.TokenType.AND, "&&");
                }
                throw new LexerException("Invalid symbol '&'");

            case '|':
                if (peek() == '|') {
                    advance();
                    advance();
                    return new Symbol(Symbol.TokenType.OR, "||");
                }
                throw new LexerException("Invalid symbol '|'");

            case '+':
                advance();
                return new Symbol(Symbol.TokenType.PLUS, "+");
            case '-':
                advance();
                return new Symbol(Symbol.TokenType.MINUS, "-");
            case '*':
                advance();
                return new Symbol(Symbol.TokenType.STAR, "*");
            case '/':
                advance();
                return new Symbol(Symbol.TokenType.SLASH, "/");
            case '%':
                advance();
                return new Symbol(Symbol.TokenType.MOD, "%");

            case '(':
                advance();
                return new Symbol(Symbol.TokenType.LPAREN, "(");
            case ')':
                advance();
                return new Symbol(Symbol.TokenType.RPAREN, ")");
            case '{':
                advance();
                return new Symbol(Symbol.TokenType.LBRACE, "{");
            case '}':
                advance();
                return new Symbol(Symbol.TokenType.RBRACE, "}");
            case '[':
                advance();
                return new Symbol(Symbol.TokenType.LBRACKET, "[");
            case ']':
                advance();
                return new Symbol(Symbol.TokenType.RBRACKET, "]");

            case '.':
                advance();
                return new Symbol(Symbol.TokenType.DOT, ".");
            case ';':
                advance();
                return new Symbol(Symbol.TokenType.SEMICOLON, ";");
            case ',':
                advance();
                return new Symbol(Symbol.TokenType.COMMA, ",");

            default:
                throw new LexerException("Unrecognized symbol: '" + (char) currentChar + "'");
        }
    }

    private Symbol readAlphaNumericOrLiteral() {
        if (currentChar == '"') {
            return readStringLiteral();
        }

        if (Character.isDigit(currentChar) ||
            (currentChar == '.' && Character.isDigit(peek()))) {
            return readNumberLiteral();
        }

        if (isLetter(currentChar) || currentChar == '_') {
            return readIdentifierOrKeyword();
        }

        throw new LexerException("Unexpected character: " + (char) currentChar);
    }

    private Symbol readStringLiteral() {
        StringBuilder sb = new StringBuilder();
        advance();

        while (currentChar != -1 && currentChar != '"') {

            if (currentChar == '\\') {
                advance();
                switch (currentChar) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default:
                        throw new LexerException("Invalid escape sequence: \\" + (char) currentChar);
                }
            } else {
                sb.append((char) currentChar);
            }

            advance();
        }

        if (currentChar != '"') {
            throw new LexerException("Unterminated string literal");
        }

        advance();

        return new Symbol(Symbol.TokenType.STRING, sb.toString());
    }

    private Symbol readNumberLiteral() {
        StringBuilder sb = new StringBuilder();
        boolean isFloat = false;

        if (currentChar == '.') {
            isFloat = true;
            sb.append('.');
            advance();

            if (!Character.isDigit(currentChar)) {
                throw new LexerException("Invalid float literal");
            }
        }

        while (Character.isDigit(currentChar)) {
            sb.append((char) currentChar);
            advance();
        }

        if (currentChar == '.') {
            isFloat = true;
            sb.append('.');
            advance();

            if (!Character.isDigit(currentChar)) {
                throw new LexerException("Invalid float literal");
            }

            while (Character.isDigit(currentChar)) {
                sb.append((char) currentChar);
                advance();
            }
        }

        if (isFloat) {
            return new Symbol(Symbol.TokenType.FLOAT, sb.toString());
        } else {
            return new Symbol(Symbol.TokenType.INT, sb.toString());
        }
    }

    private Symbol readIdentifierOrKeyword() {
        StringBuilder sb = new StringBuilder();

        while (isLetter(currentChar) ||
            Character.isDigit(currentChar) ||
            currentChar == '_') {
            sb.append((char) currentChar);
            advance();
        }

        String word = sb.toString();

        if (word.equals("true") || word.equals("false")) {
            return new Symbol(Symbol.TokenType.BOOL, word);
        }

        switch (word) {
            case "def":
                return new Symbol(Symbol.TokenType.KW_DEF, word);
            case "final":
                return new Symbol(Symbol.TokenType.KW_FINAL, word);
            case "ARRAY":
                return new Symbol(Symbol.TokenType.KW_ARRAY, word);
        }

        return new Symbol(Symbol.TokenType.IDENTIFIER, word);
    }

    private void advance() {
        try {
            currentChar = reader.read();
        } catch (IOException e) {
            throw new LexerException("I/O error while reading input", e);
        }
    }

    private int peek() {
        try {
            int next = reader.read();
            if (next != -1) {
                reader.unread(next);
            }
            return next;
        } catch (IOException e) {
            throw new LexerException("I/O error while peeking input", e);
        }
    }

    private boolean isLetter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean isLowerCaseLetter(int c) {
        return (c >= 'a' && c <= 'z');
    }

    public static class LexerException extends RuntimeException {
        public LexerException(String message) {
            super(message);
        }

        public LexerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
