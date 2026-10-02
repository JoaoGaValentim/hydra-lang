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
    void helpMentionsFmt() {
        Out r = run("help");
        assertEquals(0, r.code());
        assertTrue(r.out().contains("hydra fmt"), r.out());
    }
}
