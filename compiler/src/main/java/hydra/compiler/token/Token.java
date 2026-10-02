package hydra.compiler.token;

/**
 * Token com posição 1-based (linha, coluna).
 * text é o texto literal da fonte (para keywords e identificadores).
 */
public record Token(TokenKind kind, String text, int line, int col) {
    public static Token eof(int line, int col) {
        return new Token(TokenKind.EOF, "", line, col);
    }

    public static Token error(String message, int line, int col) {
        return new Token(TokenKind.ERROR, message, line, col);
    }

    @Override
    public String toString() {
        if (kind == TokenKind.EOF) {
            return "EOF@" + line + ":" + col;
        }
        if (kind == TokenKind.STRING) {
            return "STRING(" + text + ")@" + line + ":" + col;
        }
        return kind + "(" + text + ")@" + line + ":" + col;
    }
}
