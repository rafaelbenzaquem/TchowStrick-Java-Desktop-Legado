# ROADMAP — TchowStrick Java Desktop (legado)

Cliente em modo legado: os clientes ativos do TchowStrick são os Godot (TchowStrick:ADR-0019). Este repositório preserva o cliente Swing utilizável e o mantém compatível com o servidor. Status de cada marco no front matter do próprio documento.

## Marcos

| Marco | Entregável ao usuário | Especificação |
|---|---|---|
| M0 — Extração do TchowStrick | Mesmo cliente desktop, agora buildado e executado a partir deste repositório | [M00](marcos/M00-extracao-do-tchowstrick.md) |
| M1 — Identidade MSS no desktop | Entrar com a conta MSS e jogar no servidor que valida pela identidade (MSSIdentity:M4-04) | [M01](marcos/M01-identidade-mss.md) |

## Horizonte

- **Agora:** M0 — validar build e uso do cliente extraído.
- **Depois:** M1 — identidade MSS no desktop, quando o `identity-client-java` (MSSIdentity M3-01) estiver disponível. Outros candidatos dependem de decisão do responsável (ex.: acompanhar mudanças de contrato do servidor).
- **Mais tarde:** decidir aposentadoria ou manutenção mínima do cliente Swing.

## Pronto para começar (DoR)

Objetivo claro, critérios observáveis e versão do TchowStrick consumida registrada em [compatibilidade](compatibilidade.md).

## Não-metas

- Hospedar ou alterar o servidor autoritativo, regras de jogo ou contrato protobuf — pertencem ao TchowStrick.
- Novas features de produto sem aprovação do responsável (a autenticação MSS do M1 foi definida pelo responsável em 04/10/2026).
