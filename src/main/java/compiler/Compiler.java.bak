package compiler;

import compiler.Lexer.Lexer;
import compiler.Lexer.Symbol;

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

        System.out.println("Usage: ./gradlew run --args='-lexer path/to/file'");
    }
}
