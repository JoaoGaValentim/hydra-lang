package hydra.compiler.token;

/**
 * Tokens do Hydra. Conjunto de palavras reservadas = 20
 * (hydra/ESPECIFICACAO.md §2, D-HYD-011/014/015..018).
 */
public enum TokenKind {
    EOF,
    ERROR,

    // literais e identificadores
    IDENT,
    INT,
    FLOAT,
    STRING,

    // 20 palavras reservadas
    TYPE,
    ENUM,
    IMPORT,
    VAL,
    VAR,
    IF,
    ELSE,
    FOR,
    MATCH,
    RETURN,
    THROW,
    TRY,
    CATCH,
    SPAWN,
    AWAIT,
    TRUE,
    FALSE,
    NULL,
    EXTENDS,
    SUPER,

    // contextuais de topo (D-HYD-015) — o lexer emite IDENT; o parser decide
    // (mantidos como constantes de uso no parser, não como tokens)

    // operadores e pontuação
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    BANG,
    EQ,
    EQ_EQ,
    BANG_EQ,
    LT,
    LT_EQ,
    GT,
    GT_EQ,
    AMP_AMP,
    PIPE_PIPE,
    AMP,
    PIPE,
    CARET,
    LT_LT,
    GT_GT,
    PLUS_EQ,
    MINUS_EQ,
    STAR_EQ,
    SLASH_EQ,
    PERCENT_EQ,
    ARROW,
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    LBRACKET,
    RBRACKET,
    COMMA,
    DOT,
    COLON,
    QUESTION;

    public boolean isKeyword() {
        return switch (this) {
            case TYPE, ENUM, IMPORT, VAL, VAR, IF, ELSE, FOR, MATCH,
                 RETURN, THROW, TRY, CATCH, SPAWN, AWAIT,
                 TRUE, FALSE, NULL, EXTENDS, SUPER -> true;
            default -> false;
        };
    }
}
