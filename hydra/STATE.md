# STATE — Hydra

**Última atualização:** 2026-10-02 (Ciclo 8 — Fase 2 fechada)
**Fase atual:** 2 → 3 (frontend completo; próximo = IR)
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- **Fase 2 fechada**: lexer + parser + paridade + diagnósticos.
- `compiler/`: **55/55 testes verdes** (12 lexer + 21 parser + 9 parity + 13 diagnostics).
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer: 20 keywords; erros honestos (`;`, string, escape) com sugestão.
- Parser: unit, import, fun, type, enum, stmts, exprs, lambda, match.
- Paridade: shapes Hydra ↔ Kof (`hydra/PARITY.md`).
- Diagnósticos: `SyntaxError` com `code` (HYP00x), `suggestion`, contexto de linha com `^`.

## Códigos de erro (F2-03)

| Code | Caso |
|---|---|
| HYP000 | genérico |
| HYP001 | `expect` falhou (com hint contextual) |
| HYP002 | decl inválida no topo |
| HYP003 | enum sem casos |
| HYP004 | for-in / for clássico confuso |
| HYP005 | padrão `case` inválido |
| HYP006 | `case` fora de `match` |
| HYP007 | expressão inesperada |
| HYP008 | parâmetro sem `:` |
| HYP009 | assinatura sem corpo/`=` |
| HYP010 | erro de lexer |
| HYP011 | `catch (String e)` — tipo proibido |
| HYP012 | `fun`/`fn`/`func` |
| HYP013 | `class`/`record` |

## Orçamento (F1-04)

20 keywords · EBNF ~71 · 40 exemplos · testes 55/55.

## Próxima ação

1. Abrir **Fase 3**: F3-01 IR a partir da AST Hydra (contrato estável; espelhar IR do Kof).
2. Programa mínimo `01-hello` → IR → execução.

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
- Ciclo 8: F2-03 diagnósticos + **Fase 2 fechada**.
