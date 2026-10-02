# IR — Hydra (F3-01)

Contrato estável entre frontend e backends (D-HYD-002). Shape espelha o IR do Kof:
módulo → classes → métodos → basic blocks → ops de pilha.

## Nós

| Nó | Campos |
|---|---|
| `Module` | `name`, `classes`, `imports`, `sourceName` |
| `Class` | `name`, `superName`, `fields`, `methods`, `enumCases` |
| `Field` | `name`, `type`, `mutable`, `initialValue` |
| `Method` | `name`, `returnType`, `parameterTypes`, `locals`, `blocks` |
| `Local` | `index`, `name`, `type` |
| `Block` | `index`, `ops` |

Tipos canônicos: `Int`, `Float`, `String`, `Bool`, `Void`, `Any`, nome de tipo.

## Ops (pilha)

| Op | Efeito |
|---|---|
| `LoadLiteral(type, value)` | empilha constante |
| `LoadLocal(index, type)` | empilha local |
| `StoreLocal(index, type)` | desempilha → local |
| `Binary(op, type)` | desempilha 2, empilha resultado |
| `Unary(op, type)` | desempilha 1, empilha resultado |
| `Call(name, paramTypes, ret, hasReceiver)` | desempilha args (+receiver), empilha ret |
| `Return(type)` | termina método (`Void` = return sem valor) |
| `Pop` | descarta topo |
| `Jump(target)` | salto incondicional |
| `JumpIfFalse(target)` | desempilha Bool, salta se falso |
| `LoadEnum(enum, case)` | empilha constante de enum |
| `NewObject(type, fieldTypes)` | constrói instância (v2) |
| `LoadField/StoreField` | membro (sem `this` na fonte — D-HYD-014) |
| `Throw` | desempilha erro e lança |
| `TryStart/TryEnd/CatchStart` | marcadores de região try |

## Lowering (IrBuilder)

- Funções top-level → classe sintética `Main`.
- `type` → `Ir.Class` com fields/métodos; `enum` → classe + `enumCases`.
- `if`/`for`/`match`/`try` → basic blocks + `Jump`/`JumpIfFalse`.
- Identificador não-local que casa com campo do tipo atual → `LoadField` (sem receiver).
- `Color.Red` → `LoadEnum(Color, Red)`.
- Lambda: **na v2** (F3-02) — erro honesto na v1.
- Aritidade de chamada conferida no IR (diagnóstico `IR: aridade…`).

## Testes

`IrTest` (10) cobre: hello, val/var/assign, funções, if+throw, try/catch, enum+match,
type-decl, aridade, for clássico, imports. Shape canônico via `Ir.shape(module)`.

## Status

| Item | Estado |
|---|---|
| F3-01 IR + lowering v1 | **feito** |
| F3-02 backend JVM mínimo | todo |
| F3-03 Native/JS | todo |
| F3-04 paridade de alvos | todo |
