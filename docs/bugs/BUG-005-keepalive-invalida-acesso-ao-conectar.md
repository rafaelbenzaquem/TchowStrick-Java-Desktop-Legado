---
id: BUG-005
tipo: bug
titulo: Keepalive da partida invalida o acesso de jogo em cache a cada conexão
status: reportado
severidade: S4
prioridade: P3
depende_de: []
relacionados: [M1, BUG-002]
evidencia: constatado
branch:
versao:
atualizado_em: 2026-10-06
---

# BUG-005 — Keepalive invalida o acesso de jogo em cache ao conectar

## Classificação

| Campo | Valor |
|---|---|
| Severidade | S4 — sem efeito visível; uma emissão extra de acesso de jogo por conexão |
| Prioridade | P3 |
| Origem | revisão do código durante a correção do BUG-002 (06/10/2026), confirmada pelo orquestrador |

## Ambiente

Cliente desktop `main` @ `e856296` e branch `fix/mensagens-conta-mss` @ `e67b0e4`.

## Reprodução

**Constatado no código:** `net/grpc/GrpcClientTransport.startAccessKeepalive` usa `accountCredentials.renewAfterRejection()` como teste de "credencial renovável". Em `app/IdentityGameCredentials.renewAfterRejection` o método tem efeito colateral: chama `gateway.invalidateGameAccess()`. Assim, toda conexão a uma partida descarta o acesso de jogo em cache e força uma nova emissão (`IssueGameAccess`) na chamada seguinte.

**Esperado:** decidir se o keepalive deve rodar sem alterar o cache.
**Observado:** o cache é descartado a cada conexão.

## Causa-raiz

O método de "renovar após recusa" foi reaproveitado como predicado.

## Correção e regressão

Não corrigido: testes existentes dependem do comportamento atual e o efeito é só uma emissão extra. Proposto: trocar o teste por `accountCredentials.source() == AccountCredentials.Source.MSS_IDENTITY` (disponível desde o BUG-002) e ajustar os testes do keepalive.
