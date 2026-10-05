---
id: M1
tipo: marco
titulo: Identidade MSS no desktop
status: em-andamento
depende_de: [M0, MSSIdentity:M3-01]
relacionados: [MSSIdentity:M4, MSSIdentity:M4-04, MSSIdentity:M4-05, TchowStrick:M8, TchowStrick:ADR-0020]
evidencia: verificado
branch: feature/identidade-mss-desktop
integracao: branch
validacao: pendente
atualizado_em: 2026-10-05
---

# M1 — Identidade MSS no desktop

Lado cliente do [M4 do MSSIdentity](../../../MSSIdentity/docs/marcos/M04-integracao-tchowstrick.md) (item **M4-04**). Especificação, critério de pronto e **status do item ficam naquele marco** (fonte única); aqui ficam o registro de desenvolvimento e as verificações deste repositório. O lado servidor (M4-01 a M4-03) está no [M8 do TchowStrick](../../../TchowStrick/docs/marcos/M08-identidade-mss.md).

## Resultado para o usuário

O jogador do desktop entra com a conta MSS (login, renovação e acesso de jogo via `identity-client-java`) e joga no servidor TchowStrick, que valida o acesso pela identidade.

## Origem

Até 04/10/2026, o M4-04 previa alterar o `client-desktop` dentro do TchowStrick. Com a extração do cliente ([M0](M00-extracao-do-tchowstrick.md), TchowStrick:ADR-0020), o responsável definiu em 04/10/2026 que as mudanças de autenticação do cliente fazem parte deste repositório. Nenhuma alteração de cliente havia sido feita até então: a entrega mesclada no TchowStrick (PR #62, `7f6e377`) altera só o servidor (`IdentityVerifier`, `LocalIdentityVerifier`, `OfficialAccountGuard`).

## Decisões do responsável (05/10/2026)

- **Identidade por servidor:** o servidor (preset/configuração) indica se usa identidade MSS. LAN, embutido e servidores sem identidade seguem como antes, inclusive o fluxo de conta oficial `tchowstrick.auth.v1` (`GrpcAccountClient`/`CreateOfficialAccountDialog`).
- **Produção híbrida** (identidade + sessões legadas no `tchow-server`) fica a cargo do servidor; o preset oficial embutido **não** aponta para a identidade nesta entrega.

## Dependências

| Dependência | Situação |
|---|---|
| `identity-client-java` (MSSIdentity M3-01) | consumido como `br.com.mss.identity:identity-client-java:1.0-SNAPSHOT` do Maven local ([compatibilidade](../compatibilidade.md)); publicação definitiva é a D2 do MSSIdentity |
| Serviço de identidade local (MSSIdentity M2) e `tchow-server` com verificação remota/híbrida (TchowStrick M8, MSSIdentity M4-01..03) | em desenvolvimento em outras branches; integração ponta a ponta ainda não verificada por este repositório |

## Desenvolvimento (05/10/2026)

Branch `feature/identidade-mss-desktop`, base `main` @ `815f04a`.

- **Configuração por servidor** (`net.config`): `ServerPreset` ganhou `IdentityTarget` opcional (host:porta + TLS). Fontes: `servers.json` (`"identity": "host:porta"`, `"identityTls": false|true`, padrão TLS), escolha salva (`LocalServerChoiceStore`) e linha de comando `--identity=host:porta` (exige `--server=`) com `--identity-plaintext`. Texto claro só é aceito para loopback (`localhost`, `127.x`, `::1`). Arquivo de desenvolvimento: [`config/servers-local-identidade.json`](../../config/servers-local-identidade.json) (`tchow-server` em `localhost:5050` + identidade `localhost:9100` sem TLS).
- **Porta interna** `app.IdentityAccountGateway` com o adaptador `IdentityClientGateway` sobre `IdentityClient`; erros traduzidos para `IdentityAccountException` com mensagens em português (UNAVAILABLE → "Serviço de identidade MSS indisponível…", PERMISSION_DENIED → "Conta MSS restrita…"). Fluxos sem Swing em `app.MssAccountFlow` (entrar/criar com nick + e-mail → código, e-mail já cadastrado → recuperação, confirmar e-mail, recuperar, estado, perfil nick/avatar, sair deste dispositivo/de todos). Telefone fica fora do MVP desktop: se o serviço só aceitar telefone, o jogador recebe mensagem clara.
- **Sessão local** `app.LocalIdentitySessionStore`: `Preferences` do usuário (mesmo mecanismo dos stores existentes — `LocalAccountSessionStore`, `LocalWalletStore` etc.; no Windows fica no registro do usuário), um nó por destino de identidade (`host_porta_tls|plain`). Token nunca registrado em log (`toString` sem token).
- **Jogo em rede** (`net`, `net.grpc`): `AccountCredentials` é consultada a cada chamada por `GameCallCredentials`; com identidade, `IdentityGameCredentials` pede `issueGameAccess("tchowstrick")` (a biblioteca reaproveita o acesso até 1 min antes de expirar e renova a sessão antes, se preciso). `UNAUTHENTICATED` do servidor de jogo gera **uma** nova tentativa com acesso renovado (`CredentialRetry`: `CreateMatch`, `GetMyStats`, entrada no `Join`, jogada, chat, revanche, desfazer). Antes da nova tentativa, o adaptador descarta o acesso em cache com `IdentityClient.invalidateGameAccess` (MSSIdentity `2c75f4c`). Falha ao obter o acesso encerra a chamada localmente com o status correspondente e a mensagem em português. O `guest_id` enviado é o `account_id` da sessão (decisão de 04/10/2026 para contas sem `legacy_subject`).
- **UI** (`ui.MssSignInDialog`, `ui.MssAccountDialog`, `Main`): menu `Jogador → Conta MSS…` (e `Criar ou acessar conta…` em servidor com identidade); `Confirmar contato…` e `Recuperar conta…` usam a identidade nesses servidores; a barra mostra "Servidor: X (conta MSS)". `Excluir conta…` informa que ainda não está disponível no desktop.

## Verificações

| Comando | Diretório / revisão | Resultado |
|---|---|---|
| `./mvnw -o -B verify` (JDK 21.0.2) | este repositório, `feature/identidade-mss-desktop` @ `34595e4` (código) | `BUILD SUCCESS`; 167 testes, 0 falhas; spotless e gate de cobertura de `net.*` atendidos |
| `./mvnw -o -B dependency:tree -Dincludes=io.grpc,com.google.protobuf` | idem | grpc 1.68.1 e protobuf-java 3.25.5 únicos (sem conflito entre a biblioteca e o `tchow-proto`) |

Testes novos: `IdentityPresetTest`, `LaunchOptionsIdentityTest`, `LocalIdentitySessionStoreTest`, `MssAccountFlowTest` e `IdentityGameCredentialsTest` (fake da porta), `IdentityCallCredentialsTest` (interceptor e nova tentativa) e `IdentityClientGatewayTest` (adaptador contra serviço falso em processo).

Não verificado nesta entrega: execução da interface e jogo ponta a ponta contra identidade + `tchow-server` reais (serviço local da identidade não estava em execução durante a tarefa).

## Limitações e pendências

- Stream `Join` longo: as chamadas unárias renovam o acesso; se o servidor encerrar o stream por expiração do acesso, o cliente só registra no log — não há reconexão automática do stream (reentrada manual pela partida/cor, com o token de assento de sempre).
- Exclusão de conta MSS, telefone/SMS e convidado (`BeginGuestSession`/`PromoteGuest`) não estão no desktop.
- Contas com `legacy_subject` (migração M5) mandariam `account_id` como `guest_id` e seriam recusadas pelo guard do servidor; tratar junto da M5.
- Sugestões para o `identity-client-java`: operação para descartar o acesso em cache de uma audiência; `deleteAccount`.
- Chamadas de rede continuam na EDT, como o restante do cliente.

## Validação

Conforme a diretriz do responsável registrada no M4 do MSSIdentity: somente quando o desktop autenticar e jogar pela identidade MSS, com tudo integrado. Execução local: [operação local](../operacao/local.md#conta-mss-identidade-local). Cenários previstos (resultado esperado entre parênteses):

1. Criar conta com nick + e-mail, receber o código no Mailpit e confirmar (estado "Conta ativa").
2. Criar com e-mail já cadastrado (nenhuma sessão; oferta de código de recuperação; entrar com ele).
3. `Recuperar conta…` em outro perfil/máquina (nova sessão).
4. Criar partida, entrar com outro jogador, jogar, reconectar e pedir revanche (servidor aceita o acesso da audiência `tchowstrick`).
5. `Conta MSS…` → editar nick/avatar; sair deste dispositivo e de todos (próxima partida pede login).
6. Conta restrita (carência vencida) e identidade fora do ar (mensagens "Conta MSS restrita…" e "Serviço de identidade MSS indisponível…"; nada concedido).
7. Servidor sem identidade (LAN/oficial) continua com o fluxo anterior.
