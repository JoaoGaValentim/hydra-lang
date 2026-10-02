package hydra.compiler.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** F6-01 — CLI check/run/migrate. */
class CliTest {

    @TempDir
    Path dir;

    static boolean nodeAvailable() {
        return Cli.nodeAvailable();
    }

    private record Out(int code, String out, String err) {}

    private Out run(String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int code = Cli.run(args, new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        return new Out(code, o.toString(StandardCharsets.UTF_8), e.toString(StandardCharsets.UTF_8));
    }

    private Path write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void helpExitsZero() {
        Out r = run("help");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("hydra check"), r.out());
    }

    @Test
    void unknownCommandExitsTwo() {
        Out r = run("nope");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("desconhecido"), r.err());
    }

    @Test
    void checkOkFile() throws Exception {
        Path f = write("ok.hy", """
                main() {
                    println("ok")
                }
                """);
        Out r = run("check", f.toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("ok"), r.out());
    }

    @Test
    void checkBadFileReportsHypCode() throws Exception {
        Path f = write("bad.hy", "main( {");
        Out r = run("check", f.toString());
        assertEquals(1, r.code());
        assertTrue(r.err().contains("HYP") || r.err().contains("hy"), r.err());
    }

    @Test
    void checkMissingFile() {
        Out r = run("check", dir.resolve("missing.hy").toString());
        assertEquals(1, r.code());
        assertTrue(r.err().contains("não encontrado"), r.err());
    }

    @Test
    void runJvmExecutesHello() throws Exception {
        Path f = write("hello.hy", """
                main() {
                    println("hello-cli")
                }
                """);
        // captura stdout do processo emulado (System.out do JVM)
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            Out r = run("run", f.toString());
            assertEquals(0, r.code(), r.err());
        } finally {
            System.setOut(old);
        }
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("hello-cli"),
                "stdout: " + buf);
    }

    @Test
    @EnabledIf("nodeAvailable")
    void runJsExecutesHello() throws Exception {
        Path f = write("hello-js.hy", """
                main() {
                    println("hello-js")
                }
                """);
        Out r = run("run", f.toString(), "--js");
        assertEquals(0, r.code(), r.err());
    }

    @Test
    void migrateWritesHydraFile() throws Exception {
        Path kf = write("hello.kf", """
                main() {
                    println("Hello, World!");
                }
                """);
        Path hy = dir.resolve("hello.hy");
        Out r = run("migrate", kf.toString(), hy.toString());
        assertEquals(0, r.code(), r.err() + "\n" + r.out());
        assertTrue(Files.isRegularFile(hy));
        String text = Files.readString(hy, StandardCharsets.UTF_8);
        assertTrue(text.contains("println(\"Hello, World!\")"), text);
        // e o .hy migrado passa no check
        Out c = run("check", hy.toString());
        assertEquals(0, c.code(), c.err());
    }

    @Test
    void migratePartialExitsOne() throws Exception {
        Path kf = write("partial.kf", """
                main() {
                    var i = 0;
                    do {
                        i = i + 1;
                    } while (i < 3);
                    println(i);
                }
                """);
        Path hy = dir.resolve("partial.hy");
        Out r = run("migrate", kf.toString(), hy.toString());
        assertEquals(1, r.code());
        assertTrue(r.err().contains("MIG"), r.err());
        assertTrue(Files.isRegularFile(hy), "saída parcial ainda é gravada");
    }

    @Test
    void versionPrints() {
        Out r = run("version");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("hydra"), r.out());
    }

    @Test
    void fmtRewritesInPlace() throws Exception {
        Path f = write("messy.hy", "main() {\nprintln(\"x\")\n}\n");
        Out r = run("fmt", f.toString());
        assertEquals(0, r.code(), r.err());
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertEquals("main() {\n    println(\"x\")\n}\n", text);
        assertTrue(r.out().contains("reformat"), r.out());
        Out again = run("fmt", f.toString());
        assertEquals(0, again.code());
        assertTrue(again.out().contains("unchanged"), again.out());
    }

    @Test
    void fmtKeepsComments() throws Exception {
        Path f = write("com.hy", "// top\nmain() {\nprintln(\"x\") // t\n}\n");
        Out r = run("fmt", f.toString());
        assertEquals(0, r.code(), r.err());
        String text = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(text.contains("// top"), text);
        assertTrue(text.contains("// t"), text);
    }

    @Test
    void fmtParseErrorExitsOne() throws Exception {
        Path f = write("bad.hy", "main( {");
        Out r = run("fmt", f.toString());
        assertEquals(1, r.code());
        assertTrue(r.err().contains("HYP") || r.err().contains("hy"), r.err());
    }

    @Test
    void fmtMissingFile() {
        Out r = run("fmt", dir.resolve("missing.hy").toString());
        assertEquals(1, r.code());
        assertTrue(r.err().contains("não encontrado"), r.err());
    }

    @Test
    void testPassesAndExitsZero() throws Exception {
        Path f = write("suite.hy", """
                soma(a: Int, b: Int): Int = a + b

                test "soma ok" {
                    assert(soma(2, 3) == 5, "2+3 deve ser 5")
                }

                main() {
                    println("main nao roda em test")
                }
                """);
        Out r = run("test", f.toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("PASS soma ok"), r.out());
        assertTrue(r.out().contains("0 failed of 1 tests"), r.out());
        assertFalse(r.out().contains("main nao roda"), r.out());
        assertFalse(r.out().contains("nao deve rodar"), r.out());
    }

    @Test
    void testFailureExitsOneAndShowsMessage() throws Exception {
        Path f = write("fail.hy", """
                test "falha" {
                    assert(1 == 2, "quebrou de proposito")
                }
                """);
        Out r = run("test", f.toString());
        assertEquals(1, r.code(), r.out() + r.err());
        assertTrue(r.out().contains("FAIL falha: quebrou de proposito"), r.out());
        assertTrue(r.out().contains("1 failed of 1 tests"), r.out());
    }

    @Test
    void testDirectoryRunsEachFileIndependently() throws Exception {
        write("a.hy", """
                test "a" {
                    assert(true, "ok")
                }
                """);
        write("b.hy", """
                test "b" {
                    assert(1 == 2, "b falhou")
                }
                """);
        Out r = run("test", dir.toString());
        assertEquals(1, r.code(), r.out() + r.err());
        assertTrue(r.out().contains("PASS a"), r.out());
        assertTrue(r.out().contains("FAIL b"), r.out());
    }

    @Test
    void testSkipsFilesWithoutTests() throws Exception {
        Path f = write("plain.hy", """
                main() {
                    println("nada")
                }
                """);
        Out r = run("test", f.toString());
        assertEquals(0, r.code(), r.out() + r.err());
        assertTrue(r.out().contains("nenhum teste encontrado"), r.out());
        assertFalse(r.out().contains("nada"), r.out());
    }

    @Test
    void testArityErrorReportsParseOrIr() throws Exception {
        Path f = write("bad.hy", """
                test "x" {
                    assert(1 == 1, "a", "b")
                }
                """);
        Out r = run("test", f.toString());
        assertEquals(1, r.code());
        assertTrue(r.out().contains("0 passaram, 0 falharam") || r.err().contains("assert"),
                r.out() + r.err());
    }

    @Test
    void newCreatesProjectThatRuns() throws Exception {
        Path proj = dir.resolve("app");
        Out r = run("new", proj.toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(Files.isRegularFile(proj.resolve("hydra.toml")), r.out());
        assertTrue(Files.isRegularFile(proj.resolve("src/Main.hy")), r.out());
        assertTrue(Files.isRegularFile(proj.resolve("tests/smoke.hy")), r.out());
        Out runMain = run("run", proj.resolve("src/Main.hy").toString());
        assertEquals(0, runMain.code(), runMain.err());
        Out test = run("test", proj.resolve("tests").toString());
        assertEquals(0, test.code(), test.out() + test.err());
        assertTrue(test.out().contains("PASS soma"), test.out());
    }

    @Test
    void newRefusesToOverwrite() throws Exception {
        Path proj = dir.resolve("app2");
        assertEquals(0, run("new", proj.toString()).code());
        Out again = run("new", proj.toString());
        assertEquals(1, again.code());
        assertTrue(again.err().contains("APP003"), again.err());
    }

    @Test
    void helpMentionsTestAndNew() {
        Out r = run("help");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("hydra test"), r.out());
        assertTrue(r.out().contains("hydra new"), r.out());
    }

    @Test
    void helpMentionsFmt() {
        Out r = run("help");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("hydra fmt"), r.out());
    }

    @Test
    @EnabledIf("nodeAvailable")
    void testRunsOnJsTarget() throws Exception {
        Path f = write("js-suite.hy", """
                test "js" {
                    assert(3 == 3, "ok")
                }
                """);
        Out r = run("test", f.toString(), "--js");
        assertEquals(0, r.code(), r.out() + r.err());
        assertTrue(r.out().contains("PASS js"), r.out());
    }
}
