# LOG — Hydra

Diário cronológico. Uma linha por ciclo. Métricas a cada ~10/30 ciclos.

---

## 2026-10-01

- **Ciclo 1** | Fase 0 | Feito: clone `KofLang/Kof4j` → `kof_upstream/`; remotos; docs lidos; auditoria inicial; memória. | Verificado: JDK 25 + Maven; gh OK; build core Kof OK; shade `kof-cli` falha (BLQ-01). | Decisão: D-HYD-001..010. | Próximo: baseline; skeleton; push.
- **Ciclo 2** | Fase 0 | Feito: baseline medida; skeleton (LICENSE/NOTICE/README/CI); auditoria §15; commit `4123122` push. | Verificado: 808/155F/8E/26S focado; causa dominante Native macOS. | Decisão: baseline honesta no STATE. | Próximo: CI no GitHub; fechar auditoria; ESPECIFICACAO.
- **Ciclo 3** | Fase 0→1 | Feito: fix CI (clone Kof antes de setup-java; cache no pom do upstream; actions v5) `e0b3dce`; `ESPECIFICACAO.md` rascunho 0.1; **40** exemplos `.hy`; §17 da auditoria fechado (keywords 62; sem interpolação); D-HYD-011..013; CI **verde** (`36952608349`). | Verificado: `gh run watch` → structure ✓ + upstream-compile ✓ (1m30s no ubuntu). EBNF não-vazio ≈71 linhas. | Decisão: conjunto alvo de 20 keywords; EBNF normativa no rascunho; strings sem interpolação. | Próximo: relitura crítica dos exemplos; checklist orçamento F1-04; congelar Fase 1; só então lexer (Fase 2).
- **Ciclo 4** | Fase 1→2 | Feito: F1-04 aprovado; Fase 1 congelada; D-HYD-014..018 (sem `this`, contextuais, sem wildcard, `as` morto, sem `finally`); projeto `compiler/` Maven `hydra-compiler`; **lexer** com 20 keywords + operadores + strings; **12 testes verdes** (`mvn test`); CI job `hydra-compiler`. | Verificado: `mvn -B test` em `compiler/` → BUILD SUCCESS, 12/12. | Decisão: conjunto de 20 congelado com `extends`; Fase 1 congelada. | Próximo: parser (F2-02) a partir da EBNF congelada.
- **Ciclo 5** | Fase 2 | Feito: parser recursivo (AST + SyntaxError com linha/coluna); 29 testes verdes (12 lexer + 17 parser); EBNF corrigido (campo `nome: Tipo`, classic-for com vírgulas, `extends` após header, `case`/`default`/`in` contextuais); D-HYD-019..020; exemplos alinhados. | Verificado: `mvn -B test` em `compiler/` → **29/29** BUILD SUCCESS. | Decisão: uma forma de campo (`nome: Tipo`); for clássico `(var i = 0, cond, update)`. | Próximo: enum no parser, mais bordas de erro, CI verde no job hydra-compiler.
- **Ciclo 6** | Fase 2 | Feito: **enum** (`EnumDecl` + parse + match `Color.Red`); destructuring `Point x y`; função-tipo `(Int) -> Int` no `parseTypeRef`; método abstrato (assinatura sem corpo); testes expandidos para 34 exemplos `.hy`; **33/33 verdes**. | Verificado: `mvn -B test` em `compiler/` → BUILD SUCCESS, 33/33 (12 lexer + 21 parser). | Decisão: enum com casos separados por newline (sem vírgulas); abstrato permitido só em type-decl. | Próximo: F2-04 paridade AST com Kof; F2-03 bordas de erro; commit+push+CI.
- **Ciclo 7** | Fase 2 | Feito: **F2-04 fechado** — `ParityTest` 9/9 (hello, val-var, funcoes, erros, enum, type-membros, lambdas + parse both + functionShape); `hydra/PARITY.md`; dep test `kof-compiler:0.5.0-beta`; CI `hydra-compiler` instala `kof-parent` + módulos antes de `mvn test`; **42/42 verdes**; CI verde run `36957628100`. | Verificado: `mvn -B test` → 42/42; `gh run watch` → structure ✓ + upstream-compile ✓ + hydra-compiler ✓. | Decisão: normalizações de shape (call sem recv, ret void/?, catch só nome); type/enum sem full-shape. | Próximo: F2-03 bordas de erro; fechar Fase 2; Fase 3 IR.
- **Ciclo 8** | Fase 2→3 | Feito: **F2-03 fechado** — `SyntaxError` com `code`/`suggestion`/contexto de linha; `hintExpect` + erros HYP001..013; lexer rejeita `;` com sugestão; `catch (String e)` rejeitado; `fun`/`class` rejeitados no topo; `DiagnosticsTest` 13/13; **55/55 verdes**; **Fase 2 fechada**. | Verificado: `mvn -B test` → BUILD SUCCESS 55/55; CI run `36958202456` verde. | Decisão: diagnóstico com código estável HYP0xx para tooling futuro. | Próximo: F3-01 IR a partir da AST Hydra.
- **Ciclo 9** | Fase 3 | Feito: **F3-01 fechado** — IR `hydra.compiler.ir.{Ir,IrBuilder}` (shape Kof-like); `IrTest` 10/10; `hydra/IR.md`; **D-HYD-021**; **65/65 verdes**. | Verificado: `mvn -B test` → 65/65; CI run `36959453195` verde. | Decisão: IR com tipos canônicos string; lambda na v2; campo sem `this` → LoadField. | Próximo: F3-02 backend JVM mínimo.
- **Ciclo 10** | Fase 3 | Feito: **F3-02 fechado** — `JvmBackend` (ASM) + `Compiler`; E2E 7/7 bytecode real. | Verificado: `mvn -B test` → 72/72; CI `36960446128` verde. | Decisão: `Binary.operandType` = operandos; try-catch ASM; println(Int) via scratch. | Próximo: F3-03 JS/Native.
- **Ciclo 11** | Fase 3 | Feito: **F3-03+F3-04 fechados** — `JsBackend` (switch(pc), sem goto); E2E Node (hello/arith/fun/for/string); `TargetParityTest` JVM≡JS; Native documentado **BLQ-02** (macOS); **84/84**; commit `a528934`; CI `36961027928` verde. | Verificado: `mvn -B test` → BUILD SUCCESS **84/84**; `gh run watch` → structure ✓ + upstream-compile ✓ + hydra-compiler ✓. | Decisão: **D-HYD-022** máquina de estados JS; **D-HYD-023** Native só em Linux; try/catch JS na v2. | Próximo: Fase 4 (migração Kof→Hydra) ou backend JVM v2 (type/enum/lambda).
- **Ciclo 12** | Fase 4 | Feito: **F4-01+F4-02 fechados** — `Migrator` (AST Kof→Hydra + gate parse + MIG0xx); `MigrateTest` 15/15; `MigrateCorpusTest` 5/5 sobre `training/examples` + golden; Kof em compile scope; **104/104**; commit `35f18f8`; CI `37017668287` verde. | Verificado: `mvn -B test` → BUILD SUCCESS **104/104**; `gh run watch` → structure ✓ + upstream-compile ✓ + hydra-compiler ✓. | Decisão: **D-HYD-024** AST-walk + gate + diagnósticos honestos; compat só em tooling. | Próximo: Fase 5 stdlib ou F6 ferramentas (`fmt`/`run`/CLI migrate).
- **Ciclo 13** | Fase 6 | Feito: **F6-01 parcial fechado** — CLI `hydra check|run|migrate|version` (`Cli.java` + `bin/hydra`); `CliTest` 10/10; jar Main-Class; **114/114**; commit `1617e5f`; CI `37018674322` verde. | Verificado: `mvn -B test` → BUILD SUCCESS **114/114**; `gh run watch` → structure ✓ + upstream-compile ✓ + hydra-compiler ✓. | Decisão: **D-HYD-025** CLI só com o que o pipeline prova; fmt/new/test deferred (F6-01b). | Próximo: F6-01b (`fmt` reprint canônico) ou F5 stdlib.
- **Ciclo 14** | Fase 6 | Feito: **F6-01b fechado** — `Formatter` reprint canônico da AST + `hydra fmt` (D-HYD-026); `FormatterTest` 57/57; CLI fmt em `CliTest` 15/15; **176/176**; commit `cfe31bc`; CI `37021269907` verde. | Verificado: `mvn -B test` → BUILD SUCCESS **176/176**; `fmt(fmt(x))==fmt(x)` no corpus; `//` preservado por linha. | Decisão: **D-HYD-026** fmt = AST-printer com comentários por linha; `for x in` sem val. | Próximo: F6-01b restante (`new`/`test`) ou F5 stdlib.
- **Ciclo 15** | Fase 6 | Feito: **F6-01b completo** — `assert` vira op de IR; throw/catch String em JVM/JS (bug real de pareamento try/catch do ASM corrigido); concat universal via `ToString`; paridade bool/float; `TestRunner` (harness na AST) + `hydra test <arquivo\|dir> [--js]` + `hydra new`; **198/198**; **Fase 6 fechada**. | Verificado: `mvn -B test` → BUILD SUCCESS **198/198**; smoke `bin/hydra new` + `run` + `test` (JVM e JS) com PASS/FAIL e exit code corretos. | Decisão: **D-HYD-027** assert no IR, erro String fim-a-fim, pareamento de regiões try por fila. | Próximo: Fase 5 stdlib com orçamento.
- **Ciclo 16** | Fase 3/6 | Feito: **varredura de paridade do corpus expôs bugs silenciosos** — `match` ignorava literais/tipos/guardas/bindings; `Pop` de long/double emitia `POP` (1 slot) → `VerifyError`; match-expr usava temp `Any`; sujeito reavaliado por braço; retorno de cauda sem anotação virava void; enum em assinatura virava objeto. Corrigidos no IR (D-HYD-028); corpus **36/39 JVM≡JS** (3 restantes = stdlib v1, erro honesto); **205/205**. | Verificado: `mvn -B test` → BUILD SUCCESS **205/205**; script de paridade por exemplo (diff de stdout+exit JVM vs JS). | Decisão: **D-HYD-028** match com blocos de teste dedicados, enum=String no runtime, `Pop(type)`, retorno de cauda inferido. | Próximo: Fase 5 stdlib (`listOf`/`setOf`/`mapOf`/`readLine`).

### Métricas

| Métrica | Valor |
|---|---|
| Keywords/token de palavra Kof | **62** (medido em `TokenType.java`) + 5 contextuais |
| Meta keywords Hydra | ≤20 (conjunto alvo em ESPECIFICACAO §2) |
| Linhas EBNF (não vazias, rascunho) | **~71** (meta ≤120) |
| Formas de repetir (Kof→Hydra) | 4 → 1 (`for` ×3 cabeçalhos) |
| Formas de ramificar | 4 → 2 (`if` + `match`) |
| Comentários | 2 → 1 (`//`) |
| Exemplos `.hy` | **40** (34 parseados; fmt corpus 39 parseáveis) |
| Baseline testes (foco, macOS) | 808 / 155F / 8E / 26S |
| Baseline Native (macOS) | inexecutável (as Apple ≠ GNU ELF) |
| CI Hydra | **VERDE** — structure + upstream-compile + hydra-compiler |
| Testes hydra-compiler | **205/205** (15 IR + 19 JVM + 10 JS + 6 parity alvos + 24 CLI + 57 fmt + 15 migrate + 5 corpus + 12 lexer + 21 parser + 9 parity AST + 13 diagnostics) |
| Paridade de corpus | **36/39** exemplos JVM≡JS (3 = stdlib v1: listOf/setOf/readLine) |
| Paridade AST | **9/9** shapes hello/val-var/funcoes/erros |
| Paridade de alvos | **6/6** JVM≡JS (hello/arith/fun/for/string/jvm-only) |
| Migração Kof | **15+5** testes (unit + corpus training/golden) |
| Formatter | **57** testes (idempotência + comentários + corpus) |
| Códigos de erro | **HYP000–HYP013** · **MIG001–MIG015** |
| Commits | `4123122` · `e0b3dce` · `3dd5a68` · `7dcac62` · `b905c21` · `c831d37` · `973e3f6` · `f1766b2` · `6c4d58e` · `a1aae9a` · `1082bff` · `b835f35` · `ff95ad5` · `1653ccf` · `4a377de` · `a528934` · `f2fef2b` · `35f18f8` · `6f05c4f` · `1617e5f` · `fa35a85` · `cfe31bc` |
| CI runs verdes (Fase 6) | `37018674322` (F6-01) · `37021269907` (F6-01b) |

## Limites v1 (honestos)

- Sem objetos/`type` em runtime; enum vira string; lambda: IR rejeita (v2).
- try/catch JS na v2; Native: **BLQ-02** (macOS).
- Migrator: finally/do-while/break/implements/generics/arrays → MIG0xx.
- CLI: `fmt` pronto; `new`/`test` deferred; migração não preserva comentários Kof.

## Próxima ação

1. **F6-01b restante**: `hydra new` + `hydra test` (runner de `test "…" { }` com `assert`).
2. **Fase 5**: stdlib com orçamento (uma API por conceito; gap codes por alvo).
3. Backend JVM v2 quando a stdlib pedir `type`/enum em runtime.

## Comandos

```bash
cd compiler && mvn -B test
(cd compiler && mvn -B -DskipTests package) && bin/hydra check hydra/exemplos/01-hello.hy
bin/hydra fmt hydra/exemplos/01-hello.hy
gh run list --repo JoaoGaValentim/hydra-lang --limit 5
```

## Histórico

- Ciclo 1–3: reconhecimento, skeleton, spec.
- Ciclo 4: Fase 1 congelada + lexer.
- Ciclo 5–8: parser, enum, paridade, diagnósticos (Fase 2 fechada).
- Ciclo 9–10: IR + JVM E2E.
- Ciclo 11: JS backend + paridade de alvos (Fase 3 fechada).
- Ciclo 12: **F4-01/F4-02 migrate + corpus** (Fase 4 fechada no subset).
- Ciclo 13: **F6-01 CLI check/run/migrate**.
- Ciclo 14: **F6-01b fmt canônico + preservação de //.**
- Ciclo 15: **F6-01b new/test + assert no IR + paridade try/catch (Fase 6 fechada).**
- Ciclo 16: **match/enum/POP tipados + retorno de cauda (D-HYD-028); corpus JVM≡JS 36/39.**
 |
