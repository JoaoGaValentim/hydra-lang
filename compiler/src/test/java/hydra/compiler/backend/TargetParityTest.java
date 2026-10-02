package hydra.compiler.backend;

import hydra.compiler.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F3-04 — paridade de alvos: mesmo programa Hydra → JVM e JS produzem a mesma saída.
 */
class TargetParityTest {

    @TempDir
    Path jvmOut;

    @TempDir
    Path jsOut;

    static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String runJvm(String source) throws Exception {
        Compiler.compileTo(source, jvmOut);
        try (URLClassLoader cl = new URLClassLoader(new URL[]{jvmOut.toUri().toURL()},
                TargetParityTest.class.getClassLoader());
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

    private String runJs(String source) throws Exception {
        Compiler.compileToJs(source, jsOut);
        ProcessBuilder pb = new ProcessBuilder("node", jsOut.resolve("main.js").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), stdout);
        return stdout;
    }

    private void assertSameOutput(String name, String source) throws Exception {
        String jvm = runJvm(source);
        String js = runJs(source);
        assertEquals(jvm, js, "paridade de alvos falhou em " + name
                + "\nJVM:\n" + jvm + "\nJS:\n" + js);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void helloParity() throws Exception {
        assertSameOutput("hello", """
                main() {
                    println("Hello, Hydra")
                }
                """);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void arithmeticParity() throws Exception {
        assertSameOutput("arith", """
                main() {
                    val x = 10
                    var y = x + 5
                    y = y * 2
                    println(y)
                }
                """);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void functionParity() throws Exception {
        assertSameOutput("fun", """
                dobro(x: Int): Int {
                    return x * 2
                }

                main() {
                    println(dobro(21))
                }
                """);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void forParity() throws Exception {
        assertSameOutput("for", """
                main() {
                    var total = 0
                    for (var i = 0, i < 3, i = i + 1) {
                        total = total + i
                    }
                    println(total)
                }
                """);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void stringParity() throws Exception {
        assertSameOutput("string", """
                saudar(nome: String): String {
                    return "Hello, " + nome
                }

                main() {
                    println(saudar("Hydra"))
                }
                """);
    }

    @Test
    void jvmAloneStillWorks() throws Exception {
        // cobre caminho JVM mesmo sem Node
        String outText = runJvm("""
                main() {
                    println("jvm-only")
                }
                """);
        assertEquals("jvm-only%n".formatted(), outText);
    }
}
