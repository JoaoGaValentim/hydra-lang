# STATE — Hydra

**Última atualização:** 2026-10-01 (Fase 1 congelada; Fase 2 lexer verde)
**Fase atual:** 2 — Frontend (parser é o próximo)
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo Hydra: `https://github.com/JoaoGaValentim/hydra-lang` (branch `main`).
- Fase 1 **congelada** (ESPECIFICACAO v0.1 + D-HYD-001..018 + 40 exemplos + F1-04 aprovado).
- Fase 2 **lexer pronto**: `compiler/` Maven `dev.hydra:hydra-compiler`; **12/12 testes verdes**.
- CI: jobs `structure`, `upstream-compile` (Kof), `hydra-compiler` (nossos testes).

## O que funciona

- Build dos módulos core do Kof em `kof_upstream/`.
- Baseline do Kof **medida** no host.
- **Lexer Hydra:** 20 keywords, operadores, strings com escape, comentários `//`, posições 1-based.

## Baseline Kof (medida 2026-10-01, macOS aarch64)

| Item | Resultado real |
|---|---|
| Compilação módulos core | **OK** |
| `kof-cli` shade | **FALHA** — `${revision}` (BLQ-01) |
| Subconjunto focado `mvn test` | **808 / 155F / 8E / 26S** (~3:40) |
| Causa dominante | **Native** macOS (`as` Apple ≠ GNU ELF) |
| Suíte cheia 3000+ | **não medida** neste host |
| CI Hydra | **VERDE** (estrutura + Kof core + hydra-compiler) |

## Orçamento (F1-04) — Fase 1 congelada

| Portão | Meta | Medido | OK |
|---|---|---|---|
| Keywords | ≤20 | **20** (`extends` no lugar de `this`) | sim |
| EBNF (não vazias) | ≤120 | **~71** | sim |
| Formas repetir | 1 | 1 (`for` ×3) | sim |
| Formas ramificar | 2 | 2 (`if`+`match`) | sim |
| Comentários / `;` / `new` | 1 cada | `//` · newline · `X()` | sim |
| Exemplos `.hy` | 30+ | **40** | sim |
| Testes lexer | — | **12/12** | sim |

## Pendências

- **BLQ-01** shade `kof-cli` — não bloqueia Hydra.
- **BLQ-02** nativa no macOS — ambiental.
- **F2-02** parser + AST — próximo grande bloco.
- **F2-03/F2-04** diagnósticos de parse e prova vs exemplos.

## Próxima ação

1. Parser a partir da EBNF congelada (unit, fun-decl, type-decl, stmts, exprs).
2. Testes de parse com linha/coluna.
3. CI verde no `hydra-compiler`; commit+push.

## Comandos

```bash
cd compiler && mvn -B test
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
```

## Armadilhas

1. `${revision}` no Kof; 2. Native macOS ambiental; 3. disciplina do Kof ≠ workflow multi-agent; 4. push só em `origin`; 5. GPL-3.0 + NOTICE.

## Histórico

- Ciclo 1: clone/docs/auditoria.
- Ciclo 2: baseline + skeleton `4123122`.
- Ciclo 3: CI fix `e0b3dce` + ESPECIFICACAO + 40 exemplos.
- Ciclo 4: Fase 1 congelada + lexer 12/12 + job CI `hydra-compiler` (`c831d37`).
