# Operação local e arquitetura — TchowStrick Java Desktop (legado)

Conteúdo do cliente extraído de `TchowStrick/docs/operacao/local.md` em 04/10/2026 ([ADR-0020 do TchowStrick](../../../TchowStrick/docs/adr/0020-cliente-desktop-em-repositorio-legado.md)). Servidor, containers, deploy e reidratação continuam documentados na [operação do TchowStrick](../../../TchowStrick/docs/operacao/local.md).

O preset oficial atual declara domínio, porta 443 e TLS (segurança da conexão): [servers-default.json](../../src/main/resources/servers-default.json). Exemplos localhost são apenas locais. A referência atual de seleção é a [ADR-0017 do TchowStrick](../../../TchowStrick/docs/adr/0017-lista-de-servidores-e-troca-sem-ip-porta.md). Conferir a interface na validação humana.

Comandos a executar na raiz deste repositório, salvo indicação. Builds podem baixar dependências e escrevem no repositório Maven local do usuário; operações que escrevem fora do portfólio exigem registrar impedimento conforme as [regras comuns](../../../AGENTS.md). Operação de produção é exclusiva do responsável autorizado.

## Requisitos

- JDK 21+ (o código compila com `release 21`).
- Checkout do [TchowStrick](../../../TchowStrick/README.md) ao lado deste repositório, na versão indicada em [compatibilidade](../compatibilidade.md).
- Maven (ou o wrapper `./mvnw` incluído).
- Docker **não** é necessário: os testes deste repositório sobem o servidor em memória, no próprio processo.

## Build

1. No TchowStrick, instalar `tchow-domain`, `tchow-proto` e `tchow-server` no repositório Maven local (os testes do servidor exigem Docker; por isso são pulados aqui):

   ```bash
   cd ../TchowStrick
   ./mvnw -q install -DskipTests
   ```

2. Neste repositório:

   ```bash
   ./mvnw clean verify
   ```

   Roda os testes do cliente, o gate de cobertura de `br.com.mss.tchow.net.*` (≥ 70% de linhas) e o `spotless:check`, e gera `target/tchowstrick.jar` (jar único com gRPC/Netty).

Limitação conhecida: o `google-java-format` 1.24 usado pelo spotless não roda em JDK 27 (`NoSuchMethodError` em `com.sun.tools.javac`). Nesse JDK, use um JDK 21 para o `verify` ou pule só a checagem de formato com `-Dspotless.check.skip=true`.

## Como rodar o cliente

```bash
java -jar target/tchowstrick.jar
```

ou, direto pelo Maven:

```bash
./mvnw exec:java
```

Bancada visual do tabuleiro (classe de teste): `./mvnw test-compile exec:java@sandbox`.

### Jogar contra a IA (sem servidor)

Menu `Partida` → `Criar partida…` → em **Adversário** escolha `IA — Fácil`, `IA — Média` ou `IA — Difícil`. A partida começa na hora, em processo, sem subir servidor.

### Jogar em rede

Seleção constatada por leitura de [Main](../../src/main/java/br/com/mss/tchow/Main.java) e [ConnectionResolver](../../src/main/java/br/com/mss/tchow/ConnectionResolver.java), sem executar a interface. A [ADR-0017 do TchowStrick](../../../TchowStrick/docs/adr/0017-lista-de-servidores-e-troca-sem-ip-porta.md) é a referência do fluxo.

1. Fora de uma partida, conferir a barra `Servidor` e usar **Trocar servidor…** para escolher o destino. A busca em rede local é acionada no seletor, não automaticamente ao abrir o app.
2. No servidor oficial, preparar a conta conforme [SEG-01/02](../../../TchowStrick/docs/referencia/seg-01-02-identidade.md); perfil local não substitui autenticação. Não usar o oficial como staging sem autorização operacional.
3. Um jogador escolhe `Partida → Criar partida…`, tamanho, quantidade de jogadores (2–5), cor e opção de rede. Host e porta vêm do servidor selecionado, sem campos nos diálogos de partida.
4. Os demais escolhem `Partida → Entrar…`, buscam a partida e escolhem uma cor disponível, com senha quando houver.
5. Confirmar o início e o estado da partida conforme o roteiro da entrega; este guia não registra aprovação manual.

### Servidor sugerido ao cliente: config, CLI e descoberta em LAN (E4.5)

A ordem constatada em `ConnectionResolver` é: `--server=host:porta`, última escolha manual salva, preset padrão do catálogo, fallback `localhost`. A opção de linha de comando usa conexão local sem TLS e não identifica o oficial; não usá-la para enviar credenciais de conta.

Exemplo local, com um `ServerMain` do TchowStrick rodando na porta 5050:

```sh
java -jar target/tchowstrick.jar --server=localhost:5050
```

`servers.json` externo no diretório de execução substitui o catálogo embutido, mas não concede confiança para enviar login ao oficial; veja SEG-01/02. O [catálogo embutido](../../src/main/resources/servers-default.json) define domínio, porta 443 e TLS do oficial.

Para o servidor embarcado em rede local, o argumento é `--embedded-server`; `--port=NNNN` e `--no-discovery` controlam porta e descoberta. Rodar em rede confiável, após verificar efeitos de persistência local. O registro da [descrição anterior](../historico/conexao-desktop-anterior.md) preserva o contexto da alteração de interface.

## Arquitetura

Projeto Maven único (`tchow-client-desktop`) que consome do TchowStrick `tchow-domain` (regras puras), `tchow-proto` (codegen do `.proto`) e `tchow-server` (`net.match` e o lado servidor de `net.grpc`, usados pela IA em processo e pelo `--embedded-server`). Camadas com dependência só "para dentro" (cada pacote tem um `package-info.java`):

```
ui   (Swing, EDT)            app  (MatchController)
      \                       /
       \                     /
        net (GameTransport + DTOs; impl. gRPC em net.grpc;
             LocalTransport p/ IA em processo)
                    |
        net.match (MatchService autoritativo — vem do tchow-server)
                    |
                 domain (GameEngine, Board, GameView — vem do tchow-domain)

save (Savegame + SavegameCodec — formato de arquivo, fora do contrato de rede;
      depende de domain + domain.history e das mensagens do tchow-proto)
```

- **`save`**: `SavegameCodec` serializa `Savegame` para bytes, robusto a lixo (fuzzing com jazzer).
- **`net`**: fronteira de transporte — a UI troca só DTOs, nunca Swing. `GameTransport` tem `net.grpc` (rede) e `LocalTransport` (IA em processo).
- **`net.config`**: catálogo de servidores (`servers.json`).
- **`ui`** / **`app`**: pintura vetorial do tabuleiro e diálogos / cola transporte ↔ telas, perfil, carteira de desfazer e sessão locais.
