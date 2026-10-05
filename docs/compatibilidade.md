# Compatibilidade com o TchowStrick

Registro da dependência deste cliente em relação ao [TchowStrick](../../TchowStrick/README.md). Atualizar ao trocar a versão consumida.

## Origem do código

| Campo | Valor |
|---|---|
| Repositório de origem | `TchowStrick` (`https://github.com/rafaelbenzaquem/tchowstrick.git`) |
| Caminho de origem | `client-desktop/` |
| Revisão de origem | `main` @ `b5ff8e1` (04/10/2026) |
| Decisão | [TchowStrick:ADR-0020](../../TchowStrick/docs/adr/0020-cliente-desktop-em-repositorio-legado.md) |
| Conferência | 82 arquivos versionados na origem: 81 copiados com conteúdo idêntico (verificado por comparação byte a byte em 04/10/2026) e o `pom.xml` refeito como projeto autônomo |

## Artefatos consumidos

| Artefato | Uso no cliente |
|---|---|
| `br.com.mss.tchow:tchow-domain` | Regras puras, IA (`domain.ai`), histórico (`domain.history`) |
| `br.com.mss.tchow:tchow-proto` | Stubs gRPC do contrato `tchowstrick.v1` e mensagens do save `tchowstrick.save.v1` |
| `br.com.mss.tchow:tchow-server` | `net` (DTOs/eventos), `net.match.MatchService` (IA em processo) e `net.grpc.GrpcServerFactory` (`--embedded-server`) |

Versão: propriedade `tchow.version` do [pom.xml](../pom.xml), hoje `1.1.0-SNAPSHOT` (versão do agregador do TchowStrick em `b5ff8e1`). Como é `SNAPSHOT`, o build usa o que estiver instalado no repositório Maven local; instale a partir da revisão do TchowStrick desejada antes de buildar ([operação local](operacao/local.md#build)).

O `tchow-server` instalado é o jar sombreado do servidor (inclui dependências); o cliente usa só as classes acima. O contrato de rede segue o [protobuf original](../../TchowStrick/proto/src/main/proto/); mudanças incompatíveis lá exigem rebuild e teste deste cliente.

## Testes que cruzam os repositórios

`GrpcTransportTest` e `StandaloneServerTest` deste repositório sobem o servidor do `tchow-server` em processo e exercitam cliente e servidor ponta a ponta. Ao alterar `net`, `net.match` ou `net.grpc` no TchowStrick, reinstale os artefatos e rode `./mvnw verify` aqui.
