package hydra.compiler.migrate;

import java.util.List;

/** Resultado de `hydra migrate`: fonte Hydra + diagnósticos honestos. */
public record MigrateResult(String hydraSource, List<MigrateDiagnostic> diagnostics, boolean ok) {

    public static MigrateResult failure(List<MigrateDiagnostic> diagnostics) {
        return new MigrateResult("", List.copyOf(diagnostics), false);
    }

    public String diagnosticsText() {
        if (diagnostics.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MigrateDiagnostic d : diagnostics) {
            sb.append(d).append('\n');
        }
        return sb.toString();
    }
}
