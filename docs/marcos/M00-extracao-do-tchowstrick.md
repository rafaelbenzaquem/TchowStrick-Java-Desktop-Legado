---
id: M0
tipo: marco
titulo: Extração do cliente desktop do TchowStrick
status: em-validacao
prioridade: P2
esforco: P
depende_de: []
relacionados: [TchowStrick:ADR-0020, TchowStrick:E4a-01]
evidencia: verificado
branch: feature/migra-client-desktop
integracao: branch
validacao: pendente
atualizado_em: 2026-10-04
---

# M0 — Extração do cliente desktop do TchowStrick

## Resultado para o usuário

O jogador continua usando o mesmo cliente desktop Swing, agora buildado e executado a partir deste repositório; o repositório do TchowStrick passa a conter só servidor, domínio e contrato.

## Escopo

- Incluído: código, testes e recursos de `TchowStrick/client-desktop/` (`main` @ `b5ff8e1`), copiados sem alteração; `pom.xml` autônomo; wrapper Maven; documentação específica do cliente (operação local e arquitetura, roteiro histórico `E4d-04`, descrição anterior da conexão); remoção do módulo e ajuste da documentação no TchowStrick (branch `feature/client-desktop-legado` de lá); inclusão do produto na documentação da raiz do portfólio (branch `docs/produto-java-desktop-legado`).
- Fora de escopo: qualquer mudança de comportamento do cliente, do servidor ou do contrato; publicação de versão; criação de remoto para este repositório.

## Arquitetura e impacto

Decisão e alternativas na [ADR-0020 do TchowStrick](../../../TchowStrick/docs/adr/0020-cliente-desktop-em-repositorio-legado.md). Dependências e versão em [compatibilidade](../compatibilidade.md). Sem mudança de contrato, dados ou operação de produção.

## Riscos

| Risco | Mitigação | Rollback |
|---|---|---|
| Artefatos do TchowStrick ausentes ou desatualizados no repositório Maven local | Passo de `install` documentado; versão em `tchow.version` | Reinstalar a partir da revisão desejada |
| Mudança no servidor quebra o cliente sem ser percebida (o teste ponta a ponta saiu do TchowStrick) | Registrado na ADR-0020 e na compatibilidade | Rodar `./mvnw verify` aqui após reinstalar |
| Perda de histórico Git do módulo | Histórico permanece no TchowStrick; origem registrada | Não aplicável |

## Itens

| ID | Item | Status | Integração | Validação |
|---|---|---|---|---|
| M0-01 | Copiar código, testes e recursos; `pom.xml` autônomo; wrapper | em-validacao | branch | pendente |
| M0-02 | Mover documentação do cliente e criar conjunto mínimo de docs | em-validacao | branch | pendente |

## Verificações (04/10/2026, Windows 11, Git Bash, JDK 27)

| Verificação | Diretório / revisão | Resultado |
|---|---|---|
| Comparação byte a byte dos 81 arquivos copiados contra `git show b5ff8e1:client-desktop/<arquivo>` | raiz do portfólio | 0 diferenças |
| `./mvnw -q -B -DskipTests -Dspotless.check.skip=true install` | worktree do TchowStrick, branch `feature/client-desktop-legado` (base `b5ff8e1`, com o módulo removido) | Sucesso; `tchow-domain`, `tchow-proto` e `tchow-server` 1.1.0-SNAPSHOT instalados |
| `./mvnw -B -Dspotless.check.skip=true verify` | este repositório, branch `feature/migra-client-desktop` | `BUILD SUCCESS`; 112 testes, 0 falhas; gate de cobertura atendido; `target/tchowstrick.jar` gerado |
| Repetição após mesclar a `main` do TchowStrick @ `8b77e84` (PR #62, `IdentityVerifier`) na branch `feature/client-desktop-legado`: `install` lá e `./mvnw -B -Dspotless.check.skip=true clean verify` aqui | ambos | Sucesso; 112 testes, 0 falhas; cobertura atendida |
| `spotless` (apply/check) | ambos | **Não executado:** `google-java-format` 1.24 falha em JDK 27 (`NoSuchMethodError` em `com.sun.tools.javac`) — limitação de ambiente, preexistente; o código Java do cliente não foi alterado |

Não executado: interface gráfica, partida real, servidor oficial e testes de persistência do servidor (Docker).

## Validação manual

Pré-requisitos: JDK 21+; checkout do TchowStrick na branch `feature/client-desktop-legado` ao lado deste repositório.

Preparação (Git Bash):

```bash
cd TchowStrick && ./mvnw -q install -DskipTests && cd ..
cd TchowStrick-Java-Desktop-Legado && ./mvnw clean verify
```

Em JDK 27, acrescentar `-Dspotless.check.skip=true` ao `verify` (ver [operação local](../operacao/local.md#build)).

| # | Cenário | Resultado esperado |
|---|---|---|
| 1 | `./mvnw clean verify` neste repositório | `BUILD SUCCESS`, `target/tchowstrick.jar` gerado |
| 2 | `java -jar target/tchowstrick.jar` | Janela do TchowStrick abre, como antes da extração |
| 3 | `Partida → Criar partida…` → Adversário `IA — Média` | Partida contra a IA começa e termina normalmente, sem servidor |
| 4 | Num terminal, no TchowStrick: `java -jar server/target/tchow-server.jar`; noutro, aqui: `java -jar target/tchowstrick.jar --server=localhost:5050` e uma segunda instância igual; uma cria, a outra entra | Partida em rede entre as duas instâncias, como no RM-01 |
| 5 | No TchowStrick: `./mvnw -q -DskipTests package` | Build do reactor com três módulos (`domain`, `proto`, `server`) passa; não há `client-desktop` |
| 6 | Opcional, no TchowStrick: `docker build -t tchowstrick-server .` | Imagem do servidor builda sem o `client-desktop` |

Resultado: pendente de execução pelo responsável.
