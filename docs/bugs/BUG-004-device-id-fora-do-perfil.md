---
id: BUG-004
tipo: bug
titulo: deviceId padrão da identidade gravado nas preferências globais, fora do perfil local
status: reportado
severidade: S4
prioridade: P3
depende_de: []
relacionados: [M1]
evidencia: constatado
branch:
versao:
atualizado_em: 2026-10-06
---

# BUG-004 — deviceId da identidade fora do perfil local

## Classificação

| Campo | Valor |
|---|---|
| Severidade | S4 — sem efeito visível hoje; o identificador é informativo |
| Prioridade | P3 |
| Origem | revisão do código (achado C5, 06/10/2026) |

## Ambiente

Cliente desktop `main` @ `e856296`.

## Reprodução

**Constatado no código:** `app/IdentityClientGateway.java` (`deviceId()`, :206-215 em `e856296`) cria e grava `mssIdentityDeviceId` em `Preferences.userNodeForPackage(IdentityClientGateway.class)` — nó global do usuário do SO, fora do perfil local de dados. É usado pelo construtor `IdentityClientGateway(IdentityTarget, IdentitySessionStore)`; o `Main` usa o construtor com `DataProfile.identityDeviceId()` (por perfil). Hoje o único uso do construtor de conveniência é o teste `IdentityClientGatewayTest.destinoRemotoEmTextoClaroERecusado`, que por isso grava o nó global ao rodar a suíte (o `deviceId()` é avaliado antes da validação do destino).

**Esperado:** todo dado local da conta fica no perfil local (removível em "Gerenciar contas").
**Observado:** o construtor de conveniência grava fora do perfil e sobrevive à remoção dos perfis.

## Causa-raiz

Construtor anterior aos perfis locais de dados (M1), mantido como atalho.

## Correção e regressão

Não corrigido (pedido do responsável, 06/10/2026: só registrar). Proposto: remover o construtor de conveniência ou exigir o `deviceId` do perfil.
