---
id: roteiro-E4d-04
tipo: roteiro
titulo: "Roteiro manual — [E4d-04] UI de estatísticas no client-desktop"
status: integrado
marco: M4
evidencia: documentado
nota: "aprovado pelo responsável em 21/09/2026 (relato agregado, sem evidência por passo)"
atualizado_em: 2026-10-04
---

# Roteiro manual — [E4d-04] UI de estatísticas no client-desktop

Movido de `TchowStrick/docs/validacao/roteiros/` em 04/10/2026 ([ADR-0020 do TchowStrick](../../../../TchowStrick/docs/adr/0020-cliente-desktop-em-repositorio-legado.md)). Registro histórico: comandos, caminhos (`client-desktop/`) e revisões citados são do repositório TchowStrick na data da entrega; para executar hoje, ver [operação local](../../operacao/local.md).

Copiado do [modelo comum](../../../../docs/modelos/feature.md). Ver [regras comuns](../../../../AGENTS.md).

## Identificação e preparação

Objetivo e limites: expor `GetMyStats` (já implementada no servidor desde
`[E4d-01]`) numa tela do `client-desktop` — menu "Jogador → Estatísticas…".
Só leitura; não altera estado do servidor nem da conta.

Branch/revisão base e revisão testada: `feature/e4d-04-desktop-stats-ui` a
partir de `main` (`ef3ea24`); revisão testada = diff não commitado desta
branch no momento da escrita deste roteiro (2026-09-21).

Especificação aprovada, requisitos/IDs e critérios de aceite: `[E4d-04]` em
`docs/issues.md` (achado comparando com o cliente Mobile/Web, que já tinha o
equivalente).

Pré-requisitos: JDK 17+, Maven (`./mvnw`, raiz do repo `TchowStrick`), Docker
só se for rodar `./mvnw verify` completo (persistência) — não necessário para
este roteiro, que não toca banco. Duas contas de teste no servidor escolhido
(local ou oficial), cada uma com pelo meno uma partida encerrada, para ver
números diferentes de zero.

## Staging local

1. Preparar um servidor local sem banco (stats zeradas, mas RPC responde):
   `cd server && ../mvnw exec:java -Dexec.mainClass=br.com.mss.tchow.ServerMain`
   (diretório `server/`) — esperar log "Persistência: desabilitada".
2. Iniciar o cliente: `cd client-desktop && ../mvnw exec:java -Dexec.mainClass=br.com.mss.tchow.Main -Dexec.args="--server=127.0.0.1:<porta impressa pelo servidor> --insecure"`
   (diretório `client-desktop/`) — janela do TchowStrick abre.
3. Fluxo principal: menu "Jogador" → "Estatísticas…".

| Passo | Ação concreta e diretório | Resultado esperado | Evidência e resultado observado |
|---|---|---|---|
| 1 | Sem perfil ainda: abrir "Jogador → Estatísticas…" | Pede para criar perfil (`ensureProfile`), não quebra | Aprovado — relato do usuário, 21/09/2026 |
| 2 | Com perfil, servidor não-oficial (o de cima): abrir "Estatísticas…" | Diálogo abre sem pedir conta, mostra 5 números zerados (sem banco) | Aprovado — relato do usuário, 21/09/2026 |
| 3 | Repetir com `docker compose` (banco real) e jogar 1 partida antes | Números refletem a partida jogada (`played=1`, etc.) | Aprovado — relato do usuário, 21/09/2026 |
| 4 | Servidor com `OFFICIAL_SERVER=true` e conta oficial ainda não criada no perfil: abrir "Estatísticas…" | Cai no fluxo de criação de conta oficial antes de mostrar o diálogo (mesmo portão de `onHost`) | Aprovado — relato do usuário, 21/09/2026 |
| 5 | Servidor caído/inalcançável: abrir "Estatísticas…" | Mensagem de erro clara (`warn`), sem travar a janela | Aprovado — relato do usuário, 21/09/2026 |

**Limite desta evidência:** o responsável relatou "tudo testado e validado" (staging e produção) em 21/09/2026, sem comando, log ou captura por cenário registrado nesta tarefa. As linhas acima refletem essa aprovação agregada, não uma verificação passo a passo por este agente — ver classificação em [AGENTS.md](../../../../AGENTS.md) ("Documentado/informado" vs. "Verificado por execução").

Encerramento: fechar o cliente (janela); `Ctrl+C` no terminal do `ServerMain`.
Sem dados de teste persistentes a limpar neste roteiro (servidor sem banco,
ou banco isolado do `docker-compose.yml` local).

## Produção — execução pelo responsável autorizado

Versão efetivamente implantada e como identificá-la: não registrada nesta
tarefa — a feature ainda não foi mesclada em `main` nem versionada/deployada
por este agente; se o responsável já testou contra o oficial (abaixo), a
versão exata que rodava lá no momento do teste não foi capturada aqui.

Condições: aprovação operacional do responsável, servidor oficial confirmado
na versão que inclui esta fatia, conta de teste oficial já criada.

1. Com uma conta oficial de teste já validada e histórico de partidas real:
   "Jogador → Estatísticas…" contra `tchowstrick.minashonsoftware.com.br:443`
   — esperado: números batem com o histórico conhecido daquela conta.
   Resultado: **aprovado — relato do usuário, 21/09/2026**, sem versão de
   servidor/log/captura registrados nesta tarefa.

## Resultado e pendências

Todos os cenários acima (staging e produção): **aprovados pelo responsável em
21/09/2026** (relato do usuário) — cobertura automatizada continua sendo só
`StandaloneServerTest.getMyStatsDoClienteDecodificaAsEstatisticasDoServidor`
(servidor fake em processo); a validação manual em si não foi executada por
este agente, só relatada por quem a rodou.

Lacunas impeditivas: nenhuma. Pendência documental: registrar, numa próxima
sessão, a revisão/versão exata do servidor oficial usada no teste de
produção e evidência por cenário (comando/captura), se o responsável quiser
elevar este roteiro de "relato" para "verificado por execução" nos moldes
completos do [modelo](../../../../docs/modelos/feature.md).
