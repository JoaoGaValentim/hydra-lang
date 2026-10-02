# LOG — Hydra

Diário cronológico. Uma linha por ciclo. Métricas da seção 8 a cada ~10/30 ciclos.

---

## 2026-10-01

- **Ciclo 1** | Fase 0 | Feito: clone `KofLang/Kof4j` → `kof_upstream/`; remotos `origin`/`upstream`; leitura de AGENTS.md, PHILOSOPHY.md, docs/philosophy.md, language-reference (grammar, syntax, statements, expressions, functions, classes, closures, modules, lexical-structure); auditoria inicial `hydra/AUDITORIA-SINTAXE.md`; `STATE.md`, `BACKLOG.md`, `DECISOES.md`, `LOG.md`. | Verificado: JDK 25 + Maven 3.10 no host; `gh auth` OK como JoaoGaValentim; build dos módulos core do Kof OK; shade de `kof-cli` falha com `${revision}` (BLQ-01); `mvn test` em background — **sem número final ainda**. | Decisão: D-HYD-001…010 registradas (compat, IR, qualidade, .hy prévia, newline, comentários, hipóteses for/match, erros, null/conc.). | Próximo: fechar baseline de testes; ler frontend/backend; skeleton do repo + push.
- **Ciclo 2** | Fase 0 | Feito: baseline medido; esqueleto repo (LICENSE GPL-3.0, NOTICE Hydra←Kof, README, .gitignore, `.github/workflows/ci.yml`); auditoria §15 com `TokenType.java` (~64 word tokens); `infra` contextual descoberto no `Parser.java`; leitura de `Parser`/`TypeDeclarations`/`StatementParser`/`TokenType`/`compiler-architecture`; CI com job `structure` + `upstream-compile`. | Verificado: subconjunto focado `mvn test -pl kof-compiler -Dtest=…` → **808 testes / 155F / 8E / 26S**; causa dominante = assembly Native no macOS (`as` Apple vs GNU ELF); `kof-cli` shade ainda quebra com `${revision}`; módulos core instalam. | Decisão: baseline honesta registrada no STATE (não inventar 3000+); CI inicial só valida estrutura + compila upstream (sem esconder falha com continue-on-error). | Próximo: commit+push do esqueleto; fechar pendências da auditoria; iniciar `ESPECIFICACAO.md`.

### Métricas (Fase 0 — medidas 2026-10-01)

| Métrica | Valor |
|---|---|
| Keywords/token de palavra Kof (`TokenType`+docs) | ~64 + contextuais |
| Meta keywords Hydra | ≤20 |
| Formas de repetir (Kof) | 4 → meta 1 |
| Formas de ramificar (Kof) | 4 → meta 2 |
| Comentários (Kof) | 2 → meta 1 |
| Baseline testes (foco, host macOS) | 808 run / 155 F / 8 E / 26 S |
| Baseline Native (macOS) | **inexecutável** (as Apple ≠ GNU ELF) |
| Baseline suíte cheia upstream | **não medida** neste host |
| CI Hydra | workflow criado; validar no push |
