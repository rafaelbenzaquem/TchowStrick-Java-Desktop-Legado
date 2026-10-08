---
id: BUG-002
tipo: bug
titulo: Recusa de conta MSS vira "sessão expirada" genérica e leva a um laço sem saída
status: corrigido
severidade: S2
prioridade: P0
depende_de: []
relacionados: [M1, BUG-003, TchowStrick:M8]
evidencia: verificado
branch: fix/mensagens-conta-mss
integracao: branch
validacao: pendente
versao:
atualizado_em: 2026-10-08
---

# BUG-002 — Recusa de conta MSS mascarada como sessão expirada

## Classificação

| Campo | Valor |
|---|---|
| Severidade | S2 — o jogador com conta MSS não joga no oficial e a mensagem não leva a nenhuma ação que resolva |
| Prioridade | P0 |
| Origem | produção (relato do responsável, 06/10/2026) e revisão do código |

## Ambiente

Cliente desktop `main` @ `e856296` contra o servidor oficial que deveria estar em `TCHOW_IDENTITY_MODE=remote` (relato). **Conferido em 08/10/2026:** o oficial estava em `local` (sem a variável no `.env`), por isso recusava o acesso da identidade (TchowStrick:BUG-024); produção passou a `hybrid` e foi validada pelo responsável. As mensagens enganosas corrigidas aqui continuam válidas para qualquer recusa. Achados C1 (mapeamento de erros) e C4 (conta oficial antiga contra servidor com identidade) da revisão de 06/10/2026.

## Reprodução

**Documentado/informado** (responsável, 06/10/2026): ao criar conta MSS ou usar a conta oficial antiga no oficial, o jogador não consegue jogar e vê mensagem de sessão/conta expirada e que precisa confirmar.

**Constatado no código** (`main` @ `e856296`):

1. `net/grpc/GrpcErrors.java:20-24` trocava **todo** `UNAUTHENTICATED` por "Sessão inválida ou expirada. Use Jogador → Criar ou acessar conta… para entrar novamente.", descartando a descrição do servidor e as mensagens locais (falha ao obter o acesso na identidade, `GameCallCredentials.toStatus` :49-57; `IdentityAccountException` `NOT_SIGNED_IN`).
2. Com conta MSS, o menu indicado leva ao fluxo da conta MSS, que mostra "Conta provisória: confirme seu e-mail…" (`app/MssAccountFlow.java:59-60`) mesmo com a sessão válida: o jogador confirma, tenta de novo e recebe a mesma mensagem (laço).
3. Conta antiga contra servidor com identidade: a recusa chegava como a mesma mensagem genérica, sem dizer que o servidor só aceita conta MSS.

**Esperado:** a mensagem diz a causa e a ação que resolve.
**Observado:** mensagem genérica de sessão expirada para qualquer recusa de conta.

## Causa-raiz

Do lado do cliente, o mapeamento de erros (C1/C4). A recusa em si vem do servidor (configuração da introspecção, conforme o relato); as respostas do servidor estão sendo ajustadas em paralelo no TchowStrick: credencial de serviço recusada → `UNAVAILABLE` "identidade indisponível"; sessão antiga em servidor `remote` → `FAILED_PRECONDITION` "conta antiga não é aceita neste servidor; entre com a conta MSS"; conta restrita continua `PERMISSION_DENIED` "confirme seu contato para continuar jogando" (**documentado/informado**, pedido de 06/10/2026; não verificado nesta tarefa).

## Correção e regressão

Branch `fix/mensagens-conta-mss` (base `main` @ `e856296`), commit `bdba1c7`:

- `GrpcErrors.describe` recebe a origem da credencial (`AccountCredentials.Source`: nenhuma, sessão antiga, identidade MSS) e:
  - preserva a mensagem local quando a falha aconteceu antes de sair do cliente (`CredentialException` na causa; `GameCallCredentials.toStatus` passa a anexá-la);
  - sem sessão MSS → "Você não entrou na conta MSS neste servidor. Entre na conta MSS (Jogador → Conta MSS…) para jogar.";
  - `UNAUTHENTICATED` com conta MSS (depois da renovação automática) → o servidor de jogo recusou o acesso; entrar de novo em "Jogador → Sair/Trocar de conta MSS…" e, se persistir, o problema é do servidor; inclui a descrição do servidor; não fala em confirmar;
  - `PERMISSION_DENIED` com "contato" → conta restrita, ação "Jogador → Confirmar contato…"; outras recusas de permissão (assento, identidade divergente) mantêm a descrição do servidor;
  - `FAILED_PRECONDITION` com "conta antiga" → "Este servidor não aceita mais a conta oficial antiga: entre com a conta MSS em Jogador → Conta MSS…" (com a dica de "Trocar servidor…" quando o cliente ainda usa a conta antiga);
  - `UNAVAILABLE` com "identidade" → serviço de identidade indisponível, tente mais tarde; queda comum do servidor mantém a descrição/fallback de antes;
  - o texto da conta oficial antiga fica só para servidores sem identidade MSS.
- Mensagens padrão de `IdentityAccountException` (`NOT_SIGNED_IN`, `UNAUTHENTICATED`, `PERMISSION_DENIED`) passam a citar o menu certo.
- Regressão: `GrpcErrorsTest` (14 casos, inclusive a falha local atravessando o interceptor); antes não havia cobertura de `GrpcErrors`.

**Verificado por execução** (06/10/2026, JDK 21.0.2, raiz do worktree, `fix/mensagens-conta-mss` @ `015bd02`): `./mvnw.cmd -B -ntp -o verify` → `BUILD SUCCESS`, 235 testes, 0 falhas, spotless e cobertura atendidos.

Não verificado: interface e jogo ponta a ponta contra identidade e `tchow-server` reais; respostas novas do servidor (dependem da correção no TchowStrick).

## Validação manual (pendente)

Ambiente local, nunca o oficial: identidade local e `tchow-server` com identidade conforme a [operação local](../operacao/local.md#conta-mss-identidade-local), cliente desta branch (`./mvnw.cmd -o package -DskipTests` e `java -jar target/tchowstrick.jar --perfil=teste-bug002`). Os casos 3, 5 e 6 dependem da correção do servidor no TchowStrick.

1. Sem entrar na conta MSS, `Partida → Criar partida…` e desistir do cadastro → "O servidor … só aceita jogadores com conta MSS. Entre ou crie a conta em Jogador → Conta MSS…"; nenhuma menção a "Criar ou acessar conta".
2. Entrar na conta MSS, parar a identidade local e criar partida → mensagem de identidade indisponível (local), sem "sessão expirada".
3. Conta MSS válida com o servidor configurado com credencial de introspecção errada → "O serviço de identidade MSS está indisponível para o servidor de jogo agora. Tente novamente mais tarde."; nenhuma menção a confirmar e-mail.
4. Conta MSS válida, sessão revogada no servidor ("Sair de todos" em outra janela) e criar partida na primeira → a renovação local falha e a mensagem manda entrar de novo em Jogador → Conta MSS…; `Conta MSS…` abre o entrar/criar (sem laço).
5. Conta MSS válida com o servidor recusando o acesso (`UNAUTHENTICATED`) → "O servidor de jogo recusou o acesso da sua conta MSS…" com "(servidor: …)"; nenhuma menção a confirmar.
6. Servidor `remote` sem identidade no preset (lista `servers.json` sem `identity`) e sessão oficial antiga → "Este servidor não aceita mais a conta oficial antiga…" com a dica de Trocar servidor….
7. Conta restrita (ver BUG-003, cenário 2) → "Conta restrita… Jogador → Confirmar contato…" com oferta de confirmar agora.
8. Servidor sem identidade (embutido/LAN) recusando a sessão → texto antigo "Sessão inválida ou expirada. Use Jogador → Criar ou acessar conta…".
