package compiler.Lexer;

public class Symbol {

    private final String tokenType;
    private final String value;

    public Symbol(String type) {
        this(type, null);
    }

    public Symbol(String tokenType, String value) {
        this.tokenType = tokenType;
        this.value = value;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getValue() {
        return value;
    }

    public String toString() {
        if (this.value == null) {
            return "<" + this.tokenType + ">";
        }
        return "<" + this.tokenType + ", " + this.value + ">";
    }
}