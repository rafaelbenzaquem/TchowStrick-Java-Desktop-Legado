---
id: BUG
tipo: registro
titulo: Bugs — TchowStrick Java Desktop (legado)
atualizado_em: 2026-10-09
---

# Bugs — TchowStrick Java Desktop (legado)

Registro no formato do [padrão MSS](../../../docs/padroes/documentacao.md) §7.3 ([modelo](../../../docs/modelos/bug.md)). Bugs do cliente anteriores à extração (04/10/2026) continuam no [registro do TchowStrick](../../../TchowStrick/docs/bugs/README.md), com os IDs de lá (`TchowStrick:BUG-nnn`).

| ID | Resumo | Sev. | Prio. | Origem | Status | Correção | Fonte |
|---|---|---|---|---|---|---|---|
| BUG-001 | GUI Swing com botões cortados, texto sobre o desenho, chips/botões sumindo e janelas maiores que a tela | S3 | P1 | revisão (pedido do responsável) | validado (08/10/2026) | `fix/dimensionamento-gui` (PR #4); ajuste em `fix/largura-tela-inicial` (PR #6) | [BUG-001](BUG-001-dimensionamento-gui.md) |
| BUG-002 | Recusa de conta MSS vira "sessão expirada" genérica e leva a um laço sem saída (C1, C4) | S2 | P0 | produção (relato do responsável) + revisão | integrado (PR #5); validação pendente | `fix/mensagens-conta-mss` (PR #5) | [BUG-002](BUG-002-mensagens-de-recusa-de-conta.md) |
| BUG-003 | Estado da conta MSS desatualizado (provisória/restrita) e aviso de cadastro que afirma envio do código (C2, C3) | S3 | P1 | revisão | integrado (PR #5); validação pendente | `fix/mensagens-conta-mss` (PR #5) | [BUG-003](BUG-003-estado-da-conta-mss-desatualizado.md) |
| BUG-004 | `deviceId` padrão da identidade gravado nas preferências globais, fora do perfil local (C5) | S4 | P3 | revisão | reportado | — | [BUG-004](BUG-004-device-id-fora-do-perfil.md) |
| BUG-005 | Keepalive da partida invalida o acesso de jogo em cache a cada conexão | S4 | P3 | revisão (validação do BUG-002) | reportado | — | [BUG-005](BUG-005-keepalive-invalida-acesso-ao-conectar.md) |

Próximo ID livre: **BUG-006**.
