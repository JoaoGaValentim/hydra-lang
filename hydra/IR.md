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
| F3-02 backend JVM mínimo | **feito** — E2E 7/7 (`JvmBackendE2ETest`) |
| F3-03 backend JS | **feito** — E2E Node (`JsBackendE2ETest`); Native **BLQ-02** |
| F3-04 paridade de alvos | **feito** — `TargetParityTest` JVM≡JS |

## Backend JVM (F3-02)

- ASM 9.7.1; classes `Main` + `<init>` + métodos estáticos.
- `Int`→`long`, `Float`→`double`, `Bool`→`int`, `String`→`String`.
- `println`/`print` → `System.out` (Int/Float via scratch local).
- `+` de String → `String.concat`.
- try/catch → `visitTryCatchBlock(Throwable)`.
- `Binary.operandType` = tipo dos operandos (comparações em Int usam `LCMP`).

## Backend JS (F3-03)

- Mesmo IR; emite `main.js` com `'use strict'` + shim `println`/`print` (`console.log` / `process.stdout.write`).
- CFG arbitrário via **máquina de estados** `let pc; while (true) { switch (pc) { … } }` (JS não tem goto).
- `Jump` → `pc = N; break;`; `JumpIfFalse` → `if (!stack.pop()) { pc = N; break; }`.
- Bool em JS é `1`/`0` (mesmo domínio do JVM `int`); comparações `===`/`!==`.
- try/catch: **v2** — ops Try* são ignorados com nota (JVM cobre o caminho de erros no subset).
- LoadField/StoreField/NewObject: erro honesto (mesmo limite do JVM v1).
- Entry: `if (typeof require !== 'undefined' && require.main === module) { main(); }`.
- `Compiler.compileToJs(source, outDir)` → `outDir/main.js`.

## Native (BLQ-02)

- **Não implementado** nesta estação: host macOS aarch64 ≠ ELF GNU; baseline do Kof já falha no mesmo motivo.
- Não prometer alvo nativo até Linux dedicado. JS cobre o alvo interpretado.
