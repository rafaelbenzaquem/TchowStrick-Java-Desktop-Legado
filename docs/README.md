# Documentação — TchowStrick Java Desktop (legado)

Organizada conforme o [padrão de documentação MSS](../../docs/padroes/documentacao.md). Fluxo de desenvolvimento: [regras comuns](../../AGENTS.md) (fluxo solo de 30/09/2026).

## Ordem de leitura

1. [Regras locais](../AGENTS.md).
2. [ROADMAP](ROADMAP.md) e marcos em [`marcos/`](marcos/) (status no front matter).
3. [Compatibilidade com o TchowStrick](compatibilidade.md): artefatos consumidos, versão e revisão de origem.
4. [Operação local e arquitetura](operacao/local.md): build, execução, conexão e camadas.
5. Validação: roteiro histórico [E4d-04](validacao/roteiros/E4d-04-estatisticas-desktop.md). Regressões manuais do sistema (`RM-01`…`RM-10`, que usam este cliente) continuam no [TchowStrick](../../TchowStrick/docs/validacao/regressao/).
6. [CHANGELOG](../CHANGELOG.md) e [histórico](historico/README.md).

Decisões de arquitetura, contratos (SEG-01/02, SEG-03/OPS-01) e histórico de itens do cliente até 04/10/2026 (`E*`, M0–M5 do TchowStrick) ficam no [índice do TchowStrick](../../TchowStrick/docs/README.md).

## Dicionário de IDs e caminhos legados

| Legado | Atual | Observação |
|---|---|---|
| `TchowStrick/client-desktop/` | raiz deste repositório | Movido em 04/10/2026 (TchowStrick:ADR-0020); pacote `br.com.mss.tchow` e jar `tchowstrick.jar` mantidos |
| `./mvnw -pl client-desktop …` (no TchowStrick) | `./mvnw …` (aqui) | Após `./mvnw install` no TchowStrick |
| `TchowStrick/docs/validacao/roteiros/E4d-04-estatisticas-desktop.md` | [validacao/roteiros/](validacao/roteiros/E4d-04-estatisticas-desktop.md) | Stub de redirecionamento na origem |
| `TchowStrick/docs/historico/conexao-desktop-anterior.md` | [historico/](historico/conexao-desktop-anterior.md) | idem |
| Seções do cliente em `TchowStrick/docs/operacao/local.md` | [operacao/local.md](operacao/local.md) | Servidor continua lá |
| IDs `E*`, `M0`–`M7`, `SEG-*`, `RM-*`, `ADR-0001`–`ADR-0020` | Mantidos | Pertencem ao TchowStrick; citar como `TchowStrick:ID` quando ambíguo |
| MSSIdentity:M4-04 (`client-desktop` com `identity-client-java`) | [M1](marcos/M01-identidade-mss.md) | Status continua no M4 do MSSIdentity |
| `M0` deste repositório | [Extração do TchowStrick](marcos/M00-extracao-do-tchowstrick.md) | Numeração própria a partir de 04/10/2026 |
