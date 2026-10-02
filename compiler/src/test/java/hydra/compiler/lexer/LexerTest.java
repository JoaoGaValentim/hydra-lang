package hydra.compiler.lexer;

import hydra.compiler.token.Token;
import hydra.compiler.token.TokenKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexerTest {

    private static List<TokenKind> kinds(String src) {
        return new Lexer(src).tokenize().stream().map(Token::kind).collect(Collectors.toList());
    }

    @Test
    void helloWorld() {
        List<Token> toks = new Lexer("main() {\n    println(\"Hello, Hydra\")\n}").tokenize();
        assertEquals(TokenKind.IDENT, toks.get(0).kind());
        assertEquals("main", toks.get(0).text());
        assertEquals(TokenKind.LPAREN, toks.get(1).kind());
        assertEquals(TokenKind.RPAREN, toks.get(2).kind());
        assertEquals(TokenKind.LBRACE, toks.get(3).kind());
        assertEquals(TokenKind.IDENT, toks.get(4).kind());
        assertEquals("println", toks.get(4).text());
        assertEquals(TokenKind.LPAREN, toks.get(5).kind());
        assertEquals(TokenKind.STRING, toks.get(6).kind());
        assertEquals("Hello, Hydra", toks.get(6).text());
        assertEquals(TokenKind.RPAREN, toks.get(7).kind());
        assertEquals(TokenKind.RBRACE, toks.get(8).kind());
        assertEquals(TokenKind.EOF, toks.get(toks.size() - 1).kind());
    }

    @Test
    void allTwentyKeywordsRecognized() {
        String src = "type enum import val var if else for match return throw try catch spawn await true false null extends super";
        List<TokenKind> ks = kinds(src);
        // remove EOF
        ks.remove(ks.size() - 1);
        assertEquals(20, ks.size());
        for (TokenKind k : ks) {
            assertTrue(k.isKeyword(), "esperava keyword: " + k);
        }
    }

    @Test
    void deadWordsAreIdentifiers() {
        // fun/fn/func/class/record/while/switch/new/package/this/as não são keywords
        for (String w : List.of("fun", "fn", "func", "class", "record", "while", "switch", "new", "package", "this", "as", "finally")) {
            List<Token> toks = new Lexer(w).tokenize();
            assertEquals(TokenKind.IDENT, toks.get(0).kind(), w + " não deve ser keyword");
        }
    }

    @Test
    void contextualWordsAreIdentifiers() {
        for (String w : List.of("test", "application", "assert", "println", "listOf")) {
            List<Token> toks = new Lexer(w).tokenize();
            assertEquals(TokenKind.IDENT, toks.get(0).kind(), w);
        }
    }

    @Test
    void commentsIgnored() {
        List<TokenKind> ks = kinds("// linha\nval x = 1 // fim\n");
        assertEquals(List.of(TokenKind.VAL, TokenKind.IDENT, TokenKind.EQ, TokenKind.INT, TokenKind.EOF), ks);
    }

    @Test
    void stringsAndEscapes() {
        List<Token> toks = new Lexer("\"a\\nb\"").tokenize();
        assertEquals(TokenKind.STRING, toks.get(0).kind());
        assertEquals("a\nb", toks.get(0).text());
    }

    @Test
    void numbers() {
        assertEquals(List.of(TokenKind.INT, TokenKind.EOF), kinds("42"));
        assertEquals(List.of(TokenKind.FLOAT, TokenKind.EOF), kinds("3.14"));
    }

    @Test
    void compoundAssignmentAndArrow() {
        List<TokenKind> ks = kinds("x += 1 -> 2");
        assertEquals(List.of(TokenKind.IDENT, TokenKind.PLUS_EQ, TokenKind.INT, TokenKind.ARROW, TokenKind.INT, TokenKind.EOF), ks);
    }

    @Test
    void nullableTypeQuestion() {
        List<TokenKind> ks = kinds("String?");
        assertEquals(List.of(TokenKind.IDENT, TokenKind.QUESTION, TokenKind.EOF), ks);
    }

    @Test
    void unterminatedStringIsError() {
        List<Token> toks = new Lexer("\"abc").tokenize();
        assertEquals(TokenKind.ERROR, toks.get(0).kind());
        assertTrue(toks.get(0).text().contains("não fechada"));
    }

    @Test
    void positionsAreOneBased() {
        List<Token> toks = new Lexer("\n  val").tokenize();
        assertEquals(TokenKind.VAL, toks.get(0).kind());
        assertEquals(2, toks.get(0).line());
        assertEquals(3, toks.get(0).col());
    }

    @Test
    void tokenizesExampleHelloFile() throws Exception {
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("../hydra/exemplos/01-hello.hy"));
        List<Token> toks = new Lexer(src).tokenize();
        assertTrue(toks.size() > 5);
        assertEquals(TokenKind.EOF, toks.get(toks.size() - 1).kind());
        assertTrue(toks.stream().anyMatch(t -> t.kind() == TokenKind.STRING));
    }
}
