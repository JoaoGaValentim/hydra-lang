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
    void stringEqualityIsByContent() throws Exception {
        String outText = runMain("""
                main() {
                    val a = "hy"
                    val b = "hy" + ""
                    println(a == b)
                    println(a != "x")
                }
                """);
        assertEquals("true%ntrue%n".formatted(), outText);
    }

    @Test
    void assertTrueContinues() throws Exception {
        String outText = runMain("""
                main() {
                    assert(2 > 1, "sanidade")
                    println("depois")
                }
                """);
        assertEquals("depois%n".formatted(), outText);
    }

    @Test
    void assertFalseThrowsWithMessage() throws Exception {
        String outText = runMain("""
                main() {
                    try {
                        assert(1 == 2, "quebrou")
                    } catch (e) {
                        println(e)
                    }
                }
                """);
        assertEquals("quebrou%n".formatted(), outText);
    }

    @Test
    void throwMessageReachesCatchAsString() throws Exception {
        String outText = runMain("""
                main() {
                    try {
                        throw "detalhe"
                    } catch (e) {
                        println("erro: " + e)
                    }
                }
                """);
        assertEquals("erro: detalhe%n".formatted(), outText);
    }

    @Test
    void measuredTriesPairWithCorrectHandlers() throws Exception {
        // dois try/catch seguidos: cada handler recebe o seu erro (regressão
        // de pareamento por ordem de bloco do IR)
        String outText = runMain("""
                main() {
                    try {
                        throw "um"
                    } catch (e) {
                        println("1:" + e)
                    }
                    try {
                        throw "dois"
                    } catch (e) {
                        println("2:" + e)
                    }
                }
                """);
        assertEquals("1:um%n2:dois%n".formatted(), outText);
    }

    @Test
    void testMethodNamesAreSanitized() throws Exception {
        Compiler.compileTo("""
                test "nome com espaço" {
                    assert(true, "ok")
                }

                main() {
                    println("fim")
                }
                """, out);
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                JvmBackendE2ETest.class.getClassLoader())) {
            Class<?> main = cl.loadClass("Main");
            Method m = main.getMethod("test_nome_com_espaço");
            assertNotNull(m, "test \"nome com espaço\" deve virar método JVM válido");
        }
    }

    @Test
    void matchLiteralCasesWork() throws Exception {
        String outText = runMain("""
                nome(n: Int): String {
                    match (n) {
                        case 1 -> "um"
                        case 2 -> "dois"
                        default -> "outro"
                    }
                }

                main() {
                    println(nome(1))
                    println(nome(2))
                    println(nome(9))
                }
                """);
        assertEquals("um%ndois%noutro%n".formatted(), outText);
    }

    @Test
    void matchGuardsAndBindingsWork() throws Exception {
        String outText = runMain("""
                classificar(n: Int): String {
                    match (n) {
                        case Int i if i < 0 -> "negativo"
                        case Int i if i == 0 -> "zero"
                        case Int i if i < 10 -> "pequeno"
                        default -> "grande"
                    }
                }

                main() {
                    println(classificar(-1))
                    println(classificar(0))
                    println(classificar(5))
                    println(classificar(50))
                }
                """);
        assertEquals("negativo%nzero%npequeno%ngrande%n".formatted(), outText);
    }

    @Test
    void enumMatchAndTailReturn() throws Exception {
        String outText = runMain("""
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

                main() {
                    println(nome(Color.Red))
                    println(nome(Color.Blue))
                }
                """);
        assertEquals("red%nblue%n".formatted(), outText);
    }

    @Test
    void tailReturnWithoutAnnotationInfersType() throws Exception {
        String outText = runMain("""
                tripla(x: Int) = x * 3

                saudar(): String {
                    "ola"
                }

                main() {
                    println(tripla(3))
                    println(saudar())
                }
                """);
        assertEquals("9%nola%n".formatted(), outText);
    }

    @Test
    void matchExprWithBooleanSubject() throws Exception {
        String outText = runMain("""
                main() {
                    val status = match (1 > 0) {
                        case true -> "positivo"
                        default -> "nao"
                    }
                    println(status)
                }
                """);
        assertEquals("positivo%n".formatted(), outText);
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
