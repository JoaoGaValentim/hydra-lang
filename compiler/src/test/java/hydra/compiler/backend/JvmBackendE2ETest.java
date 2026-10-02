package hydra.compiler.backend;

import hydra.compiler.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F3-02 — ponta a ponta: fonte Hydra → IR → bytecode JVM → execução real.
 */
class JvmBackendE2ETest {

    @TempDir
    Path out;

    private String runMain(String source) throws Exception {
        Compiler.compileTo(source, out);
        assertTrue(java.nio.file.Files.exists(out.resolve("Main.class")),
                "Main.class deve existir em " + out);
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                JvmBackendE2ETest.class.getClassLoader());
             ByteArrayOutputStream buf = new ByteArrayOutputStream();
             PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            PrintStream old = System.out;
            System.setOut(ps);
            try {
                Class<?> main = cl.loadClass("Main");
                Method m = main.getMethod("main");
                m.invoke(null);
            } finally {
                System.setOut(old);
            }
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    @Test
    void helloWorldRuns() throws Exception {
        String outText = runMain("""
                main() {
                    println("Hello, Hydra")
                }
                """);
        assertEquals("Hello, Hydra%n".formatted(), outText);
    }

    @Test
    void valVarAndArithmeticRuns() throws Exception {
        String outText = runMain("""
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
    void functionCallRuns() throws Exception {
        String outText = runMain("""
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
    void ifThrowTryRuns() throws Exception {
        String outText = runMain("""
                dividir(a: Int, b: Int): Int {
                    if (b == 0) {
                        throw "divisao por zero"
                    }
                    return a / b
                }

                main() {
                    try {
                        println(dividir(1, 0))
                    } catch (e) {
                        println("erro")
                    }
                    println(dividir(10, 2))
                }
                """);
        assertEquals("erro%n5%n".formatted(), outText);
    }

    @Test
    void stringConcatRuns() throws Exception {
        String outText = runMain("""
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
    void classicForRuns() throws Exception {
        String outText = runMain("""
                main() {
                    var total = 0
                    for (var i = 0, i < 3, i = i + 1) {
                        total = total + i
                    }
                    println(total)
                }
                """);
        // i = 0,1,2 → total = 3
        assertEquals("3%n".formatted(), outText);
    }

    @Test
    void compiledClassIsValidJvm() throws Exception {
        Compiler.compileTo("""
                main() {
                    println("ok")
                }
                """, out);
        byte[] bytes = java.nio.file.Files.readAllBytes(out.resolve("Main.class"));
        // CAFEBABE
        assertEquals((byte) 0xCA, bytes[0]);
        assertEquals((byte) 0xFE, bytes[1]);
        assertEquals((byte) 0xBA, bytes[2]);
        assertEquals((byte) 0xBE, bytes[3]);
    }
}
