# TchowStrick Java Desktop (legado)

Cliente desktop Java 21/Swing do TchowStrick (jogo dos pontinhos), com IA em processo, partida em rede via gRPC, servidor embutido para LAN, conta oficial, estatísticas e saves. Extraído do módulo `client-desktop` do [TchowStrick](../TchowStrick/README.md) em 04/10/2026 ([ADR-0020 de lá](../TchowStrick/docs/adr/0020-cliente-desktop-em-repositorio-legado.md)); depende dos artefatos `domain`, `proto` e `server` daquele repositório e não hospeda outro servidor autoritativo.

Estado: código idêntico ao do TchowStrick `main` @ `b5ff8e1`; build e testes automatizados verificados na extração; validação manual da extração pendente ([M0](docs/marcos/M00-extracao-do-tchowstrick.md)). Clientes ativos do produto são os Godot ([Mobile/Web](../TchowStrick-Mobile-Web/README.md) e [Mobile-DotNet](../TchowStrick-Mobile-DotNet/README.md)).

Comece pelo [índice](docs/README.md), [compatibilidade](docs/compatibilidade.md), [como rodar](docs/operacao/local.md) e [CHANGELOG](CHANGELOG.md). Leia [AGENTS.md](AGENTS.md) e o [padrão de documentação MSS](../docs/padroes/documentacao.md) antes de alterar o projeto.
