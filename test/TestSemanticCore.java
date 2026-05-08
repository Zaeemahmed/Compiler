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
    public void throwsTypeErrorForInvalidAssignment() {
        String input = """
                def main() {
                  INT x = true;
                }
                """;

        assertSemanticErrorContains(input, "TypeError");
    }

    @Test
    public void throwsOperatorErrorForInvalidExpression() {
        String input = """
                def main() {
                  INT x = 1 + true;
                }
                """;

        assertSemanticErrorContains(input, "OperatorError");
    }

    @Test
    public void throwsArgumentErrorForFunctionCallMismatch() {
        String input = """
                def INT id(INT a) {
                  return a;
                }
                def main() {
                  INT x = id(true);
                }
                """;

        assertSemanticErrorContains(input, "ArgumentError");
    }

    @Test
    public void throwsMissingConditionErrorForIfCondition() {
        String input = """
                def main() {
                  if (1) {
                    INT x = 1;
                  }
                }
                """;

        assertSemanticErrorContains(input, "MissingConditionError");
    }

    @Test
    public void throwsCollectionErrorForDuplicateField() {
        String input = """
                coll Point {
                  INT x;
                  INT x;
                }
                """;

        assertSemanticErrorContains(input, "CollectionError");
    }

    @Test
    public void throwsCollectionErrorForUnknownFieldType() {
        String input = """
                coll Point {
                  UNKNOWN x;
                }
                """;

        assertSemanticErrorContains(input, "CollectionError");
    }

    @Test
    public void throwsTypeErrorForVariableDeclarationMismatch() {
        String input = """
                def main() {
                  INT x = "hello";
                }
                """;

        assertSemanticErrorContains(input, "TypeError");
    }

    @Test
    public void throwsTypeErrorForVariableAssignmentMismatch() {
        String input = """
                def main() {
                  INT x = 5;
                  x = "hello";
                }
                """;

        assertSemanticErrorContains(input, "TypeError");
    }

    @Test
    public void throwsOperatorErrorForArithmeticOperatorMismatch() {
        String input = """
                def main() {
                  INT x = 1;
                  FLOAT y = 2.5;
                  INT z = x + y;
                }
                """;

        assertSemanticErrorContains(input, "OperatorError");
    }

    @Test
    public void throwsOperatorErrorForLogicalOperatorOnIntegers() {
        String input = """
                def main() {
                  INT x = 1 && 2;
                }
                """;

        assertSemanticErrorContains(input, "OperatorError");
    }

    @Test
    public void throwsOperatorErrorForComparisonTypeMismatch() {
        String input = """
                def main() {
                  BOOLEAN b = 5 == true;
                }
                """;

        assertSemanticErrorContains(input, "OperatorError");
    }

    @Test
    public void throwsOperatorErrorForNotOperatorOnInteger() {
        String input = """
                def main() {
                  BOOLEAN b = not 1;
                }
                """;

        assertSemanticErrorContains(input, "OperatorError");
    }

    @Test
    public void throwsArgumentErrorForWrongNumberOfArguments() {
        String input = """
                def INT add(INT a, INT b) {
                  return a + b;
                }
                def main() {
                  INT x = add(1);
                }
                """;

        assertSemanticErrorContains(input, "ArgumentError");
    }

    @Test
    public void throwsArgumentErrorForCollectionConstructorTypeMismatch() {
        String input = """
                coll Point {
                  INT x;
                  INT y;
                }
                def main() {
                  Point p = Point(true, 2);
                }
                """;

        assertSemanticErrorContains(input, "ArgumentError");
    }

    @Test
    public void throwsMissingConditionErrorForWhileNonBoolean() {
        String input = """
                def main() {
                  INT i = 0;
                  while (i) {
                    i = i + 1;
                  }
                }
                """;

        assertSemanticErrorContains(input, "MissingConditionError");
    }

    @Test
    public void throwsReturnErrorForMissingReturnValue() {
        String input = """
                def INT getValue() {
                  return;
                }
                """;

        assertSemanticErrorContains(input, "ReturnError");
    }

    @Test
    public void throwsReturnErrorForVoidFunctionReturnValue() {
        String input = """
                def printValue() {
                  return 5;
                }
                """;

        assertSemanticErrorContains(input, "ReturnError");
    }

    @Test
    public void throwsScopeErrorForDuplicateVariableDefinition() {
        String input = """
                def main() {
                  INT x = 1;
                  INT x = 2;
                }
                """;

        assertSemanticErrorContains(input, "ScopeError");
    }

    @Test
    public void throwsScopeErrorForDuplicateFunctionDefinition() {
        String input = """
                def main() {
                }
                def main() {
                }
                """;

        assertSemanticErrorContains(input, "ScopeError");
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

    @Test
    public void passesArrayCreationSemanticCheck() {
        String input = """
                def main() {
                  INT[] c = INT ARRAY [5];
                  INT x = c[0];
                }
                """;

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AST ast = parser.getAST();
        new SemanticAnalyzer().analyze(ast);
    }

    @Test
    public void passesCollectionArrayFieldAccessSemanticCheck() {
        String input = """
                coll Point {
                  INT x;
                  INT y;
                }
                def main() {
                  Point[] p = Point ARRAY [2];
                  Point point = p[0];
                  INT x = point.x;
                }
                """;

        Parser parser = new Parser(new Lexer(new StringReader(input)));
        AST ast = parser.getAST();
        new SemanticAnalyzer().analyze(ast);
    }
}
