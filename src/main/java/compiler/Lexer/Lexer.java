package compiler.Lexer;

import java.io.IOException;
import java.io.Reader;

public class Lexer {

    private final Reader reader;
    private int currentChar;

    public Lexer(Reader input) {
        this.reader = input;
        advance();
    }

    private void advance() {
        try {
            currentChar = reader.read();
        } catch (IOException e) {
            throw new RuntimeException("Error reading input");
        }
    }

    public Symbol getNextSymbol() {
        while (Character.isWhitespace(currentChar)) {
            advance();
        }

        if (currentChar == -1) {
            return new Symbol("EOF", null);
        }

        // Handle numbers (integers and floats)
        if (Character.isDigit(currentChar) || currentChar == '.') {
            return readNumber();
        }

        throw new RuntimeException("Unrecognized character: " + (char) currentChar);
    }

    private Symbol readNumber() {
        StringBuilder sb = new StringBuilder();
        boolean isFloat = false;

        // Case 1: starts with a dot (e.g., .5)
        if (currentChar == '.') {
            isFloat = true;
            sb.append("0.");
            advance();

            if (!Character.isDigit(currentChar)) {
                throw new RuntimeException("Invalid float format");
            }
        }

        while (Character.isDigit(currentChar)) {
            sb.append((char) currentChar);
            advance();
        }

        // Case 2: decimal part (12.34)
        if (currentChar == '.') {
            isFloat = true;
            sb.append('.');
            advance();

            if (!Character.isDigit(currentChar)) {
                throw new RuntimeException("Invalid float format");
            }

            while (Character.isDigit(currentChar)) {
                sb.append((char) currentChar);
                advance();
            }
        }

        String number = sb.toString();

        if (isFloat) {
            return new Symbol("FLOAT", number);
        } else {
            // Remove leading zeros safely using Integer parsing
            int value = Integer.parseInt(number);
            return new Symbol("INT", String.valueOf(value));
        }
    }
}