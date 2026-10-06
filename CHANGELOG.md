# Changelog — TchowStrick Java Desktop (legado)

Mudanças relevantes do produto. Formato: [Keep a Changelog 1.1](https://keepachangelog.com/pt-BR/1.1.0/); versões: [SemVer 2.0](https://semver.org/lang/pt-BR/). Cada entrada cita o marco, item, bug ou ADR. Padrão: [documentação MSS](../docs/padroes/documentacao.md) §7.10.

Versões anteriores do cliente desktop foram publicadas junto do servidor, no [CHANGELOG do TchowStrick](../TchowStrick/CHANGELOG.md) (`v1.0.0`…`v1.2.6`); não são repetidas aqui.

## [Não publicado]

### Alterado
- O servidor oficial embutido usa a identidade MSS de produção (`identity.minashonsoftware.com.br:443`, TLS): no oficial só há conta MSS; o fluxo de conta oficial antigo (`tchowstrick.auth.v1`) fica para servidores sem identidade. Escolha salva ou `servers.json` que apontem para o oficial sem identidade passam a usar o preset novo (M1).

### Corrigido
- Recusas de conta no servidor com identidade MSS deixam de virar "Sessão inválida ou expirada… Criar ou acessar conta…" (que levava ao laço "confirme seu e-mail"): a mensagem local é preservada e cada caso tem a sua ação — entrar na conta MSS, acesso recusado pelo servidor, conta restrita (Confirmar contato), conta antiga não aceita (Conta MSS) e identidade indisponível; o texto antigo fica só para servidores sem identidade (BUG-002).
- Estado da conta MSS consultado na identidade ao abrir Conta MSS e antes de jogar (contato confirmado, carência de 1 h), recusa por contato vinda do servidor marca a conta como restrita e oferece "Confirmar contato"; "Gerenciar contas" indica "último estado conhecido"; o aviso de cadastro e o reenvio não afirmam mais que o código foi enviado (BUG-003).
- Dimensionamento da GUI em todas as telas: barras e chips que quebram linha sem sumir, tela de abertura sem texto sobre o desenho, tabuleiro que escala com a janela e fica centralizado, janelas e diálogos limitados à área útil da tela (inclusive 1366×768 e escalas 125–200%), combos e campos sem texto truncado, tabela do Gerenciar contas com colunas pelo conteúdo, mensagens longas com quebra de linha e o aviso de remoção completo (BUG-001).
- Duas janelas do cliente no mesmo usuário do SO compartilhavam a sessão MSS e os tokens de assento: o login da 2ª trocava a conta da 1ª, a cor da 1ª aparecia travada como "Retornar" na 2ª e a rotação simultânea da mesma sessão fazia a identidade revogar as duas. Cada janela agora usa um perfil local próprio, travado por arquivo (`padrao`, `perfil-2`, … ou `--perfil=<nome>`); o perfil padrão mantém os dados já salvos (M1).
- `./mvnw verify` falhava no spotless em JDK 27: spotless 3.10.3 e google-java-format 1.36.1, compatíveis com JDK 21 e 27 (TchowStrick:BUG-016).

### Adicionado
- `--perfil=<nome>`, conta MSS e perfil local visíveis na barra e no título, e "Sair/Trocar de conta MSS…" (M1).
- `Jogador → Gerenciar contas…`: lista, adiciona (fluxos existentes e nova janela) e remove deste computador contas MSS, conta oficial antiga, perfis de jogador e perfis locais, com confirmação e sem excluir nada no servidor; saída da conta MSS no servidor (este dispositivo) opcional (M1).
- Conta MSS por servidor (M1, MSSIdentity:M4-04): servidor com destino de identidade (`identity` no `servers.json` ou `--identity=`/`--identity-plaintext` junto de `--server=`) usa entrar/criar conta com e-mail e código, recuperar, estado da conta, perfil (nick/avatar) e sair deste dispositivo/de todos; as chamadas de jogo levam o acesso de jogo `tchowstrick`, renovado a cada chamada, com uma nova tentativa em `UNAUTHENTICATED`. Sessão local separada por destino de identidade. Servidores sem identidade seguem com o fluxo anterior. Lista de desenvolvimento local em `config/servers-local-identidade.json`.
- Dependência `br.com.mss.identity:identity-client-java:1.0-SNAPSHOT` (M1).
- Repositório próprio do cliente desktop Swing, extraído do módulo `client-desktop` do TchowStrick `main` @ `b5ff8e1` sem alteração de código; projeto Maven autônomo que consome `tchow-domain`, `tchow-proto` e `tchow-server` instalados no repositório Maven local (M0; TchowStrick:ADR-0020).
- Marco M1 (identidade MSS no desktop, MSSIdentity:M4-04) registrado: as mudanças de autenticação do cliente passam a ser feitas neste repositório (M1).
- Documentação do cliente: operação local e arquitetura, compatibilidade com o TchowStrick, roteiro histórico `E4d-04` e descrição anterior da conexão, movidos do TchowStrick (M0).
