import compiler.Lexer.Lexer;
import compiler.Parser.AST;
import compiler.Parser.Parser;
import compiler.Parser.SemanticAnalyzer;
import compiler.Parser.SemanticException;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertTrue;

public class TestSemanticCore {

    private void assertSemanticErrorContains(String source, String expectedKeyword) {
        try {
            Parser parser = new Parser(new Lexer(new StringReader(source)));
            AST ast = parser.getAST();
            new SemanticAnalyzer().analyze(ast);
        } catch (SemanticException e) {
            assertTrue(e.getMessage().contains(expectedKeyword));
            return;
        }
        throw new AssertionError("Expected semantic error containing: " + expectedKeyword);
    }

    @Test
    public void throwsCollectionErrorForLowercaseCollectionName() {
        String input = """
                coll point {
                  INT x;
                }
                """;

        assertSemanticErrorContains(input, "CollectionError");
    }

    @Test
    public void throwsScopeErrorForUndefinedVariable() {
        String input = """
                def main() {
                  INT x = y;
                }
                """;

        assertSemanticErrorContains(input, "ScopeError");
    }

    @Test
    public void throwsReturnErrorForWrongReturnType() {
        String input = """
                def INT main() {
                  return true;
                }
                """;

        assertSemanticErrorContains(input, "ReturnError");
    }

    @Test
    public void passesBasicSemanticCheck() {
        String input = """
                coll Point {
                  INT x;
                  INT y;
                }
                INT g = 1;
                def INT add(INT a, INT b) {
                  INT c = a + b;
                  return c;
                }
                def main() {
                  INT x = add(1, 2);
                }
                """;

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AST ast = parser.getAST();
        new SemanticAnalyzer().analyze(ast);
    }
}
