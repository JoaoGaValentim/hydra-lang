# STATE — Hydra

**Última atualização:** 2026-10-01 (Ciclo 7 — F2-04 paridade fechada)
**Fase atual:** 2 — Frontend (F2-02/F2-04 feitos; F2-03 bordas abertas)
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo: `https://github.com/JoaoGaValentim/hydra-lang`.
- Fase 1 congelada; Fase 2: lexer + parser + **paridade AST com Kof**.
- `compiler/`: **42/42 testes verdes** (lexer 12 + parser 21 + parity 9).
- CI: `structure` + `upstream-compile` + `hydra-compiler` (instala Kof antes dos testes).
- `hydra/PARITY.md`: contrato de equivalência AST Hydra ↔ Kof.

## O que funciona

- Lexer: 20 keywords, operadores, strings, `//`, posições.
- Parser: unit, import, fun (bloco, `= expr`, abstrato), type, enum, stmts, exprs, lambda, match.
- **Paridade**: fontes pareadas hello/val-var/funcoes/erros/enum/type/lambdas — shapes coincidem após normalização sintática documentada.
- Dep test-scoped: `dev.kof:kof-compiler:0.5.0-beta` (instalado local/CI).

## Baseline Kof (medida, macOS)

808/155F/8E/26S focado · Native inexecutável no macOS · shade `kof-cli` quebra (BLQ-01).

## Orçamento (F1-04)

20 keywords · EBNF ~71 · formas 1/2/1 · 40 exemplos · testes 42/42.

## Pendências F2

- F2-03: diagnósticos — bordas + sugestão.
- Exemplos 20, 21, 24, 25, 26, 30, 31, 37 fora do teste unitário (parseiam, sem assert).

## Próxima ação

1. F2-03: bordas de erro + sugestões em SyntaxError.
2. Fechar Fase 2; abrir Fase 3 (IR a partir da AST).

## Comandos

```bash
cd compiler && mvn -B test
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
gh run watch --repo JoaoGaValentim/hydra-lang <run-id>
```

## Histórico

- Ciclo 1–3: reconhecimento, skeleton, spec rascunho.
- Ciclo 4: Fase 1 congelada + lexer 12/12.
- Ciclo 5: parser núcleo + 29/29 + D-HYD-019..020.
- Ciclo 6: enum + destructuring + função-tipo; 33/33.
- Ciclo 7: F2-04 paridade AST (9/9) + PARITY.md + CI com Kof.
