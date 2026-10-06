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

## Servidor validado

| Revisão do TchowStrick instalada | Data | Resultado |
|---|---|---|
| `b5ff8e1` + remoção do módulo (`cfd2466`) | 04/10/2026 | `./mvnw verify` aqui: 112 testes, 0 falhas |
| `main` @ `8b77e84` (fronteira `IdentityVerifier`, PR #62) mesclada na branch `feature/client-desktop-legado` | 04/10/2026 | `./mvnw clean verify` aqui: 112 testes, 0 falhas; o PR #62 não alterou o `client-desktop` nem APIs usadas por ele |
| `main` @ `a32e9aa` (PR #63, módulo removido) | 04/10/2026 | `./mvnw clean verify` aqui: 112 testes, 0 falhas |

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

## Identidade MSS

Adotado no [M1](marcos/M01-identidade-mss.md) (MSSIdentity:M4-04), em 05/10/2026, branch `feature/identidade-mss-desktop`.

| Campo | Valor |
|---|---|
| Artefato | `br.com.mss.identity:identity-client-java` |
| Versão | `1.0-SNAPSHOT` (propriedade `identity.client.version` do [pom.xml](../pom.xml)) |
| Origem | MSSIdentity, branch `feature/identidade-cliente-java` (worktree `.wt/MSSIdentity-cliente`), instalado no Maven local com `./mvnw install`; HEAD da branch ao verificar: `20f9398`. Publicação definitiva: D2 do MSSIdentity |
| API usada | só `br.com.mss.identity.client` (`IdentityClient`, `SessionStore`, `Session`, `ContactInput`, `IdentityException`); os stubs relocados em `client.internal.v1` aparecem apenas no teste do adaptador |
| Dependências transitivas | grpc 1.68.1 e protobuf-java 3.25.5, as mesmas do `tchow-proto` (conferido com `dependency:tree`) |
| Audiência do acesso de jogo | `tchowstrick` |
| Testes | `IdentityClientGatewayTest` (adaptador contra serviço falso em processo); demais testes usam fake da porta `IdentityAccountGateway` |

Como é `SNAPSHOT`, o build usa o que estiver instalado no Maven local: instale o `identity-client-java` (no MSSIdentity, `./mvnw install`) antes de buildar este repositório.

### Destinos de produção (desde 05/10/2026)

| Campo | Valor |
|---|---|
| Servidor oficial | `tchowstrick.minashonsoftware.com.br:443` (TLS), `tchow-server` 1.3.0 com `TCHOW_IDENTITY_MODE=remote` — só aceita acesso de jogo da identidade MSS (informado pelo orquestrador em 05/10/2026; não verificado por este repositório) |
| Identidade | `identity.minashonsoftware.com.br:443` (TLS), só `IdentityService` |
| Cliente | preset oficial embutido declara essa identidade (branch `feature/preset-oficial-e-multicontas`); contas `tchowstrick.auth.v1` não jogam mais no oficial |
