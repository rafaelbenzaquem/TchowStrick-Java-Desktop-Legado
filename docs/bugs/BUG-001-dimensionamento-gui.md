---
id: BUG-001
tipo: bug
titulo: GUI Swing com botões cortados, texto sobre o desenho, chips/botões sumindo e janelas maiores que a tela
status: validado
severidade: S3
prioridade: P1
depende_de: []
relacionados: [M1]
evidencia: verificado
branch: fix/dimensionamento-gui; ajuste em fix/largura-tela-inicial
integracao: "integrado (PR #4, e856296); ajuste da largura da tela inicial pelo PR #6 (f919144)"
validacao: "aprovada pelo responsável em 08/10/2026, com a largura da tela inicial aumentada em 200 px"
versao:
atualizado_em: 2026-10-09
---

# BUG-001 — Dimensionamento da GUI

## Classificação

| Campo | Valor |
|---|---|
| Severidade | S3 — há contorno (redimensionar a janela, rolar), mas botões e textos ficam escondidos |
| Prioridade | P1 |
| Origem | revisão de todas as telas a pedido do responsável (05/10/2026) |

## Ambiente

Cliente Swing com o Look and Feel do sistema (Windows 11), JDK 21, base `feature/preset-oficial-e-multicontas` @ `cf0c021`. Escalas 1.0, 1.25, 1.5 e 2.0 (`-Dsun.java2d.uiScale`) e tela simulada de 1366×768.

## Reprodução e observado (antes)

Capturas geradas pelo harness `src/test/java/br/com/mss/tchow/sandbox/GuiShots.java` (dados sintéticos longos, sem rede, preferências em memória). Imagens fora do repositório, em `.wt/gui-shots/antes/` e `.wt/gui-shots/depois/`.

| Tela | Problema observado |
|---|---|
| Janela inicial | Tamanho fixo 480×340, menor que o preferido; "Trocar perfil…" e "Trocar servidor…" iam para uma 2ª linha do `FlowLayout` fora da área (cortados); título, subtítulo e dica da tela de abertura desenhados por cima do tabuleiro, que passava da borda inferior |
| Partida | Tabuleiro com célula fixa de 56 px: 2x2 encolhido no canto, 12x12 com janela de 998×901 (maior que telas 1366×768, e que 1920×1080 a 150%/200%), barras de rolagem; com 5 jogadores em janela estreita os chips quebravam para uma linha invisível; status e placar dividiam a linha com os botões e eram cortados com "…" |
| Replay | Mesmos problemas do tabuleiro; 12x12 maior que a tela |
| Criar partida | Combo "Humano (em re…" truncado (Windows L&F); campo de nick estreito |
| Entrar numa partida | Diálogo fixo; mensagens de erro de rede longas desarrumavam o formulário |
| Trocar servidor | Sem margem (botões colados na borda superior), fixo, nomes longos cortados |
| Perfil do jogador | Combo truncava nomes longos |
| Gerenciar contas | Largura fixa de 900 px; colunas cortadas com "…"; linhas de botões sumindo em janela estreita; maior que a tela a 200% |
| Criar ou acessar conta | 184 px de largura, campos cortando o texto, sem margem |
| Confirmar contato, Conta MSS | Sem margem; contato/estado longos esticavam o diálogo sem limite |
| Mensagens (`JOptionPane`) | Textos de erro/aviso numa linha só: 900–1150 px, além da área útil em 150%/200% |
| Remover deste computador | `JTextArea` com quebra mostrava só a 1ª linha (altura calculada sem largura), em fonte monoespaçada |

## Causa-raiz

Tamanhos fixos (`setSize(480, 340)`, `setPreferredSize` em pixels), `FlowLayout` em barras que precisam quebrar linha (o preferido dele é sempre uma linha), desenho customizado com coordenadas fixas (sem `FontMetrics` nem escala pelo tamanho do componente), janelas empacotadas sem limite da área útil da tela, textos longos em rótulos/mensagens sem quebra e a folga do `JComboBox` no L&F do Windows menor que a desenhada.

## Correção

- `UiSizing`: `packWithin` (empacota, limita à área útil da tela, mínimo do layout, centraliza), mensagens longas com quebra em ~420 px lógicos, coluna de formulário e margem padrão.
- `WrapLayout`: `FlowLayout` que reserva a altura das linhas quebradas (barras da tela inicial, chips de jogadores, botões da partida e do Gerenciar contas).
- `FittingComboBox`: combo com folga para o item mais longo.
- `BoardView`/`BoardGeometry.fitting`: célula entre 24 e 96 px que cabe no componente, centralizada, traços e pontos proporcionais; dentro do `JScrollPane` só rola abaixo do mínimo.
- `SplashPanel`: título, subtítulo e dica pelas métricas da fonte; tabuleiro na faixa livre.
- Diálogos com margens, campos esticando na coluna, listas que crescem com a janela (Entrar, Trocar servidor), tabela do Gerenciar contas com colunas pelo conteúdo, rolagem horizontal e dica com o texto completo; todas as mensagens do `Main` passam por `UiSizing.message`.

Textos funcionais e comportamento mantidos; mudanças de disposição: na partida, o status fica numa linha própria acima dos botões (Revanche passa a ficar junto de Desfazer/Refazer); Entrar e Trocar servidor passaram a ser redimensionáveis.

## Verificações

- Harness de capturas nas 4 escalas, antes/depois (`.wt/gui-shots/run.sh antes|depois`); diagnóstico automático em `relatorio-<escala>.txt` sem componentes cortados nem sobrepostos (exceto o botão interno da seta do combo do Windows, 19 vs 21 px, sem efeito visível).
- Testes headless `GuiSizingTest` (geometria ajustada, tabuleiro acompanha o viewport, `WrapLayout`, chips, quebra de mensagens, limite à área útil) e `./mvnw -B -o clean verify`.
- Não verificado visualmente: diálogos que só abrem com rede ou estado real (fluxos de conta contra a identidade, `JFileChooser`, diálogos de entrada simples); suas mensagens usam o mesmo `UiSizing.message` coberto pelo harness. Barra de título (decoração do sistema) não aparece nas capturas.

## Validação manual

**Aprovada pelo responsável em 08/10/2026** (documentado): tamanhos de janelas e diálogos conferidos. Ajuste feito pelo responsável durante a validação: `SplashPanel.getPreferredSize` ganha 200 px de largura (`+ 4 * GAP + 200`), deixando a janela principal mais larga na tela inicial; registrado na branch `fix/largura-tela-inicial`.

Roteiro usado:

Rodar o cliente desta branch (`./mvnw -o package -DskipTests` e `java -jar target/tchowstrick.jar`; para 200%: `java -Dsun.java2d.uiScale=2 -jar target/tchowstrick.jar`) e conferir:

1. Tela inicial: os três botões da barra visíveis; título, subtítulo e dica sem tocar o tabuleiro; encolher a janela quebra os botões em linhas visíveis.
2. Criar partida contra a IA 12x12 com 5 jogadores: janela cabe na tela; tabuleiro inteiro visível e centralizado; ao redimensionar, o tabuleiro acompanha; chips quebram linha em janela estreita; status completo acima dos botões.
3. Criar partida 2x2: tabuleiro centralizado, sem ficar no canto.
4. Diálogos Criar partida, Entrar, Trocar servidor, Perfil, Conta MSS, Gerenciar contas: nenhum texto ou botão cortado; Gerenciar contas com rolagem horizontal quando as colunas não cabem.
5. Provocar um erro de rede (servidor desligado) em Entrar → Buscar partidas e em Criar partida: a mensagem quebra linha e a janela não passa da tela.
