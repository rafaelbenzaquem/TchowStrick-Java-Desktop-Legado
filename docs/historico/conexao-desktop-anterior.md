# Conexão desktop — descrição anterior

Movido de `TchowStrick/docs/historico/` em 04/10/2026 ([ADR-0020 do TchowStrick](../../../TchowStrick/docs/adr/0020-cliente-desktop-em-repositorio-legado.md)); caminhos `client-desktop/...` abaixo são do repositório de origem, na época. Trecho herdado do README, substituído por [operação atual](../operacao/local.md). Campos de host/porta e descoberta automática abaixo descrevem a interface anterior. Não usar como roteiro vigente.

### Jogar em rede

Por padrão, `Criar partida` **cria a partida num `ServerMain` externo**
(`CreateMatch`). Ao abrir, o cliente já resolve sozinho qual servidor usar
(ver [Servidor sugerido](../operacao/local.md#servidor-sugerido-ao-cliente-config-cli-e-descoberta-em-lan-e45),
abaixo) e mostra `Servidor: <nome>` na tela inicial — a seleção atual segue a ADR-0017 (lista de servidores). A descrição detalhada abaixo dos antigos campos de host/porta precisa ser conferida na interface antes de uso. Para reproduzir o modo LAN antigo
(o próprio desktop sobe um servidor embutido), use a flag:

```bash
java -jar client-desktop/target/tchowstrick.jar --embedded-server [--port=NNNN]
```

1. **Um** jogador cria a partida (`Partida → Criar partida…`): confirma (ou
   troca) endereço/porta do `ServerMain` (a menos que use
   `--embedded-server`), tamanho do tabuleiro, **número de jogadores** (2 a
   5) e **sua cor** — livre entre as 5 (vermelho, azul, verde, amarelo,
   rosa), sem reservar nenhuma outra —, adversário (`Humano (em rede)`),
   nick e, opcionalmente, uma senha.
2. Os demais entram (`Partida → Entrar…`): confirmam host/porta, clicam
   `Buscar partidas` (lista as partidas abertas naquele servidor), escolhem
   uma e a própria cor entre as que ainda sobraram (a lista encolhe a cada
   jogador que entra) e senha se houver.
3. A partida começa quando todos os assentos do elenco conectam.

### Servidor sugerido ao cliente: config, CLI e descoberta em LAN ([E4.5](../../../TchowStrick/docs/PLANO.md))

Ao abrir (fora do `--embedded-server`), o cliente resolve sozinho qual
servidor sugerir — sem pedir host/porta em nenhum diálogo separado — em
ordem:

1. `--server=host:porta` na linha de comando — ignora tudo abaixo, inclusive
   a descoberta em LAN:
   ```bash
   java -jar client-desktop/target/tchowstrick.jar --server=localhost:5050
   ```
2. O preset marcado `"default": true` em `servers.json` — um arquivo externo
   nesse nome, no diretório de onde o `.jar` é executado, **substitui** a
   lista embutida por completo (não faz merge). Sem arquivo externo, vale o
   embutido no `.jar`, cujo preset oficial deve ser consultado no arquivo `client-desktop/src/main/resources/servers-default.json`. Exemplo de `servers.json`:
   ```json
   [
     { "name": "Meu servidor", "host": "192.168.0.10", "port": 5050, "default": true }
   ]
   ```
3. **~1 segundo depois**, em paralelo (não trava a tela inicial): se a
   descoberta em LAN (broadcast UDP) achar um servidor diferente do preset
   acima, aparece um diálogo perguntando se quer usá-lo em vez do padrão —
   só troca se você confirmar. Não achando nada, ou recusando a troca, fica
   valendo o preset do passo 2.

Em qualquer caso os campos de host/porta continuam **editáveis** nos
diálogos de `Criar partida…`/`Entrar…` — o que muda é que agora, na maioria
das vezes, não é preciso tocar neles.

No modo `--embedded-server`, `--port=NNNN` escolhe a porta do servidor
embutido (senão `5050`). Servidores locais (`--embedded-server` e o
`ServerMain` standalone) respondem, por padrão, a buscas por servidor na
rede local (broadcast UDP na porta `5051`) — `--no-discovery` desliga isso,
pra quem não quer que a própria máquina apareça pra outros na LAN.

> A porta precisa estar liberada no firewall de quem cria a partida/roda o
> `ServerMain`. O servidor "Oficial" (`servers.json` embutido) já fala TLS de
> verdade (E5, atrás de um Caddy com certificado Let's Encrypt automático,
> `docs/adr/0005`); LAN/`--embedded-server`/`ServerMain` locais continuam em
> texto claro (`InsecureChannelCredentials`) — use-os só em rede confiável.

