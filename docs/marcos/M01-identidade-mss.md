---
id: M1
tipo: marco
titulo: Identidade MSS no desktop
status: proposto
depende_de: [M0, MSSIdentity:M3-01]
relacionados: [MSSIdentity:M4, MSSIdentity:M4-04, MSSIdentity:M4-05, TchowStrick:M8, TchowStrick:ADR-0020]
evidencia: proposto
atualizado_em: 2026-10-04
---

# M1 — Identidade MSS no desktop

Lado cliente do [M4 do MSSIdentity](../../../MSSIdentity/docs/marcos/M04-integracao-tchowstrick.md) (item **M4-04**). Especificação, critério de pronto e **status do item ficam naquele marco** (fonte única); aqui ficam o registro de desenvolvimento e as verificações deste repositório. O lado servidor (M4-01 a M4-03) está no [M8 do TchowStrick](../../../TchowStrick/docs/marcos/M08-identidade-mss.md).

## Resultado para o usuário

O jogador do desktop entra com a conta MSS (login, renovação e acesso de jogo via `identity-client-java`) e joga no servidor TchowStrick, que valida o acesso pela identidade.

## Origem

Até 04/10/2026, o M4-04 previa alterar o `client-desktop` dentro do TchowStrick. Com a extração do cliente ([M0](M00-extracao-do-tchowstrick.md), TchowStrick:ADR-0020), o responsável definiu em 04/10/2026 que as mudanças de autenticação do cliente fazem parte deste repositório. Nenhuma alteração de cliente havia sido feita até então: a entrega mesclada no TchowStrick (PR #62, `7f6e377`) altera só o servidor (`IdentityVerifier`, `LocalIdentityVerifier`, `OfficialAccountGuard`).

## Dependências

| Dependência | Situação |
|---|---|
| `identity-client-java` publicado pelo MSSIdentity (M3-01) | pendente; registrar coordenadas e versão em [compatibilidade](../compatibilidade.md) ao consumir |
| Servidor com verificação remota (TchowStrick M8 / MSSIdentity M4-01..03) | parte local mesclada (`8b77e84`); remota pendente |

## Desenvolvimento

Ainda não iniciado. Branch prevista: `feature/identidade-mss-desktop` neste repositório, derivada da principal após o M0.

## Validação

Conforme a diretriz do responsável registrada no M4 do MSSIdentity: somente quando o desktop autenticar e jogar pela identidade MSS, com tudo integrado.
