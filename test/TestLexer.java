import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestLexer {

    @Test
    public void test() {
        String input = "var x int = 2;";
        StringReader reader = new StringReader(input);
        Lexer lexer = new Lexer(reader);
        assertNotNull(lexer.getNextSymbol());
    }

    @Test
    public void allowsSpacesInsideNotEqualOperator() {
        StringReader reader = new StringReader("value = /= 3;");
        Lexer lexer = new Lexer(reader);

        assertEquals(Symbol.TokenType.IDENTIFIER, lexer.getNextSymbol().getType());
        assertEquals(Symbol.TokenType.NEQ, lexer.getNextSymbol().getType());
        assertEquals(Symbol.TokenType.INT, lexer.getNextSymbol().getType());
        assertEquals(Symbol.TokenType.SEMICOLON, lexer.getNextSymbol().getType());
    }
}
