package hydra.compiler.migrate;

/** Diagnóstico de migração Kof→Hydra (códigos MIG0xx). */
public record MigrateDiagnostic(String code, String message, String suggestion) {

    public static MigrateDiagnostic of(String code, String message) {
        return new MigrateDiagnostic(code, message, null);
    }

    public static MigrateDiagnostic of(String code, String message, String suggestion) {
        return new MigrateDiagnostic(code, message, suggestion);
    }

    @Override
    public String toString() {
        return code + ": " + message + (suggestion == null ? "" : "  → " + suggestion);
    }
}
