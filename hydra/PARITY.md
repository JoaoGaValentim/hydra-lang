# PARITY — AST Hydra ↔ Kof (F2-04)

Contrato de equivalência estrutural entre a AST do Hydra e a do Kof.
Teste executável: `compiler/src/test/java/hydra/compiler/parity/ParityTest.java`.
Fonte de verdade: parser Kof (`dev.kof.compiler.parser`) + parser Hydra (`hydra.compiler.parser`).

## Mapeamento de nós

| Conceito | Kof | Hydra |
|---|---|---|
| Unidade | `CompilationUnitNode` | `Unit` |
| Função | `FunctionDeclarationNode` | `FunDecl` |
| Classe/tipo | `ClassDeclarationNode` / `RecordDeclarationNode` | `TypeDecl` |
| Enum | `EnumDeclarationNode` (`constants`) | `EnumDecl` (`cases`) |
| Campo | `FieldDeclarationNode` | `Field` |
| Parâmetro | `FormalParameterNode` | `Param` |
| Identificador | `IdentifierExpr` | `IdentExpr` |
| Literal | `LiteralExpr(kind, value)` | `IntLit`/`FloatLit`/`StringLit`/`BoolLit`/`NullLit` |
| Binário | `BinaryExpr(op, l, r)` | `BinaryExpr(op, l, r)` |
| Atribuição | `AssignmentExpr(target, op, value)` | `AssignExpr(op, target, value)` |
| Campo acesso | `FieldAccessExpr(recv, name)` | `FieldExpr(recv, name)` |
| Chamada | `MethodCallExpr(recv, name, args)` | `CallExpr(callee, args)` |
| Chamada sem recv | `MethodCallExpr(null, name, args)` | `CallExpr(IdentExpr(name), args)` |
| Chamada de método | `MethodCallExpr(recv, name, args)` | `CallExpr(FieldExpr(recv, name), args)` |
| Lambda | `LambdaExpr(params, body)` | `LambdaExpr(params, body, exprBody)` |
| Var decl | `VarDeclStmt(type, name, init)` — `type` ∈ {`val`,`var`} | `VarDecl(mutable, name, type, init)` |
| Return | `ReturnStmt(value)` | `ReturnStmt(value)` |

## Diferenças sintáticas conhecidas (esperadas)

| Ponto | Kof | Hydra |
|---|---|---|
| Terminação de instrução | `;` obrigatório | newline (sem `;`) |
| Declaração de tipo | `class`/`record`/`entity` | `type` |
| Herança | `extends Super` após modifiers | `extends Super` após header de campos |
| Campo | `class U { var name: String }` | `type U(var name: String)` |
| Chamada de função | `println(x)` → `MethodCallExpr(null,…)` | `println(x)` → `CallExpr(IdentExpr,…)` |
| Mutabilidade em AST | `VarDeclStmt.type = "val"`/`"var"` | `VarDecl.mutable` |
| Comentários | `//` e `/* */` | só `//` |
| Enum | `enum C { A, B }` (vírgulas) | `enum C { A B }` (newline) |

## Shape canônico (usado no teste)

Nós são reduzidos a strings de forma:

```
fun(name; p:Type,…; ret) { … }
call(name; args)
call(recv.name; args)
field(recv; name)
id(name)
lit(kind; value)
bin(op; l; r)
assign(op; target; value)
var(val|var; name; init)
return(expr)
enum(name; cases)
type(name; extends; fields; methods)
```

Comparação: parse de fonte pareada (Hydra e Kof equivalentes) → shapes → igualdade.
