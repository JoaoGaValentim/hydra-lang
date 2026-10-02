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
| `ToString(type)` | valor → String, para concat/throw (D-HYD-027) |
| `Throw` | desempilha erro (String) e lança |
| `Assert(message)` | desempilha Bool; se falso, lança erro com mensagem |
| `Pop(type)` | descarta o topo (1 ou 2 slots) |
| `TryStart/TryEnd/CatchStart` | marcadores de região try |

## Lowering (IrBuilder)

- Funções top-level → classe sintética `Main`.
- `type` → `Ir.Class` com fields/métodos; `enum` → casos como metadado
  (`enumCases`) e **String no runtime** — `LoadEnum` vira `"Tipo.Caso"`
  (D-HYD-028).
- `if`/`for`/`match`/`try` → basic blocks + `Jump`/`JumpIfFalse`; cada teste de
  `case` é um bloco dedicado sobre um temp do sujeito (literal, enum, `null`,
  tipo→`!= null`); guards/bindings no bloco do braço (D-HYD-028).
- `assert(cond[, "msg"])` no topo de um statement → `Assert` (não vira Call).
- Concat com qualquer operando não-String → `ToString` antes do `Binary(+)`.
- `throw valor` → `ToString` se preciso, depois `Throw` (erro é String).
- Retorno de cauda: última expressão de função não-void vira `Return`
  (retorno implícito do Kof preservado); tipo inferido quando não anotado.
- Identificador não-local que casa com campo do tipo atual → `LoadField` (sem receiver).
- `Color.Red` → `LoadEnum(Color, Red)`.
- Lambda: **na v2** (F3-02) — erro honesto na v1.
- Aritidade de chamada conferida no IR (diagnóstico `IR: aridade…`).
- Nomes de método passam por `Ir.safeName` no backend (`test "x y"` → `test_x_y`).

## Testes

`IrTest` cobre: hello, val/var/assign, funções, if+throw, try/catch, enum+match,
type-decl, aridade, for clássico, imports, assert (3 casos), concat→ToString,
throw→ToString. Shape canônico via `Ir.shape(module)`; E2E de match/guardas/
destructuring/retorno de cauda em JVM e JS.

## Status

| Item | Estado |
|---|---|
| F3-01 IR + lowering v1 | **feito** |
| F3-02 backend JVM mínimo | **feito** — E2E (`JvmBackendE2ETest`) |
| F3-03 backend JS | **feito** — E2E Node (`JsBackendE2ETest`); Native **BLQ-02** |
| F3-04 paridade de alvos | **feito** — `TargetParityTest` JVM≡JS |
| F6-01b `assert`/throw/catch String | **feito** — D-HYD-027 |

## Backend JVM (F3-02)

- ASM 9.7.1; classes `Main` + `<init>` + métodos estáticos.
- `Int`→`long`, `Float`→`double`, `Bool`→`int`, `String`→`String`.
- `println`/`print` → `System.out` (Int/Float via scratch local).
- `+` de String → `String.concat`; `==`/`!=` de String → `String.equals`.
- `Throw` → `RuntimeException(msg)`; `catch (e)` → `e.getMessage()` (String).
- try/catch → `visitTryCatchBlock(Throwable)`; pareamento de regiões por fila
  (handler do try#1 pode estar depois do try#2 no IR — D-HYD-027).
- `Assert` → se falso, `Throwable(msg)`.
- `Binary.operandType` = tipo dos operandos (comparações em Int usam `LCMP`).

## Backend JS (F3-03)

- Mesmo IR; emite `main.js` com `'use strict'` + shim `println`/`print` (`console.log` / `process.stdout.write`).
- CFG arbitrário via **máquina de estados** `let pc; while (true) { switch (pc) { … } }` (JS não tem goto).
- `Jump` → `pc = N; break;`; `JumpIfFalse` → `if (!stack.pop()) { pc = N; break; }`.
- Bool em JS é `1`/`0` (mesmo domínio do JVM `int`); comparações `===`/`!==`;
  `println` de Bool/Float usa `_b`/`_strF` para paridade com o JVM; locals
  duplicados (`catch (e)` repetido) viram `e`, `e_2`….
- try/catch: cada bloco dentro da região vira `try { … } catch { pc = handler }`;
  `CatchStart` lê `_err`.
- LoadField/StoreField/NewObject: erro honesto (mesmo limite do JVM v1).
- Entry: `if (typeof require !== 'undefined' && require.main === module) { main(); }`;
  modo teste chama `main()` (harness sintetizado) direto.
- `Compiler.compileToJs(source, outDir)` → `outDir/main.js`.

## Native (BLQ-02)

- **Não implementado** nesta estação: host macOS aarch64 ≠ ELF GNU; baseline do Kof já falha no mesmo motivo.
- Não prometer alvo nativo até Linux dedicado. JS cobre o alvo interpretado.
