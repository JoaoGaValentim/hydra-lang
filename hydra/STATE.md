# STATE — Hydra

**Última atualização:** 2026-10-02 (Ciclo 9 — F3-01 IR)
**Fase atual:** 3 (IR feito; próximo = backend JVM)
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fase 2 fechada; **F3-01 IR fechado**.
- `compiler/`: **65/65 testes verdes** (10 IR + 12 lexer + 21 parser + 9 parity + 13 diagnostics).
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer: 20 keywords; erros honestos com sugestão (HYP010).
- Parser: unit, import, fun, type, enum, stmts, exprs, lambda, match; diagnósticos HYP001..013.
- Paridade: shapes Hydra ↔ Kof (`hydra/PARITY.md`).
- **IR**: `Ir.Module/Class/Method/Block/Op`; `IrBuilder` lowera AST→IR; contrato em `hydra/IR.md`.

## IR (F3-01)

- Shape Kof-like; tipos canônicos: `Int/Float/String/Bool/Void/Any`.
- Lowering cobre: funções, val/var, if, for (3 heads), match, try/catch/throw, enum, type-decl, calls, assign.
- Campo sem `this` → `LoadField` (D-HYD-014); enum → `LoadEnum`.
- Lambda e compound-assign em campo: **v2** (erros honestos).

## Orçamento (F1-04)

20 keywords · EBNF ~71 · 40 exemplos · testes 65/65.

## Próxima ação

1. **F3-02**: backend JVM mínimo a partir de `Ir.Module` (hello world ponta a ponta).
2. Depois F3-03 (Native/JS) e F3-04 (paridade de alvos).

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
- Ciclo 9: F3-01 IR + `IrBuilder` + `IrTest`.
