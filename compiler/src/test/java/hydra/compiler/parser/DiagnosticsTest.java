package hydra.compiler.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F2-03 — diagnósticos: códigos, sugestões e casos de borda.
 */
class DiagnosticsTest {

    private static SyntaxError parseErr(String src) {
        return assertThrows(SyntaxError.class, () -> hydra.compiler.parser.Parser.parse(src));
    }

    @Test
    void missingParamColonSuggestsNameColonType() {
        SyntaxError e = parseErr("dobro(x Int): Int {\n    return x * 2\n}\n");
        assertEquals("HYP008", e.code());
        assertNotNull(e.suggestion());
        assertTrue(e.suggestion().contains("x: Int"), e.suggestion());
        assertTrue(e.getMessage().contains("HYP008") || e.getMessage().contains("':'"), e.getMessage());
    }

    @Test
    void funKeywordRejectedWithHint() {
        SyntaxError e = parseErr("fun main() {\n}\n");
        assertEquals("HYP012", e.code());
        assertTrue(e.suggestion().contains("sem palavra-chave"), e.suggestion());
    }

    @Test
    void classKeywordRejectedWithTypeHint() {
        SyntaxError e = parseErr("class User {\n}\n");
        assertEquals("HYP013", e.code());
        assertTrue(e.suggestion().contains("type"), e.suggestion());
    }

    @Test
    void semicolonRejectedByLexerWithHint() {
        SyntaxError e = parseErr("main() {\n    println(\"x\");\n}\n");
        assertEquals("HYP010", e.code());
        assertTrue(e.getMessage().contains("';'") || e.getMessage().contains("newline"), e.getMessage());
        assertTrue(e.suggestion().contains("newline"), e.suggestion());
    }

    @Test
    void catchWithTypeRejected() {
        SyntaxError e = parseErr("""
                main() {
                    try {
                        println("x")
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        assertEquals("HYP011", e.code());
        assertTrue(e.suggestion().contains("catch (e)"), e.suggestion());
    }

    @Test
    void unclosedBraceAtEof() {
        SyntaxError e = parseErr("main() {\n    println(\"x\")\n");
        assertEquals("HYP001", e.code());
        assertNotNull(e.suggestion());
        assertTrue(e.suggestion().contains("chaves") || e.getMessage().contains("EOF")
                        || e.getMessage().contains("fim do arquivo"),
                e.getMessage());
    }

    @Test
    void forInMissingInHasHint() {
        SyntaxError e = parseErr("main() {\n    for (val x xs) {\n        println(x)\n    }\n}\n");
        assertEquals("HYP004", e.code());
        assertTrue(e.suggestion().contains("in"), e.suggestion());
    }

    @Test
    void enumEmptyHasCasesHint() {
        SyntaxError e = parseErr("enum Color { }\n");
        assertEquals("HYP003", e.code());
        assertTrue(e.suggestion().contains("Red"), e.suggestion());
    }

    @Test
    void invalidCasePatternHasHint() {
        SyntaxError e = parseErr("""
                main() {
                    match (x) {
                        case -> 1
                    }
                }
                """);
        assertEquals("HYP005", e.code());
        assertTrue(e.suggestion().contains("Color.Red") || e.suggestion().contains("String s"), e.suggestion());
    }

    @Test
    void unterminatedStringLexerError() {
        SyntaxError e = parseErr("main() {\n    println(\"abc\n}\n");
        assertEquals("HYP010", e.code());
        assertTrue(e.getMessage().contains("string"), e.getMessage());
        assertNotNull(e.suggestion());
    }

    @Test
    void errorCarriesPosition() {
        SyntaxError e = parseErr("main( {\n}\n");
        assertTrue(e.line() >= 1);
        assertTrue(e.col() >= 1);
        assertEquals("HYP001", e.code());
    }

    @Test
    void errorMessageIncludesPositionAndHint() {
        SyntaxError e = parseErr("dobro(x Int): Int {\n    return x\n}\n");
        String msg = e.getMessage();
        assertTrue(msg.contains("(" + e.line() + ":" + e.col() + ")"), msg);
        assertTrue(msg.contains("hint:"), msg);
    }

    @Test
    void validCodeStillParses() {
        assertDoesNotThrow(() -> hydra.compiler.parser.Parser.parse("""
                main() {
                    println("ok")
                }
                """));
    }
}
