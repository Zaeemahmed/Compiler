package compiler;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;
import compiler.Parser.AST;
import compiler.Parser.CodeGenerator;
import compiler.Parser.Parser;
import compiler.Parser.SemanticAnalyzer;

import java.io.FileReader;
import java.io.Reader;

public class Compiler {
    public static void main(String[] args) {
        if (args.length == 2 && "-lexer".equals(args[0])) {
            runLexer(args[1]);
            return;
        }

        if (args.length == 2 && "-parser".equals(args[0])) {
            runParser(args[1]);
            return;
        }

        if (args.length == 2 && "-semantic".equals(args[0])) {
            runSemantic(args[1]);
            return;
        }

        CompilationArguments compilationArguments = parseCompilationArguments(args);
        if (compilationArguments != null) {
            runFullCompilation(compilationArguments.sourceFile, compilationArguments.targetFile);
            return;
        }

        printUsage();
        System.exit(1);
    }

    private static void runLexer(String sourceFile) {
        try (Reader reader = new FileReader(sourceFile)) {
            Lexer lexer = new Lexer(reader);
            while (true) {
                Symbol symbol = lexer.getNextSymbol();
                System.out.println(symbol);
                if (symbol.getType() == Symbol.TokenType.EOF) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Lexer error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runParser(String sourceFile) {
        try (Reader reader = new FileReader(sourceFile)) {
            Lexer lexer = new Lexer(reader);
            Parser parser = new Parser(lexer);
            AST ast = parser.getAST();
            ast.print();
        } catch (Exception e) {
            System.err.println("Parser error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runSemantic(String sourceFile) {
        try (Reader reader = new FileReader(sourceFile)) {
            AST ast = parse(sourceFile, reader);
            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
            semanticAnalyzer.analyze(ast);
            System.out.println("Semantic analysis OK");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    private static void runFullCompilation(String sourceFile, String targetFile) {
        try (Reader reader = new FileReader(sourceFile)) {
            AST ast = parse(sourceFile, reader);
            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
            semanticAnalyzer.analyze(ast);
            CodeGenerator codeGenerator = new CodeGenerator();
            codeGenerator.generate(ast, targetFile);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    private static AST parse(String sourceFile, Reader reader) {
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        return parser.getAST();
    }

    private static CompilationArguments parseCompilationArguments(String[] args) {
        if (args.length == 1) {
            return new CompilationArguments(args[0], null);
        }
        if (args.length == 3 && "-o".equals(args[1])) {
            return new CompilationArguments(args[0], args[2]);
        }
        return null;
    }

    private static void printUsage() {
        System.out.println("Usage: ./gradlew run --args='-lexer path/to/file'");
        System.out.println("   or: ./gradlew run --args='-parser path/to/file'");
        System.out.println("   or: ./gradlew run --args='-semantic path/to/file'");
        System.out.println("   or: ./gradlew run --args='source_file -o target_file'");
        System.out.println("   or: ./gradlew run --args='source_file' (generates Main.class)");
    }

    private static final class CompilationArguments {
        final String sourceFile;
        final String targetFile;

        CompilationArguments(String sourceFile, String targetFile) {
            this.sourceFile = sourceFile;
            this.targetFile = targetFile;
        }
    }
}
