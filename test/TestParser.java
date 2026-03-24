import compiler.Lexer.Lexer;
import compiler.Parser.AST;
import compiler.Parser.Parser;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.assertNotNull;

public class TestParser {

    @Test
    public void parsesArrayTypeDeclarationsAndArrayAccess() {
        String input = """
                coll Point {
                  INT x;
                  INT y;
                }
                INT[] c = INT ARRAY[5];
                def Point copyPoints(Point[] p) {
                  return Point(p[0].x + p[1].x, p[0].y + p[1].y);
                }
                """;

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AST ast = parser.getAST();

        assertNotNull(ast);
        assertNotNull(ast.getProgram());
    }

    @Test
    public void parsesVoidStyleFunctionWithoutExplicitReturnType() {
        String input = """
                def main() {
                  println(1);
                }
                """;

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AST ast = parser.getAST();

        assertNotNull(ast);
        assertNotNull(ast.getProgram());
    }

    @Test
    public void parsesForLoop() {
        String input = """
                def main() {
                  INT i;
                  for (i; 1 -> 10; i + 1) {
                    println(i);
                  }
                }
                """;

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AST ast = parser.getAST();

        assertNotNull(ast);
        assertNotNull(ast.getProgram());
    }
}