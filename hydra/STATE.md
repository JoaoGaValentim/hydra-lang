# STATE — Hydra

**Última atualização:** 2026-10-02 (Ciclo 10 — F3-02 JVM E2E)
**Fase atual:** 3 (JVM mínimo ponta a ponta; próximo = Native/JS ou paridade)
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fase 2 fechada; **F3-01 IR** e **F3-02 JVM E2E** fechados.
- `compiler/`: **72/72 testes verdes** (10 IR + 7 JVM E2E + 12 lexer + 21 parser + 9 parity + 13 diagnostics).
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer: 20 keywords; diagnósticos HYP001..013.
- Parser: unit, import, fun, type, enum, stmts, exprs, lambda, match.
- Paridade AST (`hydra/PARITY.md`).
- IR (`hydra/IR.md`): shape Kof-like; `IrBuilder` lowera AST→IR.
- **JVM E2E**: fonte `.hy` → `.class` → executa de verdade (`Compiler.compileTo` + `JvmBackend`).

## Pipeline atual

```
.hy → Lexer → Parser → Ast.Unit → IrBuilder → Ir.Module → JvmBackend → Main.class → java Main
```

## Limites v1 (honestos)

- Sem objetos/`type` em runtime JVM (LoadField/NewObject → erro).
- Sem enum como classe JVM (LoadEnum vira string `"Color.Red"`).
- Lambda: IR rejeita (v2).
- println(Int/Float) usa scratch local; println multi-arg: v2.
- Native/JS: não implementados (F3-03).

## Próxima ação

1. **F3-03**: Native e/ou JS para o mesmo programa (subset hello).
2. **F3-04**: suíte de paridade entre alvos.
3. Ou: suporte a `type`/enum no backend JVM (v2 do backend).

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
