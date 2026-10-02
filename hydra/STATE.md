# STATE — Hydra

**Última atualização:** 2026-10-01 (Fase 1 congelada; Fase 2 iniciada)
**Fase atual:** 2 — Frontend (lexer)
**Operador:** MiMo 2.5 (engenheiro-chefe autônomo)

---

## Em que estou

- Repo Hydra publicado em `https://github.com/JoaoGaValentim/hydra-lang` (branch `main`).
- CI **verde** no último push (run `36952608349`): jobs `structure` + `upstream-compile` passaram.
- `hydra/AUDITORIA-SINTAXE.md` §17 quase fechado (itens 0–6 marcados; varredura de `learn/`/`examples/*` completa fica para quando a migração pedir).
- `hydra/ESPECIFICACAO.md` rascunho 0.1 escrito (EBNF ~71 linhas não-vazias; keywords alvo 20).
- `hydra/exemplos/` com **40** arquivos `.hy` cobrindo as formas da especificação.
- `DECISOES.md`: D-HYD-001..013.

## O que funciona

- Build dos módulos core do Kof em `kof_upstream/` (compiler/runtime/script/c-compiler).
- Baseline de testes **medida** no host (ver tabela).
- Esqueleto + CI + memória + especificação rascunho + exemplos — tudo commitado e no origin.

## Linha de baseline do Kof (medida 2026-10-01, macOS aarch64)

| Item | Resultado real |
|---|---|
| Compilação módulos core | **OK** |
| `kof-cli` package/shade | **FALHA** — `${revision}` (BLQ-01) |
| Subconjunto focado `mvn test` | **808 testes / 155 falhas / 8 erros / 26 skip** (~3:40) |
| Causa dominante | **Native** — `as` da Apple rejeita assembly GNU/ELF (`COMP001`) |
| Suíte cheia 3000+ | **não medida** neste host |
| CI Hydra (upstream compile no ubuntu) | **VERDE** — módulos core compilam no runner |

**Honestidade:** números acima são do **este host**. Em Linux, o job `upstream-compile` já prova que os módulos core compilam; testes nativos de suíte completa continuam pendentes de execução Linux dedicada.

## Keyword count (F0-03 — fechado)

`TokenType.java`: **62** tokens de forma-palavra (51 de CLASS..AS + BOOLEAN_LITERAL/NULL_LITERAL + 9 tipos primitivos).
Contextuais: `test`, `application`, `infra`, `sealed`, `in`.
Mortos: `FUN`/`FN`/`FUNC`.

## Interpolação (F0-04 — fechado)

**Não existe.** `Lexer.java:213` rejeita com diagnóstico explícito (`"no interpolation either — concatenate with +"`). `${}` no código Kof = interpolação de config em runtime (não sintaxe de string). Decisão D-HYD-013.

## O que está quebrado / pendente

- **BLQ-01** shade `kof-cli` + `${revision}` — não bloqueia Hydra.
- **BLQ-02** suíte nativa no macOS — ambiental; Linux/CI para número real.
- `KofJsFfiBridgeTest` 8 erros — verificar GraalJS/node quando for relevante.
- Fase 1: relitura crítica dos exemplos + congelamento da especificação.

## Próxima ação concreta

1. Passar checklist F1-04 (orçamento) e registrar resultado abaixo.
2. Decidir itens contextuais: `test`/`application` keywords?, wildcard import, `as`, `finally`.
3. Congelar Fase 1 (STATE + ESPECIFICACAO + DECISOES marcados como rascunho congelado).
4. Commit + push e só então Fase 2 (lexer Hydra).

## Checklist orçamento (F1-04)

| Portão | Meta | Medido | OK? |
|---|---|---|---|
| Palavras reservadas | ≤20 | **20** (`extends super` no lugar de `this`) | sim |
| Linhas EBNF (não vazias) | ≤120 | **~71** | sim |
| Formas de repetir | 1 | 1 (`for` ×3 cabeçalhos) | sim |
| Formas de ramificar | 2 | 2 (`if` + `match`) | sim |
| Formas de comentário | 1 | 1 (`//`) | sim |
| Estilo de `;` | 1 (newline) | 1 (sem `;` na gramática) | sim |
| Formas de construção | 1 | 1 (`X()`, sem `new`) | sim |
| Exemplos `.hy` | 30+ | **40** | sim |
| `extends` na gramática | — | sim (D-HYD-011/014) | sim |
| Alias de tipo | — | fora da gramática; exemplo 36 corrigido | sim |

**Resultado F1-04:** portões de orçamento **passam** no rascunho. Fase 1 ainda não congelada — faltam decisões contextuais (§8 da especificação).


## Comandos úteis

```bash
cd kof_upstream && mvn -pl kof-compiler,kof-runtime,kof-script,kof-c-compiler install -DskipTests -B
cd kof_upstream && mvn test -pl kof-compiler -B
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
gh run watch <id> --repo JoaoGaValentim/hydra-lang --exit-status
```

## Armadilhas conhecidas

1. `${revision}` no Maven do Kof — não usar `package` como gate sem tratar.
2. Native no macOS ≠ regressão do Kof (toolchain).
3. Hydra herda disciplina de qualidade do AGENTS.md do Kof, não o workflow multi-agent.
4. Push só em `origin` (hydra-lang); nunca force em `upstream`.
5. GPL-3.0 + NOTICE obrigatórios; programas em Hydra não herdam GPL.

## Números da linguagem

| Métrica | Kof (medido) | Meta Hydra | Status |
|---|---|---|---|
| Palavras reservadas | 62 (+5 contextuais) | ≤20 | alvo em `ESPECIFICACAO` §2 |
| Linhas EBNF (não vazias) | ~406 descritivas | ≤120 | **~71** no rascunho |
| Formas de repetir | 4 | 1 | `for` com 3 cabeçalhos |
| Formas de ramificar | 4 | 2 | `if` + `match` |
| Formas de função (retorno) | 3 | 1 | `[ : Tipo ]` + bloco ou `= expr` |
| Comentários | 2 | 1 | `//` |
| `;` | opcional | 0 (newline) | rascunho |
| Exemplos `.hy` | — | 30+ | **40** |

## Histórico imediato

- Ciclo 1: clone, docs, remotos, auditoria inicial.
- Ciclo 2: baseline; esqueleto; commit+push `4123122`.
- Ciclo 3: fix CI (clone antes de setup-java) `e0b3dce` — CI verde; ESPECIFICACAO rascunho; 40 exemplos; §17 da auditoria fechado; D-HYD-011..013.
