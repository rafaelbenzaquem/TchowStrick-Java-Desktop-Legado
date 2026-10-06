---
id: M1
tipo: marco
titulo: Identidade MSS no desktop
status: em-andamento
depende_de: [M0, MSSIdentity:M3-01]
relacionados: [MSSIdentity:M4, MSSIdentity:M4-04, MSSIdentity:M4-05, TchowStrick:M8, TchowStrick:ADR-0020]
evidencia: verificado
branch: feature/preset-oficial-e-multicontas
integracao: branch
validacao: pendente
atualizado_em: 2026-10-06
---

# M1 — Identidade MSS no desktop

Lado cliente do [M4 do MSSIdentity](../../../MSSIdentity/docs/marcos/M04-integracao-tchowstrick.md) (item **M4-04**). Especificação, critério de pronto e **status do item ficam naquele marco** (fonte única); aqui ficam o registro de desenvolvimento e as verificações deste repositório. O lado servidor (M4-01 a M4-03) está no [M8 do TchowStrick](../../../TchowStrick/docs/marcos/M08-identidade-mss.md).

## Resultado para o usuário

O jogador do desktop entra com a conta MSS (login, renovação e acesso de jogo via `identity-client-java`) e joga no servidor TchowStrick, que valida o acesso pela identidade.

## Origem

Até 04/10/2026, o M4-04 previa alterar o `client-desktop` dentro do TchowStrick. Com a extração do cliente ([M0](M00-extracao-do-tchowstrick.md), TchowStrick:ADR-0020), o responsável definiu em 04/10/2026 que as mudanças de autenticação do cliente fazem parte deste repositório. Nenhuma alteração de cliente havia sido feita até então: a entrega mesclada no TchowStrick (PR #62, `7f6e377`) altera só o servidor (`IdentityVerifier`, `LocalIdentityVerifier`, `OfficialAccountGuard`).

## Decisões do responsável (05/10/2026)

- **Identidade por servidor:** o servidor (preset/configuração) indica se usa identidade MSS. LAN, embutido e servidores sem identidade seguem como antes, inclusive o fluxo de conta oficial `tchowstrick.auth.v1` (`GrpcAccountClient`/`CreateOfficialAccountDialog`).
- **Produção híbrida** (identidade + sessões legadas no `tchow-server`) fica a cargo do servidor; o preset oficial embutido **não** aponta para a identidade nesta entrega. *Substituída na mesma data pela decisão abaixo.*

## Decisões do responsável (05/10/2026, depois da produção remota)

Informado pelo orquestrador em 05/10/2026: a produção (`tchowstrick.minashonsoftware.com.br:443`, TLS) roda o `tchow-server` 1.3.0 em `TCHOW_IDENTITY_MODE=remote` e só aceita acesso de jogo da identidade MSS; a identidade pública fica em `identity.minashonsoftware.com.br:443` (TLS). Não verificado por este repositório (nenhuma conexão à produção nesta tarefa).

- **Preset oficial com identidade:** o oficial embutido declara a identidade de produção; nele só existe o fluxo de conta MSS. O fluxo de conta oficial antigo (`GrpcAccountClient`/`CreateOfficialAccountDialog`) fica restrito a servidores sem identidade.
- **Várias contas no mesmo computador:** duas ou mais janelas no mesmo usuário do Windows entram com contas MSS diferentes, sem flags, por perfis locais isolados e travados por janela; `--perfil=<nome>` como opção explícita.
- **Gerenciar contas:** painel de dados **locais** (listar, adicionar pelos fluxos existentes, remover deste computador) para contas MSS, conta oficial antiga e perfis de jogador/locais; operações remotas continuam onde estão.

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
- **Jogo em rede** (`net`, `net.grpc`): `AccountCredentials` é consultada a cada chamada por `GameCallCredentials`; com identidade, `IdentityGameCredentials` pede `issueGameAccess("tchowstrick")` (a biblioteca reaproveita o acesso até 1 min antes de expirar e renova a sessão antes, se preciso). `UNAUTHENTICATED` do servidor de jogo gera **uma** nova tentativa com acesso renovado (`CredentialRetry`: `CreateMatch`, `GetMyStats`, entrada no `Join`, jogada, chat, revanche, desfazer). Antes da nova tentativa, o adaptador descarta o acesso em cache com `IdentityClient.invalidateGameAccess` (MSSIdentity `2c75f4c`). Falha ao obter o acesso encerra a chamada localmente com o status correspondente e a mensagem em português. O `guest_id` enviado é o `account_id` da sessão (decisão de 04/10/2026 para contas sem `legacy_subject`). Durante a partida com identidade, `GetMyStats` periódico (a cada 1 min, `GrpcClientTransport.ACCESS_KEEPALIVE`; com 4 min o teste ponta a ponta de 05/10/2026 derrubou o stream aos 11 min de ociosidade, porque a biblioteca reusa o acesso em cache até 1 min antes de vencer e o servidor nunca recebia um acesso novo) entrega ao servidor um acesso renovado: o servidor revalida o stream `Join` a cada evento e, quando o acesso de 10 min vence, usa a credencial mais recente da mesma conta (TchowStrick M8). Sem isso, um jogador ocioso por mais de 10 min perderia o stream.
- **UI** (`ui.MssSignInDialog`, `ui.MssAccountDialog`, `Main`): menu `Jogador → Conta MSS…` (e `Criar ou acessar conta…` em servidor com identidade); `Confirmar contato…` e `Recuperar conta…` usam a identidade nesses servidores; a barra mostra "Servidor: X (conta MSS)". `Excluir conta…` informa que ainda não está disponível no desktop.

## Preset oficial, várias contas e Gerenciar contas (05/10/2026)

Branch `feature/preset-oficial-e-multicontas`, base `main` @ `e0514f6`.

### Diagnóstico das várias contas (bug relatado pelo responsável)

Relato: dois clientes abertos, contas diferentes, e "a cor ficou presa para quem marcou primeiro". Reproduzido em 05/10/2026 contra o ambiente local (identidade `localhost:9100`, `tchow-server` híbrido `localhost:5050`) com **dois processos** usando as classes do jar de `main` @ `e0514f6` e a mesma fiação do `Main` (programa `.wt/e2e-multicontas/`, cenário `antes`; nó de Preferences de teste, não o do cliente real). **Verificado por execução:**

1. **Sessão MSS compartilhada.** Todos os stores locais ficam no mesmo nó das `Preferences` do usuário do SO (`/br/com/mss/tchow/app`); a sessão MSS é uma por destino de identidade. A 2ª janela já abre "logada" na conta da 1ª; o login da 2ª (conta B) sobrescreve a sessão e a 1ª passa a ser B (`whoami` da instância 1 = conta B).
2. **Conta e acesso de jogo divergentes.** A 1ª janela ainda tinha em memória o acesso de jogo da conta A e mandava `guest_id` da conta B: o servidor recusa com `PERMISSION_DENIED` "identidade divergente da conta". Depois de renovar o acesso, a 1ª janela joga **como conta B**; a mesma conta B ocupa RED e BLUE.
3. **"Cor presa".** O token de assento (`LocalSessionTokenStore`, por partida/cor) também era compartilhado: a 2ª janela via RED (cor da 1ª) como "sua" — `knownColorFor` = RED, e o `JoinDialog` trava a cor e oferece **Retornar** — e a entrada em RED é recusada ("RED já está em uso", `FAILED_PRECONDITION`). Só escolhendo outra cor à mão a 2ª janela entrava.
4. **Rotação concorrente.** As duas janelas renovando a mesma sessão: a identidade detectou reuso de sessão rotacionada e revogou a família (log do serviço de identidade: `reuso de sessão rotacionada: família revogada … sessoes=2`); as duas janelas ficaram sem sessão.

Também eram compartilhados `LocalProfileStore` (perfil de jogador ativo, cujo id é o `guest_id` em LAN), carteira, sessão oficial antiga e o `deviceId` da identidade. No servidor (TchowStrick, **não alterado**; constatado no código em `480bf8f`): `MatchService.join` trava cada cor ao `guest_id` que a ocupou primeiro (ADR-0009), mas não impede o mesmo `guest_id` de ocupar duas cores (`server/src/main/java/br/com/mss/tchow/net/match/MatchService.java`, linhas 420–433) — por isso a mesma conta B ficou com RED e BLUE; e `OfficialAccountGuard` guarda a credencial renovada mais recente **por conta** (`OfficialAccountGuard.java`, linhas 29 e 66), o que é coerente com uma conta por janela. Nenhuma correção de servidor é necessária para este bug.

### O que mudou

- **Preset oficial** ([servers-default.json](../../src/main/resources/servers-default.json)): `identity.minashonsoftware.com.br:443` com TLS. `ServerDirectory.isTrustedIdentityEndpoint` só confia em destino oficial **sem** identidade (hoje nenhum), então o fluxo de conta antigo não aparece no oficial; `withOfficialIdentity` troca um registro antigo do oficial sem identidade (escolha salva no `LocalServerChoiceStore`, que é regravada, ou `servers.json` externo) pelo preset novo. LAN, embutido e servidores sem identidade seguem como antes. Cancelar o login num servidor com identidade mostra "O servidor Oficial só aceita jogadores com conta MSS…".
- **Perfis locais por janela** (`app.DataProfile`): cada janela trava `~/.tchowstrick/perfis/<nome>.lock` (`FileChannel.tryLock`, liberado pelo SO ao fechar ou cair). Sem opção, a 1ª janela usa o perfil `padrao` e as seguintes `perfil-2`, `perfil-3`… (até 20); `--perfil=<nome>` escolhe um perfil e falha com mensagem clara se ele estiver aberto em outra janela. Sessão MSS, tokens de assento, perfis de jogador, carteira, sessão oficial antiga e `deviceId` da identidade ficam no perfil; o `padrao` usa o mesmo nó de antes (dados existentes preservados) e os demais `…/app/perfis/<nome>`. A escolha de servidor continua comum. Como cada perfil é travado, uma sessão MSS nunca é usada (nem rotacionada) por duas janelas.
- **UI:** título com `[perfil local: perfil-2]` fora do padrão; barra "Conta MSS: <nick> · perfil local: <nome>" com **Entrar…** ou **Sair/Trocar de conta…**; menu `Jogador → Sair/Trocar de conta MSS…` e botão **Trocar de conta…** em `Conta MSS…` (sai só deste dispositivo e abre o entrar/criar).
- **Gerenciar contas** (`Jogador → Gerenciar contas…`; `app.LocalAccountsService` + `ui.ManageAccountsDialog`): tabela com tipo, nome/nick, servidor ou identidade, conta (contato mascarado lembrado ou id abreviado; nunca token), perfil local, estado (ativa/provisória/restrita/expirada) e uso por janela aberta. **Adicionar:** conta MSS (entrar/criar; se a janela já tem conta, oferece nova janela ou trocar), conta oficial antiga (só em servidor sem identidade; no oficial explica que agora é conta MSS), perfil de jogador e **nova janela** (outro processo do cliente no próximo perfil livre ou no nome dado). **Remover deste computador:** confirmação com o texto do que será apagado; conta MSS apaga a sessão local (opção desmarcada "Sair também no servidor (só este dispositivo)" usa `signOut(false)`), conta antiga apaga a sessão, perfil de jogador leva carteira e sessão antiga dele, perfil local (não o padrão nem o desta janela) é apagado inteiro. Perfis abertos em outra janela não podem ser alterados; os livres são travados durante a remoção. Nenhuma exclusão de conta no servidor; excluir/sair de todos continuam em `Conta MSS…`.

## Verificações

| Comando | Diretório / revisão | Resultado |
|---|---|---|
| `./mvnw -o -B verify` (JDK 21.0.2) | este repositório, `feature/identidade-mss-desktop` @ `34595e4` (código) | `BUILD SUCCESS`; 167 testes, 0 falhas; spotless e gate de cobertura de `net.*` atendidos |
| `./mvnw -o -B dependency:tree -Dincludes=io.grpc,com.google.protobuf` | idem | grpc 1.68.1 e protobuf-java 3.25.5 únicos (sem conflito entre a biblioteca e o `tchow-proto`) |

| `./mvnw.cmd -B -o clean verify` (JDK 21.0.2) | este repositório, `feature/preset-oficial-e-multicontas` com o código de `375c046` | `BUILD SUCCESS`; 200 testes, 0 falhas; spotless e gate de cobertura de `net.*` atendidos |
| `.wt/e2e-multicontas/run.sh antes` (2 processos, jar de `main` @ `e0514f6`) | raiz do portfólio, ambiente local no ar | 8/8 cenários reproduzem o defeito (conta trocada, `PERMISSION_DENIED`, mesma conta em duas cores, cor travada, revogação da família no log da identidade) |
| `.wt/e2e-multicontas/run.sh depois` (2 processos, jar de `375c046`) | idem | 10/10 cenários: perfis `padrao`/`perfil-2` automáticos; `--perfil=padrao` em uso recusado (inclusive pelo `tchowstrick.jar` real, saída 1); contas A e B distintas; RED (A) e BLUE (B) na mesma partida; 6 jogadas recebidas pelos dois streams; 15 rotações simultâneas por janela sem revogação; reabrir volta ao `padrao` com a conta A; 0 linhas "reuso de sessão" na identidade e 0 recusas no `tchow-server` no período |

Testes novos desta etapa: `DataProfileTest` (trava, perfis automáticos/explícitos, isolamento de sessão MSS, tokens de assento, perfis, carteira e sessão antiga, compatibilidade do padrão), `LocalAccountsServiceTest` (listagem e remoção por tipo, sem tokens, bloqueio por janela aberta, saída remota opcional), atualizações de `IdentityPresetTest`, `ServerDirectoryTest`, `ConnectionResolverTest`, `LaunchOptionsIdentityTest`, `MssAccountFlowTest` e `LocalIdentitySessionStoreTest`.

Testes novos da etapa anterior: `IdentityPresetTest`, `LaunchOptionsIdentityTest`, `LocalIdentitySessionStoreTest`, `MssAccountFlowTest` e `IdentityGameCredentialsTest` (fake da porta), `IdentityCallCredentialsTest` (interceptor e nova tentativa) e `IdentityClientGatewayTest` (adaptador contra serviço falso em processo).

Não verificado nesta entrega: execução da interface e jogo ponta a ponta contra identidade + `tchow-server` reais (serviço local da identidade não estava em execução durante a tarefa).

## Mensagens e estado da conta no oficial `remote` (06/10/2026)

Relato do responsável (06/10/2026, **documentado/informado**): com o oficial em `TCHOW_IDENTITY_MODE=remote`, quem cria conta MSS ou usa a conta antiga não joga e vê "sessão/conta expirada" e que precisa confirmar. Do lado do cliente, a recusa do servidor era mascarada e o estado da conta ficava desatualizado; o servidor está sendo ajustado em paralelo no TchowStrick (recusa da credencial de serviço → `UNAVAILABLE`; sessão antiga em `remote` → `FAILED_PRECONDITION`).

Branch `fix/mensagens-conta-mss` (base `main` @ `e856296`):

- [BUG-002](../bugs/BUG-002-mensagens-de-recusa-de-conta.md) (C1, C4): mensagens por caso, preservando a descrição local/servidor; texto da conta antiga só para servidores sem identidade.
- [BUG-003](../bugs/BUG-003-estado-da-conta-mss-desatualizado.md) (C2, C3): estado consultado na identidade (perfil + carência de 1 h, sem rotação desnecessária), recusa por contato vinda do jogo marca a conta como restrita e oferece Confirmar contato; aviso de cadastro sem afirmar envio.
- [BUG-004](../bugs/BUG-004-device-id-fora-do-perfil.md) (C5): só registrado.

| Comando | Diretório / revisão | Resultado |
|---|---|---|
| `./mvnw.cmd -B -ntp -o verify` (JDK 21.0.2) | worktree da branch, `fix/mensagens-conta-mss` @ `015bd02` | `BUILD SUCCESS`; 235 testes, 0 falhas; spotless e gate de cobertura atendidos |

Validação manual pendente: cenários nos BUG-002 e BUG-003; os que dependem das respostas novas do servidor exigem a correção do TchowStrick.

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
7. Servidor sem identidade (LAN, embutido) continua com o fluxo anterior; o oficial passa a pedir conta MSS (barra `Servidor: Oficial (conta MSS)`).
8. Duas janelas ao mesmo tempo (abrir o jar duas vezes): a 2ª mostra `[perfil local: perfil-2]` no título e "Conta MSS: não conectada"; entrar com outra conta, criar partida numa e entrar na outra com outra cor (cada janela joga com a própria conta; nenhuma cor aparece como "Retornar" na outra; jogadas chegam às duas).
9. Fechar tudo e reabrir uma janela: volta ao perfil padrão com a conta de antes.
10. `--perfil=padrao` com uma janela já aberta (mensagem "já está aberto em outra janela"; nada é aberto).
11. `Jogador → Gerenciar contas…`: a tabela lista as contas das duas janelas, com "esta janela"/"outra janela aberta"; remover algo do perfil da outra janela é recusado; remover a conta MSS desta janela (com e sem "Sair também no servidor") pede confirmação e a barra volta a "não conectada"; "Nova janela" abre outra janela; adicionar conta oficial antiga no oficial explica que agora é conta MSS.
12. Escolha salva antiga do oficial (sem identidade): ao abrir, o oficial aparece com "(conta MSS)".
