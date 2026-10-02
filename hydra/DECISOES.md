# DECISOES — Hydra

Formato curto:
```
### D-HYD-XXX — título
- Contexto:
- Decisão:
- Alternativas descartadas:
- Consequências:
- Data:
```

---

### D-HYD-001 — Compatibilidade não é meta da gramática
- Contexto: Hydra deriva do Kof; o prompt mestre define orçamento de sintaxe rígido.
- Decisão: o Hydra **não** aceita formas sintáticas antigas do Kof. Compatibilidade é papel da ferramenta de migração (Fase 4), nunca da gramática.
- Alternativas descartadas: modo legacy no parser; aceitar ambos os estilos de `;`; aceitar `while` e `for`.
- Consequências: gramática limpa; usuários Kof migram com `hydra migrate`; testes do Hydra não herdam a suíte do Kof literalmente.
- Data: 2026-10-01

### D-HYD-002 — IR e backends são contrato estável
- Contexto: Kof tem IR de ~30 ops e backends JVM/Native/JS maduros.
- Decisão: o trabalho central do Hydra está no **frontend**. O IR permanece o contrato entre frontend e backends. Mudanças de IR exigem registro aqui e mudança mínima.
- Alternativas descartadas: reescrever IR; gerar Java como intermediário (proibido pelo mestre e pela filosofia Kof).
- Consequências: reutilização máxima de backend; paridade de alvo preservada onde o IR cobre.
- Data: 2026-10-01

### D-HYD-003 — Herança de qualidade do AGENTS.md do Kof
- Contexto: `AGENTS.md` do Kof define Q0–Q7, no-stubs, compile-before-delivery, honest diagnostics, no-silent-fallback.
- Decisão: o Hydra adota a **disciplina de qualidade** (testes, prova, sem stub, sem silêncio de alvo) mas **não** o workflow multi-agent do Kof (claims em DOING.md, sync-push do upstream, PR #619).
- Alternativas descartadas: copiar DOING.md e o ritual de claims; ignorar quality gates.
- Consequências: portões de qualidade da seção 10 do mestre valem; repositório e memória (`hydra/STATE.md`) são do Hydra.
- Data: 2026-10-01

### D-HYD-004 — Extensão de arquivo do Hydra (prévia)
- Contexto: precisa-se de extensão para `.kf` do Kof.
- Decisão (prévia, revisar na Fase 1): extensão **`.hy`** para fontes Hydra.
- Alternativas descartadas: `.kof`, `.hd`, manter `.kf`.
- Consequências: migração renomeia; tooling distingue por extensão.
- Data: 2026-10-01 (prévia — confirmar com exemplos e CLI)

### D-HYD-005 — Fim de instrução: estilo único por newline (prévia)
- Contexto: Kof aceita `;` opcional em todo statement-end.
- Decisão (prévia): no Hydra, **newline termina instrução**; `;` não é parte da gramática (ou é rejeitado). Uma regra em todo lugar.
- Alternativas descartadas: `;` obrigatório (cerimônia); opcional (dois estilos).
- Consequências: parser com regra de quebra de linha documentada e lookahead pequeno; `fmt` pode não precisar inserir `;`.
- Data: 2026-10-01 (prévia — validar com exemplos de blocos, lambdas, chamadas multi-linha)

### D-HYD-006 — Comentários: um estilo
- Contexto: Kof tem `//` e `/* */`.
- Decisão (prévia): **apenas `//`**. Sem bloco.
- Alternativas descartadas: manter os dois; usar só `/* */`.
- Consequências: menos um token de comentário; docs longas em `//` consecutivos.
- Data: 2026-10-01 (prévia)

### D-HYD-007 — Repetição: uma forma `for` (hipótese a validar na Fase 1)
- Contexto: Kof tem while/do-while/for/for-in.
- Decisão (hipótese): Hydra tem **uma** construção de repetição `for` cobrindo contagem, coleção e condição; `while`/`do-while` não existem como palavras separadas.
- Alternativas descartadas: manter `while` por ser familiar; duas formas (for + while).
- Consequências: gramática menor; necessidade de decidir sintaxe de intervalo e de "uma vez" (`do`) dentro do mesmo `for`.
- Data: 2026-10-01 (hipótese — refinar com exemplos)

### D-HYD-008 — Ramificação: `if` sempre expressão + um `match` (hipótese)
- Contexto: Kof tem if-stmt, if-expr, switch-stmt, switch-expr.
- Decisão (hipótese): `if` é sempre expressão (`else` sempre presente). Escolha por padrão é **uma** construção `match` (fusão switch/case) com binding, desestruturação, guardas opcionais e exaustividade de enum.
- Alternativas descartadas: manter switch instrução e expressão; adicionar `when` Kotlin-like ao lado.
- Consequências: duas regras de ramificação no total (if, match); pattern matching concentrado.
- Data: 2026-10-01 (hipótese)

### D-HYD-009 — Erros: único mecanismo (throw String + try/catch)
- Contexto: Kof já não tem Result/Option no core.
- Decisão: manter **um** mecanismo. `catch` aceita **apenas** o tipo de erro da linguagem (String). Rejeitar outros tipos em compilação.
- Alternativas descartadas: introduzir Result; aceitar catch genérico unspecified.
- Consequências: elimina armadilha do `catch (Int)` do Kof; diagnóstico claro.
- Data: 2026-10-01

### D-HYD-010 — Nulabilidade e concorrência preservadas
- Contexto: `T?` + narrowing e `spawn`/`await` são congelados no Kof e úteis.
- Decisão: manter como únicas formas. Sem `?.`/`?:`/`??`. Timeout por função de stdlib.
- Alternativas descartadas: monads de erro/nulidade na gramática.
- Consequências: orçamento preservado; familiaridade Kof mantida onde é bom.
- Data: 2026-10-01

---

*Novas decisões entram aqui no ciclo em que forem tomadas (seção 8, passo 8).*
