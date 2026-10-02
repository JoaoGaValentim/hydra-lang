# LOG — Hydra

Diário cronológico. Uma linha por ciclo. Métricas a cada ~10/30 ciclos.

---

## 2026-10-01

- **Ciclo 1** | Fase 0 | Feito: clone `KofLang/Kof4j` → `kof_upstream/`; remotos; docs lidos; auditoria inicial; memória. | Verificado: JDK 25 + Maven; gh OK; build core Kof OK; shade `kof-cli` falha (BLQ-01). | Decisão: D-HYD-001..010. | Próximo: baseline; skeleton; push.
- **Ciclo 2** | Fase 0 | Feito: baseline medida; skeleton (LICENSE/NOTICE/README/CI); auditoria §15; commit `4123122` push. | Verificado: 808/155F/8E/26S focado; causa dominante Native macOS. | Decisão: baseline honesta no STATE. | Próximo: CI no GitHub; fechar auditoria; ESPECIFICACAO.
- **Ciclo 3** | Fase 0→1 | Feito: fix CI (clone Kof antes de setup-java; cache no pom do upstream; actions v5) `e0b3dce`; `ESPECIFICACAO.md` rascunho 0.1; **40** exemplos `.hy`; §17 da auditoria fechado (keywords 62; sem interpolação); D-HYD-011..013; CI **verde** (`36952608349`). | Verificado: `gh run watch` → structure ✓ + upstream-compile ✓ (1m30s no ubuntu). EBNF não-vazio ≈71 linhas. | Decisão: conjunto alvo de 20 keywords; EBNF normativa no rascunho; strings sem interpolação. | Próximo: relitura crítica dos exemplos; checklist orçamento F1-04; congelar Fase 1; só então lexer (Fase 2).

### Métricas

| Métrica | Valor |
|---|---|
| Keywords/token de palavra Kof | **62** (medido em `TokenType.java`) + 5 contextuais |
| Meta keywords Hydra | ≤20 (conjunto alvo em ESPECIFICACAO §2) |
| Linhas EBNF (não vazias, rascunho) | **~71** (meta ≤120) |
| Formas de repetir (Kof→Hydra) | 4 → 1 (`for` ×3 cabeçalhos) |
| Formas de ramificar | 4 → 2 (`if` + `match`) |
| Comentários | 2 → 1 (`//`) |
| Exemplos `.hy` | **40** |
| Baseline testes (foco, macOS) | 808 / 155F / 8E / 26S |
| Baseline Native (macOS) | inexecutável (as Apple ≠ GNU ELF) |
| CI Hydra | **VERDE** — structure + upstream-compile |
| Commits | `4123122` (root) · `e0b3dce` (CI fix) |
