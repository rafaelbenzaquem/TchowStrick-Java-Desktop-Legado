# Changelog — TchowStrick Java Desktop (legado)

Mudanças relevantes do produto. Formato: [Keep a Changelog 1.1](https://keepachangelog.com/pt-BR/1.1.0/); versões: [SemVer 2.0](https://semver.org/lang/pt-BR/). Cada entrada cita o marco, item, bug ou ADR. Padrão: [documentação MSS](../docs/padroes/documentacao.md) §7.10.

Versões anteriores do cliente desktop foram publicadas junto do servidor, no [CHANGELOG do TchowStrick](../TchowStrick/CHANGELOG.md) (`v1.0.0`…`v1.2.6`); não são repetidas aqui.

## [Não publicado]

### Adicionado
- Repositório próprio do cliente desktop Swing, extraído do módulo `client-desktop` do TchowStrick `main` @ `b5ff8e1` sem alteração de código; projeto Maven autônomo que consome `tchow-domain`, `tchow-proto` e `tchow-server` instalados no repositório Maven local (M0; TchowStrick:ADR-0020).
- Documentação do cliente: operação local e arquitetura, compatibilidade com o TchowStrick, roteiro histórico `E4d-04` e descrição anterior da conexão, movidos do TchowStrick (M0).
