# STATE — Hydra

**Última atualização:** 2026-10-02 (Ciclo 11 — F3-03/F3-04 JS + paridade)
**Fase atual:** 3 fechada (IR + JVM + JS + paridade); próximo = Fase 4 (migração) ou backend JVM v2
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fase 2 fechada; **F3-01..F3-04 fechados** (F3 com subset hello+funções+for+string).
- `compiler/`: **84/84 testes verdes** (10 IR + 7 JVM E2E + 6 JS E2E + 6 parity alvos + 12 lexer + 21 parser + 9 parity AST + 13 diagnostics).
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer: 20 keywords; diagnósticos HYP001..013.
- Parser: unit, import, fun, type, enum, stmts, exprs, lambda, match.
- Paridade AST (`hydra/PARITY.md`).
- IR (`hydra/IR.md`): shape Kof-like; `IrBuilder` lowera AST→IR.
- **JVM E2E**: fonte `.hy` → `.class` → executa (`Compiler.compileTo` + `JvmBackend`).
- **JS E2E**: fonte `.hy` → `main.js` → `node main.js` (`Compiler.compileToJs` + `JsBackend`).
- **Paridade de alvos**: `TargetParityTest` — mesma fonte, mesma saída JVM e JS.

## Pipeline atual

```
.hy → Lexer → Parser → Ast.Unit → IrBuilder → Ir.Module
  ├─ JvmBackend → Main.class → java Main
  └─ JsBackend  → main.js   → node main.js
```

## Limites v1 (honestos)

- Sem objetos/`type` em runtime (LoadField/NewObject → erro nos dois backends).
- Sem enum como classe (LoadEnum vira string `"Color.Red"`).
- Lambda: IR rejeita (v2).
- println(Int/Float) usa scratch/conv no JVM; println multi-arg: v2.
- **try/catch só no JVM** — JS na v2 (`JsBackend` ignora TryStart/TryEnd/CatchStart).
- **Native: BLQ-02** (macOS aarch64 ≠ GNU ELF) — não implementado nesta estação.

## Próxima ação

1. **Fase 4**: ferramenta de migração Kof→Hydra (`hydra migrate`) — compat é tooling, nunca gramática (D-HYD-001).
2. **Backend JVM v2**: `type`/enum em runtime + lambda no IR.
3. **JsBackend v2**: try/catch; objetos quando IR os liberar.
4. Native só em Linux dedicado (não prometer aqui).

## Comandos

```bash
cd compiler && mvn -B test
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
```

## Histórico

- Ciclo 1–3: reconhecimento, skeleton, spec.
- Ciclo 4: Fase 1 congelada + lexer.
- Ciclo 5: parser núcleo.
- Ciclo 6: enum/destructuring/função-tipo.
- Ciclo 7: F2-04 paridade + CI Kof.
- Ciclo 8: F2-03 diagnósticos + Fase 2 fechada.
- Ciclo 9: F3-01 IR + `IrBuilder`.
- Ciclo 10: F3-02 `JvmBackend` + E2E 7/7.
- Ciclo 11: F3-03 `JsBackend` + F3-04 paridade de alvos + BLQ-02 Native.
