package hydra.compiler.backend;

import hydra.compiler.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F3-03 — backend JS: fonte Hydra → main.js → execução em Node.
 * Sem Node no ambiente, o teste é skipped (honesto, não falso verde).
 */
class JsBackendE2ETest {

    @TempDir
    Path out;

    static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String runJs(String source) throws Exception {
        Compiler.compileToJs(source, out);
        Path js = out.resolve("main.js");
        assertTrue(Files.exists(js), "main.js deve existir");
        ProcessBuilder pb = new ProcessBuilder("node", js.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(10, TimeUnit.SECONDS);
        assertTrue(finished, "node demorou");
        assertEquals(0, p.exitValue(), "node saiu com erro:\n" + stdout);
        return stdout;
    }

    @Test
    @EnabledIf("nodeAvailable")
    void helloWorldRunsInNode() throws Exception {
        String outText = runJs("""
                main() {
                    println("Hello, Hydra")
                }
                """);
        assertEquals("Hello, Hydra%n".formatted(), outText);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void valVarAndArithmeticRunsInNode() throws Exception {
        String outText = runJs("""
                main() {
                    val x = 10
                    var y = x + 5
                    y = y * 2
                    println(y)
                }
                """);
        assertEquals("30%n".formatted(), outText);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void functionCallRunsInNode() throws Exception {
        String outText = runJs("""
                dobro(x: Int): Int {
                    return x * 2
                }

                main() {
                    println(dobro(21))
                }
                """);
        assertEquals("42%n".formatted(), outText);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void classicForRunsInNode() throws Exception {
        String outText = runJs("""
                main() {
                    var total = 0
                    for (var i = 0, i < 3, i = i + 1) {
                        total = total + i
                    }
                    println(total)
                }
                """);
        assertEquals("3%n".formatted(), outText);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void stringConcatRunsInNode() throws Exception {
        String outText = runJs("""
                saudar(nome: String): String {
                    return "Hello, " + nome
                }

                main() {
                    println(saudar("Hydra"))
                }
                """);
        assertEquals("Hello, Hydra%n".formatted(), outText);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void assertThrowCatchParityInNode() throws Exception {
        String outText = runJs("""
                main() {
                    try {
                        assert(1 == 2, "quebrou")
                    } catch (e) {
                        println("erro: " + e)
                    }
                    try {
                        throw "detalhe"
                    } catch (e) {
                        println("2:" + e)
                    }
                    println("fim")
                }
                """);
        assertEquals("erro: quebrou%n2:detalhe%nfim%n".formatted(), outText);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void boolAndFloatPrintLikeJvmInNode() throws Exception {
        String outText = runJs("""
                main() {
                    println(true)
                    println(false)
                    println(1.0)
                    println("n=" + 42)
                }
                """);
        assertEquals("true%nfalse%n1.0%nn=42%n".formatted(), outText);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void matchAndEnumParityInNode() throws Exception {
        String outText = runJs("""
                enum Color {
                    Red
                    Green
                    Blue
                }

                nome(c: Color): String {
                    match (c) {
                        case Color.Red -> "red"
                        case Color.Green -> "green"
                        default -> "blue"
                    }
                }

                classificar(n: Int): String {
                    match (n) {
                        case Int i if i < 0 -> "negativo"
                        case Int i if i == 0 -> "zero"
                        case Int i if i < 10 -> "pequeno"
                        default -> "grande"
                    }
                }

                main() {
                    println(nome(Color.Red))
                    println(classificar(-1))
                    println(classificar(50))
                }
                """);
        assertEquals("red%nnegativo%ngrande%n".formatted(), outText);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void integerDivisionMatchesJvmInNode() throws Exception {
        String outText = runJs("""
                main() {
                    println(10 / 3)
                    println(10 % 3)
                    println(10.0 / 4.0)
                }
                """);
        assertEquals("3%n1%n2.5%n".formatted(), outText);
    }

    @Test
    void emitsJsFileWithoutNode() throws Exception {
        // não requer Node: só prova a emissão
        Compiler.compileToJs("""
                main() {
                    println("ok")
                }
                """, out);
        String js = Files.readString(out.resolve("main.js"), StandardCharsets.UTF_8);
        assertTrue(js.contains("function main()"), js);
        assertTrue(js.contains("println"), js);
        assertTrue(js.contains("'ok'"), js);
    }
}
