# Operação local e arquitetura — TchowStrick Java Desktop (legado)

Conteúdo do cliente extraído de `TchowStrick/docs/operacao/local.md` em 04/10/2026 ([ADR-0020 do TchowStrick](../../../TchowStrick/docs/adr/0020-cliente-desktop-em-repositorio-legado.md)). Servidor, containers, deploy e reidratação continuam documentados na [operação do TchowStrick](../../../TchowStrick/docs/operacao/local.md).

O preset oficial atual declara domínio, porta 443, TLS (segurança da conexão) e, desde 05/10/2026, a identidade MSS de produção `identity.minashonsoftware.com.br:443` com TLS: [servers-default.json](../../src/main/resources/servers-default.json). Exemplos localhost são apenas locais. A referência atual de seleção é a [ADR-0017 do TchowStrick](../../../TchowStrick/docs/adr/0017-lista-de-servidores-e-troca-sem-ip-porta.md). Conferir a interface na validação humana.

Comandos a executar na raiz deste repositório, salvo indicação. Builds podem baixar dependências e escrevem no repositório Maven local do usuário; operações que escrevem fora do portfólio exigem registrar impedimento conforme as [regras comuns](../../../AGENTS.md). Operação de produção é exclusiva do responsável autorizado.

## Requisitos

- JDK 21+ (o código compila com `release 21`).
- Checkout do [TchowStrick](../../../TchowStrick/README.md) ao lado deste repositório, na versão indicada em [compatibilidade](../compatibilidade.md).
- `identity-client-java` do [MSSIdentity](../../../MSSIdentity/README.md) instalado no Maven local (desde o [M1](../marcos/M01-identidade-mss.md); ver [compatibilidade](../compatibilidade.md#identidade-mss)).
- Maven (ou o wrapper `./mvnw` incluído).
- Docker **não** é necessário: os testes deste repositório sobem o servidor em memória, no próprio processo.

## Build

1. No TchowStrick, instalar `tchow-domain`, `tchow-proto` e `tchow-server` no repositório Maven local (os testes do servidor exigem Docker; por isso são pulados aqui):

   ```bash
   cd ../TchowStrick
   ./mvnw -q install -DskipTests
   ```

2. No MSSIdentity (branch que contém o módulo `identity-client-java`), instalar o cliente Java da identidade:

   ```bash
   cd ../MSSIdentity
   ./mvnw -q install -DskipTests
   ```

3. Neste repositório:

   ```bash
   ./mvnw clean verify
   ```

   Roda os testes do cliente, o gate de cobertura de `br.com.mss.tchow.net.*` (≥ 70% de linhas) e o `spotless:check`, e gera `target/tchowstrick.jar` (jar único com gRPC/Netty).

Funciona em JDK 21 e 27: spotless 3.10.3 com google-java-format 1.36.1 (até 04/10/2026, a 1.24 quebrava no JDK 27 e exigia `-Dspotless.check.skip=true`; TchowStrick:BUG-016). No TchowStrick, rode `./mvnw install` completo ou com `-DskipTests`; o spotless do agregador também passa nos dois JDKs.

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
2. No servidor oficial, entrar com a conta MSS (`Jogador → Conta MSS…`, [M1](../marcos/M01-identidade-mss.md)); a conta oficial antiga ([SEG-01/02](../../../TchowStrick/docs/referencia/seg-01-02-identidade.md)) não joga mais no oficial. Perfil local não substitui autenticação. Não usar o oficial como staging sem autorização operacional.
3. Um jogador escolhe `Partida → Criar partida…`, tamanho, quantidade de jogadores (2–5), cor e opção de rede. Host e porta vêm do servidor selecionado, sem campos nos diálogos de partida.
4. Os demais escolhem `Partida → Entrar…`, buscam a partida e escolhem uma cor disponível, com senha quando houver.
5. Confirmar o início e o estado da partida conforme o roteiro da entrega; este guia não registra aprovação manual.

### Servidor sugerido ao cliente: config, CLI e descoberta em LAN (E4.5)

A ordem constatada em `ConnectionResolver` é: `--server=host:porta`, última escolha manual salva, preset padrão do catálogo, fallback `localhost`. A opção de linha de comando usa conexão local sem TLS e não identifica o oficial; não usá-la para enviar credenciais de conta.

Exemplo local, com um `ServerMain` do TchowStrick rodando na porta 5050:

```sh
java -jar target/tchowstrick.jar --server=localhost:5050
```

`servers.json` externo no diretório de execução substitui o catálogo embutido, mas não concede confiança para enviar login ao oficial; veja SEG-01/02. O [catálogo embutido](../../src/main/resources/servers-default.json) define domínio, porta 443, TLS e identidade MSS do oficial; um `servers.json` externo ou uma escolha salva que apontem para o oficial sem identidade passam a usar a identidade do catálogo embutido.

Para o servidor embarcado em rede local, o argumento é `--embedded-server`; `--port=NNNN` e `--no-discovery` controlam porta e descoberta. Rodar em rede confiável, após verificar efeitos de persistência local. O registro da [descrição anterior](../historico/conexao-desktop-anterior.md) preserva o contexto da alteração de interface.

### Conta MSS (identidade local)

Desde o [M1](../marcos/M01-identidade-mss.md), um servidor pode declarar um destino de identidade MSS (`identity` no `servers.json`, ou `--identity=` na linha de comando). Nesse servidor o jogador entra com a conta MSS e as chamadas de jogo levam o acesso de jogo da identidade (audiência `tchowstrick`); nos demais (LAN, embutido) nada muda. Desde 05/10/2026 o oficial embutido também usa a identidade (produção). Execução ponta a ponta contra o ambiente local verificada em 05/10/2026 ([M1](../marcos/M01-identidade-mss.md#verificações)).

Pré-requisitos (dados sintéticos, só local):

1. Serviço de identidade do MSSIdentity em `localhost:9100` (gRPC sem TLS) com o Mailpit do compose local (UI em http://localhost:8025, onde chegam os códigos), conforme a operação local do [MSSIdentity](../../../MSSIdentity/README.md).
2. `ServerMain` do TchowStrick em `localhost:5050` aceitando o acesso da identidade (modo `remote` ou `hybrid`, audiência `tchowstrick`), conforme a [operação do TchowStrick](../../../TchowStrick/docs/operacao/local.md).

Executar o cliente (escolha uma forma):

```sh
# a) lista de servidores de desenvolvimento do repositório (Local com conta MSS + Local sem conta)
java -Dtchow.servers.file=config/servers-local-identidade.json -jar target/tchowstrick.jar

# b) linha de comando (tem prioridade sobre a escolha salva)
java -jar target/tchowstrick.jar --server=localhost:5050 --identity=localhost:9100 --identity-plaintext
```

No PowerShell, cite a propriedade: `java "-Dtchow.servers.file=config/servers-local-identidade.json" -jar target/tchowstrick.jar`. Na forma (a), uma escolha de servidor salva anteriormente prevalece sobre o padrão do arquivo: use **Trocar servidor…** e escolha "Local (conta MSS)".

No cliente: a barra mostra `Servidor: … (conta MSS)`; `Jogador → Conta MSS…` entra ou cria a conta (nick + e-mail → código do Mailpit), mostra o estado (provisória, ativa ou restrita), edita nick/avatar e sai deste dispositivo ou de todos; `Confirmar contato…` e `Recuperar conta…` usam a identidade. Criar ou entrar em partida pede a conta MSS se ainda não houver sessão.

Regras: `--identity=` exige `--server=`; texto claro (`--identity-plaintext` ou `"identityTls": false`) só é aceito para `localhost`/loopback, e credenciais de conta só trafegam em claro para `localhost`. Staging/produção usam TLS (`--identity=host:443` ou `"identity": "host:443"` em um `servers.json`); o catálogo embutido aponta o oficial para `identity.minashonsoftware.com.br:443`. A sessão MSS fica nas `Preferences` do usuário (no Windows, registro do usuário), separada por perfil local e por destino de identidade; tokens não são registrados em log.

### Várias janelas e perfis locais (`--perfil`)

Cada janela do cliente usa um **perfil local de dados** próprio, travado enquanto ela estiver aberta (arquivo `~/.tchowstrick/perfis/<nome>.lock`; outro diretório com `-Dtchow.data.dir=`). Nele ficam a sessão MSS, os tokens de assento das partidas, os perfis de jogador, a carteira, a sessão da conta oficial antiga e o identificador de dispositivo da identidade. A escolha de servidor é comum a todas as janelas.

- Sem opção: a 1ª janela usa o perfil `padrao` (os dados que já existiam antes dos perfis); as seguintes, o próximo livre (`perfil-2`, `perfil-3`, …, até 20). Fora do padrão, o título mostra `[perfil local: perfil-2]` e a barra "Conta MSS: … · perfil local: …".
- `--perfil=<nome>` (até 32 letras sem acento, dígitos, `-` ou `_`; `padrão` = `padrao`) abre aquele perfil; se ele já estiver aberto em outra janela, o cliente mostra "O perfil local … já está aberto em outra janela" e não abre.
- Duas janelas nunca compartilham a mesma sessão MSS; para jogar com duas contas no mesmo computador, basta abrir o jar duas vezes e entrar com uma conta em cada.

```sh
# janela 1 (perfil padrão) e janela 2 (perfil-2), contra o servidor oficial (produção)
java -jar target/tchowstrick.jar
java -jar target/tchowstrick.jar
# ou com nomes fixos
java -jar target/tchowstrick.jar --perfil=ana
java -jar target/tchowstrick.jar --perfil=bia
```

`Jogador → Gerenciar contas…` lista e remove deste computador as contas MSS, a conta oficial antiga, os perfis de jogador e os perfis locais, e abre uma **nova janela** em outro perfil; detalhes no [M1](../marcos/M01-identidade-mss.md#preset-oficial-várias-contas-e-gerenciar-contas-05102026). Remover não exclui nada no servidor; perfis abertos em outra janela não podem ser alterados.

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
- **`net.config`**: catálogo de servidores (`servers.json`), com destino de identidade MSS opcional (`IdentityTarget`).
- **`net`/`net.grpc`** (credencial de conta): `AccountCredentials` consultada a cada chamada; `UNAUTHENTICATED` gera uma nova tentativa com credencial renovada.
- **`ui`** / **`app`**: pintura vetorial do tabuleiro e diálogos / cola transporte ↔ telas, perfil, carteira de desfazer e sessão locais. Conta MSS: porta `IdentityAccountGateway` (adaptador `IdentityClientGateway` sobre o `identity-client-java`), fluxos em `MssAccountFlow`, sessão em `LocalIdentitySessionStore`. Perfil local por janela: `DataProfile` (trava e nós de Preferences); painel de contas locais: `LocalAccountsService` + `ui.ManageAccountsDialog`.
