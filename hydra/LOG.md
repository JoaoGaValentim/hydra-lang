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

### Métricas

| Métrica | Valor |
|---|---|
| Keywords/token de palavra Kof | **62** (medido em `TokenType.java`) + 5 contextuais |
| Meta keywords Hydra | ≤20 (conjunto alvo em ESPECIFICACAO §2) |
| Linhas EBNF (não vazias, rascunho) | **~71** (meta ≤120) |
| Formas de repetir (Kof→Hydra) | 4 → 1 (`for` ×3 cabeçalhos) |
| Formas de ramificar | 4 → 2 (`if` + `match`) |
| Comentários | 2 → 1 (`//`) |
| Exemplos `.hy` | **40** (34 parseados no teste) |
| Baseline testes (foco, macOS) | 808 / 155F / 8E / 26S |
| Baseline Native (macOS) | inexecutável (as Apple ≠ GNU ELF) |
| CI Hydra | **VERDE** — structure + upstream-compile + hydra-compiler |
| Testes hydra-compiler | **33/33** (12 lexer + 21 parser) |
| Commits | `4123122` · `e0b3dce` · `3dd5a68` · `7dcac62` · `b905c21` · `c831d37` · `973e3f6` · `f1766b2` |
