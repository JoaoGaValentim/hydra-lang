# STATE — Hydra

**Última atualização:** 2026-10-01 (Ciclo 6 — parser completo + 33/33)
**Fase atual:** 2 — Frontend (parser núcleo fechado)
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fase 1 congelada; Fase 2: lexer + parser núcleo **fechado**.
- `compiler/`: **33/33 testes verdes** (lexer 12 + parser 21).
- CI: `structure` + `upstream-compile` + `hydra-compiler`.
- 34 exemplos `.hy` parseiam no teste `parsesAllSimpleExamples`.

## O que funciona

- Lexer: 20 keywords, operadores, strings, `//`, posições.
- Parser: unit, import, fun (bloco, `= expr`, abstrato), type (campos `nome: Tipo`, methods, extends), **enum** (`enum Color { Red Green Blue }`), stmts (val/var/if/for×3/return/throw/try/match/spawn), exprs (binário, unary, call, field, assign, lambda, if-expr, match-expr), match com guardas e destructuring, função-tipo `(Int) -> Int`.
- Padrões de enum no match: `Color.Red`, literais, bindings `String s`, destructuring `Point x y`.
- Diagnósticos `SyntaxError` com linha/coluna.

## Baseline Kof (medida, macOS)

808/155F/8E/26S focado · Native inexecutável no macOS · shade `kof-cli` quebra (BLQ-01).

## Orçamento (F1-04)

20 keywords · EBNF ~71 · formas 1/2/1 · 40 exemplos · testes 33/33.

## Pendências F2

- F2-03: diagnósticos — casos de borda, sugestão de correção.
- F2-04: prova exemplos → AST vs Kof (paridade de IR).
- Exemplos 20, 21, 24, 25, 26, 30, 31, 37 não estão no teste unitário (parseiam, mas não assertamos).

## Próxima ação

1. F2-04: comparar AST Hydra com AST Kof nos exemplos parseados.
2. F2-03: bordas de erro + sugestões.
3. Commit+push; CI verde.

## Comandos

```bash
cd compiler && mvn -B test
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
gh run watch --repo JoaoGaValentim/hydra-lang <run-id>
```

## Histórico

- Ciclo 1–3: reconhecimento, skeleton, spec rascunho.
- Ciclo 4: Fase 1 congelada + lexer 12/12.
- Ciclo 5: parser núcleo + 29/29 testes + D-HYD-019..020.
- Ciclo 6: enum + destructuring + função-tipo + abstrato; 33/33.
