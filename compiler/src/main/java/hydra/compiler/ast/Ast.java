package hydra.compiler.ast;

import hydra.compiler.token.Token;

import java.util.List;

/** AST do Hydra (Fase 2). Tipos aninhados em um único arquivo. */
public final class Ast {
    private Ast() {}

    public sealed interface Node {
        Token pos();
    }

    public record Unit(List<ImportDecl> imports, List<Decl> decls, Token pos) implements Node {}

    public record ImportDecl(List<String> parts, Token pos) implements Node {}

    public sealed interface Decl extends Node {}

    public record FunDecl(String name, List<Param> params, TypeRef returnType, Block body, Token pos) implements Decl {}

    public record Param(String name, TypeRef type, Token pos) implements Node {}

    public record TypeDecl(String name, TypeRef extendsType, List<Field> fields, List<FunDecl> methods, Token pos) implements Decl {}

    public record EnumDecl(String name, List<String> cases, Token pos) implements Decl {}

    public record Field(boolean mutable, TypeRef type, String name, Token pos) implements Node {}

    public record TypeRef(String name, boolean nullable, Token pos) implements Node {}

    public sealed interface Stmt extends Node {}

    public record VarDecl(boolean mutable, String name, TypeRef type, Expr init, Token pos) implements Stmt {}

    public record Block(List<Stmt> stmts, Token pos) implements Stmt {}

    public record ReturnStmt(Expr value, Token pos) implements Stmt {}

    public record IfStmt(Expr cond, Block thenBlock, Block elseBlock, Token pos) implements Stmt {}

    public record ForStmt(ForHead head, Block body, Token pos) implements Stmt {}

    public sealed interface ForHead extends Node {}

    public record ForInHead(boolean mutable, String name, Expr iter, Token pos) implements ForHead {}

    public record ForClassicHead(VarDecl init, Expr cond, Expr update, Token pos) implements ForHead {}

    public record ForCondHead(Expr cond, Token pos) implements ForHead {}

    public record ExprStmt(Expr expr, Token pos) implements Stmt {}

    public record ThrowStmt(Expr value, Token pos) implements Stmt {}

    public record TryStmt(Block body, List<CatchClause> catches, Token pos) implements Stmt {}

    public record CatchClause(String name, Block body, Token pos) implements Node {}

    public record MatchStmt(Expr subject, List<CaseArm> cases, Token pos) implements Stmt {}

    public record CaseArm(String patternType, String patternName, Expr guard, Expr result, Token pos) implements Node {}

    public record SpawnStmt(Expr value, Token pos) implements Stmt {}

    public sealed interface Expr extends Node {}

    public record IntLit(long value, Token pos) implements Expr {}

    public record FloatLit(double value, Token pos) implements Expr {}

    public record StringLit(String value, Token pos) implements Expr {}

    public record BoolLit(boolean value, Token pos) implements Expr {}

    public record NullLit(Token pos) implements Expr {}

    public record IdentExpr(String name, Token pos) implements Expr {}

    public record BinaryExpr(String op, Expr left, Expr right, Token pos) implements Expr {}

    public record UnaryExpr(String op, Expr operand, Token pos) implements Expr {}

    public record CallExpr(Expr callee, List<Expr> args, Token pos) implements Expr {}

    public record FieldExpr(Expr receiver, String name, Token pos) implements Expr {}

    /** `alvo[índice]` — acesso a elemento (List/Map). */
    public record IndexExpr(Expr target, Expr index, Token pos) implements Expr {}

    /** `chave to valor` — par de mapOf. */
    public record PairExpr(Expr left, Expr right, Token pos) implements Expr {}

    public record AssignExpr(String op, Expr target, Expr value, Token pos) implements Expr {}

    public record IfExpr(Expr cond, Expr thenExpr, Expr elseExpr, Token pos) implements Expr {}

    public record LambdaExpr(List<Param> params, Block body, Expr exprBody, Token pos) implements Expr {}

    public record MatchExpr(MatchStmt stmt, Token pos) implements Expr {}
}
