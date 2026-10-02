package hydra.compiler.lexer;

import hydra.compiler.token.Token;
import hydra.compiler.token.TokenKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lexer Hydra (Fase 2). Regras:
 * - newline termina instrução (o lexer emite o token de nova linha como
 *   separador implícito via posição; o parser decide fim de stmt);
 * - comentários apenas //;
 * - strings "..." com escape \\n \\t \\\" \\\\; sem interpolação;
 * - palavras reservadas exatamente as 20 de ESPECIFICACAO §2.
 */
public final class Lexer {

    private static final Map<String, TokenKind> KEYWORDS = Map.ofEntries(
            Map.entry("type", TokenKind.TYPE),
            Map.entry("enum", TokenKind.ENUM),
            Map.entry("import", TokenKind.IMPORT),
            Map.entry("val", TokenKind.VAL),
            Map.entry("var", TokenKind.VAR),
            Map.entry("if", TokenKind.IF),
            Map.entry("else", TokenKind.ELSE),
            Map.entry("for", TokenKind.FOR),
            Map.entry("match", TokenKind.MATCH),
            Map.entry("return", TokenKind.RETURN),
            Map.entry("throw", TokenKind.THROW),
            Map.entry("try", TokenKind.TRY),
            Map.entry("catch", TokenKind.CATCH),
            Map.entry("spawn", TokenKind.SPAWN),
            Map.entry("await", TokenKind.AWAIT),
            Map.entry("true", TokenKind.TRUE),
            Map.entry("false", TokenKind.FALSE),
            Map.entry("null", TokenKind.NULL),
            Map.entry("extends", TokenKind.EXTENDS),
            Map.entry("super", TokenKind.SUPER)
    );

    private final String src;
    private int pos;
    private int line = 1;
    private int col = 1;

    public Lexer(String src) {
        this.src = src == null ? "" : src;
    }

    public List<Token> tokenize() {
        List<Token> out = new ArrayList<>();
        while (true) {
            Token t = next();
            out.add(t);
            if (t.kind() == TokenKind.EOF) {
                return out;
            }
        }
    }

    private Token next() {
        skipTrivia();
        if (pos >= src.length()) {
            return Token.eof(line, col);
        }
        char c = src.charAt(pos);
        if (Character.isDigit(c)) {
            return number();
        }
        if (c == '"') {
            return string();
        }
        if (Character.isJavaIdentifierStart(c)) {
            return identOrKeyword();
        }
        return operator();
    }

    private void skipTrivia() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') {
                advance();
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
                continue;
            }
            if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    advance();
                }
                continue;
            }
            break;
        }
    }

    private Token number() {
        int startLine = line;
        int startCol = col;
        int start = pos;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            advance();
        }
        boolean isFloat = false;
        if (pos < src.length() && src.charAt(pos) == '.' && pos + 1 < src.length()
                && Character.isDigit(src.charAt(pos + 1))) {
            isFloat = true;
            advance();
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                advance();
            }
        }
        String text = src.substring(start, pos);
        return new Token(isFloat ? TokenKind.FLOAT : TokenKind.INT, text, startLine, startCol);
    }

    private Token string() {
        int startLine = line;
        int startCol = col;
        advance(); // abre "
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != '"') {
            char c = src.charAt(pos);
            if (c == '\n') {
                return Token.error("string não fechada antes de newline", startLine, startCol);
            }
            if (c == '\\' && pos + 1 < src.length()) {
                char e = src.charAt(pos + 1);
                advance();
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> {
                        return Token.error("escape inválido \\" + e, line, col);
                    }
                }
                advance();
                continue;
            }
            sb.append(c);
            advance();
        }
        if (pos >= src.length()) {
            return Token.error("string não fechada", startLine, startCol);
        }
        advance(); // fecha "
        return new Token(TokenKind.STRING, sb.toString(), startLine, startCol);
    }

    private Token identOrKeyword() {
        int startLine = line;
        int startCol = col;
        int start = pos;
        while (pos < src.length() && Character.isJavaIdentifierPart(src.charAt(pos))) {
            advance();
        }
        String text = src.substring(start, pos);
        TokenKind kind = KEYWORDS.getOrDefault(text, TokenKind.IDENT);
        return new Token(kind, text, startLine, startCol);
    }

    private Token operator() {
        int startLine = line;
        int startCol = col;
        char c = src.charAt(pos);
        char c2 = pos + 1 < src.length() ? src.charAt(pos + 1) : '\0';

        // 2-char
        switch (c) {
            case '=':
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.EQ_EQ, "==", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.EQ, "=", startLine, startCol);
            case '!':
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.BANG_EQ, "!=", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.BANG, "!", startLine, startCol);
            case '<':
                if (c2 == '<') {
                    advance();
                    advance();
                    return new Token(TokenKind.LT_LT, "<<", startLine, startCol);
                }
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.LT_EQ, "<=", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.LT, "<", startLine, startCol);
            case '>':
                if (c2 == '>') {
                    advance();
                    advance();
                    return new Token(TokenKind.GT_GT, ">>", startLine, startCol);
                }
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.GT_EQ, ">=", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.GT, ">", startLine, startCol);
            case '&':
                if (c2 == '&') {
                    advance();
                    advance();
                    return new Token(TokenKind.AMP_AMP, "&&", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.AMP, "&", startLine, startCol);
            case '|':
                if (c2 == '|') {
                    advance();
                    advance();
                    return new Token(TokenKind.PIPE_PIPE, "||", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.PIPE, "|", startLine, startCol);
            case '+':
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.PLUS_EQ, "+=", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.PLUS, "+", startLine, startCol);
            case '-':
                if (c2 == '>') {
                    advance();
                    advance();
                    return new Token(TokenKind.ARROW, "->", startLine, startCol);
                }
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.MINUS_EQ, "-=", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.MINUS, "-", startLine, startCol);
            case '*':
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.STAR_EQ, "*=", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.STAR, "*", startLine, startCol);
            case '/':
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.SLASH_EQ, "/=", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.SLASH, "/", startLine, startCol);
            case '%':
                if (c2 == '=') {
                    advance();
                    advance();
                    return new Token(TokenKind.PERCENT_EQ, "%=", startLine, startCol);
                }
                advance();
                return new Token(TokenKind.PERCENT, "%", startLine, startCol);
            case '^':
                advance();
                return new Token(TokenKind.CARET, "^", startLine, startCol);
            case '(':
                advance();
                return new Token(TokenKind.LPAREN, "(", startLine, startCol);
            case ')':
                advance();
                return new Token(TokenKind.RPAREN, ")", startLine, startCol);
            case '{':
                advance();
                return new Token(TokenKind.LBRACE, "{", startLine, startCol);
            case '}':
                advance();
                return new Token(TokenKind.RBRACE, "}", startLine, startCol);
            case '[':
                advance();
                return new Token(TokenKind.LBRACKET, "[", startLine, startCol);
            case ']':
                advance();
                return new Token(TokenKind.RBRACKET, "]", startLine, startCol);
            case ',':
                advance();
                return new Token(TokenKind.COMMA, ",", startLine, startCol);
            case '.':
                advance();
                return new Token(TokenKind.DOT, ".", startLine, startCol);
            case ':':
                advance();
                return new Token(TokenKind.COLON, ":", startLine, startCol);
            case '?':
                advance();
                return new Token(TokenKind.QUESTION, "?", startLine, startCol);
            default:
                advance();
                return Token.error("caractere inválido '" + c + "'", startLine, startCol);
        }
    }

    private void advance() {
        if (pos < src.length()) {
            if (src.charAt(pos) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }
}
