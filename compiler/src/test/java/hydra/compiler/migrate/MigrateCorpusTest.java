package hydra.compiler.migrate;

import hydra.compiler.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F4-02 — migração sobre o corpus Kof real (training/examples + golden).
 * Regra: toda fonte Kof válida migra para (a) Hydra parseável, ou
 * (b) resultado com diagnóstico honesto (nunca saída silenciosa quebrada
 * sem MIG).
 */
class MigrateCorpusTest {

    private static final Path KOF = Path.of("..", "kof_upstream").toAbsolutePath().normalize();

    @Test
    void trainingExamplesMigrateToParseableHydraOrHonestDiags() throws IOException {
        Path dir = KOF.resolve("training/examples");
        assumeKof(dir);
        List<Path> files = listKf(dir);
        assertFalse(files.isEmpty(), "esperava .kf em " + dir);
        for (Path f : files) {
            String src = Files.readString(f, StandardCharsets.UTF_8);
            MigrateResult r = Migrator.migrate(src, f.getFileName().toString());
            if (r.hydraSource().isBlank() && !r.diagnostics().isEmpty()) {
                // falha dura de parse Kof — aceitável com MIG008
                assertTrue(r.diagnostics().stream().anyMatch(d -> "MIG008".equals(d.code())),
                        f + ": sem fonte e sem MIG008:\n" + r.diagnosticsText());
                continue;
            }
            assertFalse(r.hydraSource().isBlank(), f + ": saída vazia sem diagnóstico");
            if (r.diagnostics().stream().anyMatch(d -> "MIG009".equals(d.code()))) {
                // gate Hydra falhou — deve haver MIG009 e ainda assim saída parcial
                assertTrue(r.diagnostics().stream().anyMatch(d -> d.code().startsWith("MIG")),
                        f + ": gate falhou sem diagnóstico MIG");
                continue;
            }
            assertDoesNotThrow(() -> Parser.parse(r.hydraSource()),
                    () -> f + " → Hydra não parseia:\n" + r.hydraSource()
                            + "\nDIAGS:\n" + r.diagnosticsText());
        }
    }

    @Test
    void goldenFunctionsAndRecordsMigrateClean() throws IOException {
        assertMigratesClean(KOF.resolve("tests/golden/functions/Main.kf"));
        assertMigratesClean(KOF.resolve("tests/golden/records/Main.kf"));
        assertMigratesClean(KOF.resolve("tests/golden/hello/Main.kf"));
    }

    @Test
    void goldenControlFlowMigrates() throws IOException {
        Path f = KOF.resolve("tests/golden/control-flow/Main.kf");
        assumeKof(f);
        MigrateResult r = Migrator.migrate(Files.readString(f), f.getFileName().toString());
        assertFalse(r.hydraSource().isBlank());
        // pode ter MIG parciais (while→for ok; for-in ok) — gate deve passar
        assertTrue(r.diagnostics().stream().noneMatch(d -> "MIG009".equals(d.code())),
                () -> "control-flow gate:\n" + r.diagnosticsText() + "\n" + r.hydraSource());
        assertDoesNotThrow(() -> Parser.parse(r.hydraSource()));
    }

    @Test
    void goldenExceptionsDiagnosesFinally() throws IOException {
        Path f = KOF.resolve("tests/golden/exceptions/Main.kf");
        assumeKof(f);
        MigrateResult r = Migrator.migrate(Files.readString(f), f.getFileName().toString());
        assertTrue(r.diagnostics().stream().anyMatch(d -> "MIG002".equals(d.code())),
                r.diagnosticsText());
        assertTrue(r.hydraSource().contains("catch (e)"), r.hydraSource());
        assertDoesNotThrow(() -> Parser.parse(r.hydraSource()));
    }

    @Test
    void goldenStringsMigrates() throws IOException {
        Path f = KOF.resolve("tests/golden/strings/Main.kf");
        assumeKof(f);
        MigrateResult r = Migrator.migrate(Files.readString(f), f.getFileName().toString());
        assertFalse(r.hydraSource().isBlank());
        if (r.diagnostics().stream().noneMatch(d -> "MIG009".equals(d.code()))) {
            assertDoesNotThrow(() -> Parser.parse(r.hydraSource()), r.hydraSource());
        }
    }

    private static void assertMigratesClean(Path f) throws IOException {
        assumeKof(f);
        MigrateResult r = Migrator.migrate(Files.readString(f), f.getFileName().toString());
        assertTrue(r.ok(), f + ":\n" + r.diagnosticsText() + "\n" + r.hydraSource());
        assertDoesNotThrow(() -> Parser.parse(r.hydraSource()), r.hydraSource());
    }

    private static List<Path> listKf(Path dir) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(p -> p.toString().endsWith(".kf")).sorted().forEach(out::add);
        }
        return out;
    }

    private static void assumeKof(Path p) {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(p),
                "corpus Kof ausente: " + p + " (CI instala/clona o upstream)");
    }
}
