package hydra.compiler.parser;

/**
 * Erro de sintaxe com posição, código e sugestão opcional (F2-03).
 * Formato da mensagem: {@code msg (line:col)} + {@code \n  hint: ...} se houver sugestão.
 */
public final class SyntaxError extends RuntimeException {
    private final int line;
    private final int col;
    private final String code;
    private final String suggestion;

    public SyntaxError(String message, int line, int col) {
        this(message, line, col, null, null);
    }

    public SyntaxError(String message, int line, int col, String code, String suggestion) {
        super(format(message, line, col, suggestion));
        this.line = line;
        this.col = col;
        this.code = code == null ? "HYP000" : code;
        this.suggestion = suggestion;
    }

    public int line() { return line; }
    public int col() { return col; }
    public String code() { return code; }
    public String suggestion() { return suggestion; }

    private static String format(String message, int line, int col, String suggestion) {
        String base = message + " (" + line + ":" + col + ")";
        if (suggestion == null || suggestion.isBlank()) {
            return base;
        }
        return base + "\n  hint: " + suggestion;
    }
}
