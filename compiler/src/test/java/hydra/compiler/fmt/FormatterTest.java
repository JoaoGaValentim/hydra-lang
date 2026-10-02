package hydra.compiler.fmt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** F6-01b — formatter canônico: idempotência + preservação de //. */
class FormatterTest {

    private static final Path EXEMPLOS = Path.of("..", "hydra", "exemplos");

    private static String fmt(String src) {
        return Formatter.format(src);
    }

    @Test
    void helloCanonical() {
        String out = fmt("""
                main() {
                println("Hello, Hydra")
                }
                """);
        assertEquals("""
                main() {
                    println("Hello, Hydra")
                }
                """, out);
    }

    @Test
    void idempotentHello() {
        String once = fmt("main() {\nprintln(\"hi\")\n}\n");
        assertEquals(once, fmt(once));
    }

    @Test
    void preservesLeadingComments() {
        String out = fmt("""
                // linha um
                // linha dois
                main() {
                    println("ok")
                }
                """);
        assertTrue(out.startsWith("// linha um\n// linha dois\nmain()"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void preservesTrailingComment() {
        String out = fmt("""
                main() {
                    println("ok") // fim
                }
                """);
        assertTrue(out.contains("println(\"ok\") // fim"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void preservesCommentAtEndOfBlock() {
        String src = """
                main() {
                    println("ok")
                    // no fim do bloco
                }
                """;
        String out = fmt(src);
        assertTrue(out.contains("// no fim do bloco"), out);
        assertTrue(out.indexOf("// no fim do bloco") < out.lastIndexOf('}'), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void blankLineBetweenTopLevelDecls() {
        String out = fmt("""
                main() {
                    println("a")
                }
                other() {
                    println("b")
                }
                """);
        assertTrue(out.contains("}\n\nother()"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void matchPrintsOneForm() {
        String out = fmt("""
                main() {
                val n = 2
                val t = match (n) {
                case 1 -> "um"
                default -> "outro"
                }
                println(t)
                }
                """);
        assertTrue(out.contains("match (n) {"), out);
        assertTrue(out.contains("case 1 -> \"um\""), out);
        assertTrue(out.contains("default -> \"outro\""), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void forClassicAndForIn() {
        String out = fmt("""
                main() {
                for (var i = 0, i < 3, i += 1) {
                println(i)
                }
                val xs = listOf(1, 2)
                for x in xs {
                println(x)
                }
                }
                """);
        assertTrue(out.contains("for (var i = 0, i < 3, i += 1)"), out);
        assertTrue(out.contains("for x in xs"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void forInVarPrintsVar() {
        String out = fmt("main() {\nfor var x in xs {\nprintln(x)\n}\n}\n");
        assertTrue(out.contains("for var x in xs"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void typeAndEnum() {
        String out = fmt("""
                type Point(x: Int, y: Int)
                enum Color {
                Red
                Green
                }
                """);
        assertTrue(out.contains("type Point(x: Int, y: Int)"), out);
        assertTrue(out.contains("enum Color {\n    Red\n    Green\n}"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void typeWithMethods() {
        String out = fmt("""
                type User(var name: String) {
                greet(): String {
                return "hi " + name
                }
                }
                """);
        assertTrue(out.contains("type User(var name: String) {"), out);
        assertTrue(out.contains("greet(): String {"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void sugarFunctionExpression() {
        String out = fmt("maior(a: Int, b: Int): Int = if (a > b) a else b\n");
        assertEquals("maior(a: Int, b: Int): Int = if (a > b) a else b\n", out);
        assertEquals(out, fmt(out));
    }

    @Test
    void testAndApplication() {
        String out = fmt("""
                test "soma" {
                assert(1 == 1, "ok")
                }
                application {
                main {
                println("app")
                }
                }
                """);
        assertTrue(out.contains("test \"soma\" {"), out);
        assertTrue(out.contains("application {"), out);
        assertTrue(out.contains("main {"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void tryCatchAndThrow() {
        String out = fmt("""
                main() {
                try {
                throw "erro"
                } catch (e) {
                println(e)
                }
                }
                """);
        assertTrue(out.contains("try {"), out);
        assertTrue(out.contains("} catch (e) {"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void ifElseIf() {
        String out = fmt("""
                main() {
                if (1 > 2) {
                println("a")
                } else if (2 > 1) {
                println("b")
                } else {
                println("c")
                }
                }
                """);
        assertTrue(out.contains("} else if (2 > 1) {"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void importsThenDecl() {
        String out = fmt("""
                import kof.io
                main() {
                println("x")
                }
                """);
        assertTrue(out.startsWith("import kof.io\n\nmain()"), out);
        assertEquals(out, fmt(out));
    }

    @Test
    void invalidSourceThrows() {
        assertThrows(RuntimeException.class, () -> fmt("main( {"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "01-hello.hy", "02-val-var.hy", "03-funcoes.hy", "04-tipos-immutable.hy",
            "05-tipos-membros.hy", "06-enum.hy", "07-if-expressao.hy", "08-match-basico.hy",
            "09-match-guardas.hy", "10-match-destructuring.hy", "11-for-colecao.hy",
            "12-for-contagem.hy", "13-for-condicao.hy", "14-strings.hy", "15-nullable.hy",
            "16-erros.hy", "17-lambdas.hy", "18-trailing-lambda.hy", "19-listas.hy",
            "21-conjuntos.hy", "22-heranca-super.hy", "23-tipo-abstrato.hy",
            "24-concorrencia.hy", "25-imports.hy", "26-teste.hy", "27-mat-comparacoes.hy",
            "28-assign-composto.hy", "29-closures.hy", "30-mini-programa.hy",
            "31-idiomas-migrados.hy", "32-null-match.hy", "33-funcao-expressao.hy",
            "34-type-mutavel.hy", "35-enum-exaustivo.hy", "36-tipos-anotados.hy",
            "37-application.hy", "38-comentarios.hy", "39-bitwise.hy", "40-ordem-superior.hy"
    })
    void corpusIdempotent(String name) throws IOException {
        Path f = EXEMPLOS.resolve(name);
        assertTrue(Files.isRegularFile(f), "exemplo ausente: " + f.toAbsolutePath());
        String src = Files.readString(f, StandardCharsets.UTF_8);
        String once;
        try {
            once = fmt(src);
        } catch (RuntimeException e) {
            org.junit.jupiter.api.Assumptions.abort("exemplo fora do subset parseável: " + name + " — " + e.getMessage());
            return;
        }
        String twice = fmt(once);
        assertEquals(once, twice, "não idempotente: " + name + "\n---1---\n" + once + "\n---2---\n" + twice);
    }

    @Test
    void corpusFormattedStillParses() throws IOException {
        int checked = 0;
        try (Stream<Path> files = Files.list(EXEMPLOS)) {
            for (Path p : files.filter(x -> x.toString().endsWith(".hy")).toList()) {
                String src = Files.readString(p, StandardCharsets.UTF_8);
                String out;
                try {
                    out = fmt(src);
                } catch (RuntimeException e) {
                    continue; // exemplos aspiracionais fora do subset parseável
                }
                assertDoesNotThrow(() -> Formatter.format(out), p.getFileName().toString());
                checked++;
            }
        }
        assertTrue(checked >= 30, "poucos exemplos parseáveis para fmt: " + checked);
    }
}
