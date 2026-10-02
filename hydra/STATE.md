# STATE — Hydra

**Última atualização:** 2026-10-01 (parser núcleo verde)
**Fase atual:** 2 — Frontend (parser em construção)
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fase 1 congelada; Fase 2: lexer + parser núcleo.
- `compiler/`: **29/29 testes verdes** (lexer 12 + parser 17).
- CI: `structure` + `upstream-compile` + `hydra-compiler`.

## O que funciona

- Lexer: 20 keywords, operadores, strings, `//`, posições.
- Parser: unit, import, fun (bloco e `= expr`), type (campos `nome: Tipo`, methods, extends), stmts (val/var/if/for×3/return/throw/try/match/spawn), exprs (binário, unary, call, field, assign, lambda, if-expr, match-expr).
- Diagnósticos `SyntaxError` com linha/coluna.

## Baseline Kof (medida, macOS)

808/155F/8E/26S focado · Native inexecutável no macOS · shade `kof-cli` quebra (BLQ-01).

## Orçamento (F1-04)

20 keywords · EBNF ~71 · formas 1/2/1 · 40 exemplos · testes 29/29.

## Pendências F2

- `enum` decl + padrões de enum no parser.
- Mais casos de erro (borda, sugestão).
- F2-04: prova exemplos → AST vs Kof.
- Exemplos restantes (05, 06, 09, 15, 18–26, 29, 32, 35–37, 40) ainda não parseados no teste unitário.

## Próxima ação

1. `enum` + match de enum no parser.
2. Adicionar mais exemplos ao teste `parsesAllSimpleExamples`.
3. Commit+push; CI verde.

## Comandos

```bash
cd compiler && mvn -B test
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
```

## Histórico

- Ciclo 1–3: reconhecimento, skeleton, spec rascunho.
- Ciclo 4: Fase 1 congelada + lexer 12/12.
- Ciclo 5: parser núcleo + 29/29 testes + D-HYD-019..020.
