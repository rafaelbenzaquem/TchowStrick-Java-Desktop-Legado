---
id: BUG-003
tipo: bug
titulo: Estado da conta MSS desatualizado (provisória/restrita) e aviso de cadastro que afirma envio do código
status: corrigido
severidade: S3
prioridade: P1
depende_de: []
relacionados: [M1, BUG-002]
evidencia: verificado
branch: fix/mensagens-conta-mss
integracao: branch
validacao: pendente
versao:
atualizado_em: 2026-10-06
---

# BUG-003 — Estado da conta MSS desatualizado e envio de código afirmado

## Classificação

| Campo | Valor |
|---|---|
| Severidade | S3 — há contorno (Confirmar contato pelo menu), mas o cliente mostra o estado errado e não oferece a ação quando o servidor recusa |
| Prioridade | P1 |
| Origem | revisão do código a partir do relato do BUG-002 (06/10/2026) |

## Ambiente

Cliente desktop `main` @ `e856296`, conta MSS (identidade com carência de 1 h para confirmar o contato, MSSIdentity `AccountStatus.GRACE_PERIOD`).

## Reprodução

**Constatado no código** (`main` @ `e856296`):

1. C2 — o estado guardado na sessão (`PROVISIONAL`) só muda quando a sessão é rotacionada: `IdentityClientGateway.refreshStatus` (:108-110) chamava `refreshIfNeeded`, que não consulta a identidade enquanto a sessão não está perto de vencer (30 dias). O portão de rede (`Main.ensureMssAccount`), o diálogo Conta MSS e "Gerenciar contas" (`LocalAccountsService.mssState`) mostravam esse estado. Passada 1 h sem confirmar, o servidor de jogo responde `PERMISSION_DENIED` e o cliente continuava mostrando "provisória", sem oferecer Confirmar contato; com o contato confirmado em outro dispositivo, continuava mostrando "provisória".
2. C3 — depois de criar a conta, "Conta MSS criada. Enviamos um código para …" (`MssAccountFlow.java:98-101`) afirmava o envio, mas a identidade responde igual quando não consegue entregar (anti-enumeração).

**Esperado:** estado atual da conta, e aviso que não promete o que o cliente não sabe.
**Observado:** estado da última rotação da sessão; envio afirmado.

## Causa-raiz

O cliente tratava o estado da sessão guardada como atual; a biblioteca só o atualiza na rotação. O texto de cadastro presumia entrega do código.

## Correção e regressão

Branch `fix/mensagens-conta-mss`, commit `015bd02`:

- `IdentityClientGateway.refreshStatus`: conta `ACTIVE` não consulta a rede; senão `refreshIfNeeded` (rotaciona só perto do vencimento) + `profile()` (`contactVerified`). Contato confirmado → `ACTIVE`; sem confirmação, `IdentityAccountGateway.deriveState` aplica a carência de 1 h a partir da primeira vez que este dispositivo viu a conta provisória (`IdentitySessionStore.provisionalSince`, gravado pelo `StoreAdapter`; a conta é no máximo tão nova quanto isso, então a restrição derivada nunca é precoce). Sem essa referência (sessão de versão anterior), uma única rotação (`refresh()`) traz o estado calculado pela identidade. O estado derivado fica guardado na sessão local (mesmo token).
- `PERMISSION_DENIED` "confirme seu contato…" vindo do servidor de jogo: o interceptor (`GameCallCredentials`) avisa a credencial (`AccountCredentials.accountRestricted`), que marca a sessão local como `RESTRICTED` (`IdentityAccountGateway.markRestricted`); o `Main` oferece "Confirmar o e-mail agora?" em Criar partida, Entrar, Estatísticas e na conexão.
- Portão de rede: conta não ativa tem o estado consultado; restrita → explica e oferece confirmar antes de chamar o servidor; identidade fora do ar → segue com o último estado conhecido (o servidor decide); sessão encerrada pela identidade → abre o entrar/criar.
- "Gerenciar contas" rotula o estado MSS como "(último estado conhecido)" — a lista lê só dados locais.
- C3: "Conta MSS criada. Se o e-mail … estiver correto, você receberá um código em instantes; confira o spam. Sem código? Peça outro. …"; o reenvio diz "Novo código pedido. Se o e-mail estiver correto, ele chega em instantes…"; o diálogo do código sugere conferir o spam ou pedir outro. Nenhum texto revela se o e-mail existe.
- Regressão: `AccountStateDerivationTest`, casos novos em `IdentityClientGatewayTest` (ativa sem rede, confirmada em outro lugar sem rotação, carência de 1 h, rotação única sem referência, referência gravada no cadastro e não adiada pela rotação, marcação de restrita), `IdentityCallCredentialsTest` (só a recusa por contato marca a conta), `LocalIdentitySessionStoreTest` e `LocalAccountsServiceTest`.

**Verificado por execução** (06/10/2026, JDK 21.0.2, raiz do worktree, `fix/mensagens-conta-mss` @ `015bd02`): `./mvnw.cmd -B -ntp -o verify` → `BUILD SUCCESS`, 235 testes, 0 falhas, spotless e cobertura atendidos.

Não verificado: interface contra identidade e `tchow-server` reais.

## Validação manual (pendente)

Mesmo ambiente local do [BUG-002](BUG-002-mensagens-de-recusa-de-conta.md#validação-manual-pendente):

1. Criar conta MSS nova → aviso "Se o e-mail … estiver correto, você receberá um código…"; cancelar a confirmação; `Jogador → Conta MSS…` mostra "Conta provisória…".
2. Mesma conta depois de 1 h sem confirmar (ou carência reduzida na identidade local): `Jogador → Conta MSS…` mostra "Conta restrita…"; Criar partida oferece "Confirmar o e-mail agora?"; confirmar com o código do Mailpit → "E-mail confirmado. Conta ativa…" e a partida pode ser criada.
3. Conta provisória confirmada pelo Web/outro dispositivo: `Jogador → Conta MSS…` neste mostra "Conta ativa" sem novo login.
4. `Jogador → Gerenciar contas…` mostra o estado MSS como "… (último estado conhecido)".
