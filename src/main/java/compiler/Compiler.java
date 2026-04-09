package compiler;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import compiler.Parser.AST;
import compiler.Parser.Parser;
import compiler.Parser.SemanticAnalyzer;

import java.io.FileReader;
import java.io.Reader;

public class Compiler {
    public static void main(String[] args) {
        if (args.length == 2 && "-lexer".equals(args[0])) {
            try (Reader reader = new FileReader(args[1])) {
                Lexer lexer = new Lexer(reader);
                while (true) {
                    Symbol s = lexer.getNextSymbol();
                    System.out.println(s);
                    if (s.getType() == Symbol.TokenType.EOF) {
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Lexer error: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        if (args.length == 2 && "-parser".equals(args[0])) {
            try (Reader reader = new FileReader(args[1])) {
                Lexer lexer = new Lexer(reader);
                Parser parser = new Parser(lexer);
                AST ast = parser.getAST();
                ast.print();
            } catch (Exception e) {
                System.err.println("Parser error: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        if (args.length == 2 && "-semantic".equals(args[0])) {
            try (Reader reader = new FileReader(args[1])) {
                Lexer lexer = new Lexer(reader);
                Parser parser = new Parser(lexer);
                AST ast = parser.getAST();
                SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
                semanticAnalyzer.analyze(ast);
                System.out.println("Semantic analysis OK");
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
            return;
        }

        System.out.println("Usage: ./gradlew run --args='-lexer path/to/file'");
        System.out.println("   or: ./gradlew run --args='-parser path/to/file'");
        System.out.println("   or: ./gradlew run --args='-semantic path/to/file'");
    }
}
