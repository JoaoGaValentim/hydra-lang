package hydra.compiler.parser;

/** Erro de sintaxe com posição. */
public final class SyntaxError extends RuntimeException {
    private final int line;
    private final int col;

    public SyntaxError(String message, int line, int col) {
        super(message + " (" + line + ":" + col + ")");
        this.line = line;
        this.col = col;
    }

    public int line() { return line; }
    public int col() { return col; }
}
