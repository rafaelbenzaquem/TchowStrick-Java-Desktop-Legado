package br.com.mss.tchow.sandbox;

import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.GameEngine;
import br.com.mss.tchow.domain.GameOutcome;
import br.com.mss.tchow.domain.InvalidMoveException;
import br.com.mss.tchow.domain.Move;
import br.com.mss.tchow.domain.MoveResult;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.ui.BoardView;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * Bancada de teste visual da Fase 2 — <b>não faz parte do jogo distribuído</b> (vive em {@code
 * src/test}, fora do jar).
 *
 * <p>Liga um {@link GameEngine} local a um {@link BoardView}: cada clique numa aresta livre é uma
 * jogada do jogador da vez. Serve para conferir a renderização (pontos, arestas coloridas por
 * jogador, quadros capturados, captura dupla, fim de jogo).
 *
 * <p>Rodar: {@code ./mvnw test-compile exec:java@sandbox}
 */
public final class BoardSandbox {

    private final JFrame frame = new JFrame("TchowStrick — bancada do BoardView (Fase 2)");
    private final JScrollPane boardScroll = new JScrollPane();
    private final JLabel status = new JLabel(" ");
    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 12, 1));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 12, 1));
    private final JSpinner playersSpinner = new JSpinner(new SpinnerNumberModel(3, 2, 5, 1));

    private GameEngine engine;
    private BoardView view;
    private MoveResult lastResult;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BoardSandbox().show());
    }

    private void show() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("largura:"));
        controls.add(widthSpinner);
        controls.add(new JLabel("altura:"));
        controls.add(heightSpinner);
        controls.add(new JLabel("jogadores:"));
        controls.add(playersSpinner);
        JButton restart = new JButton("Novo jogo");
        restart.addActionListener(e -> newGame());
        controls.add(restart);

        frame.setLayout(new BorderLayout(8, 8));
        frame.add(controls, BorderLayout.NORTH);
        frame.add(boardScroll, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        newGame();

        frame.setSize(760, 680);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void newGame() {
        int width = (int) widthSpinner.getValue();
        int height = (int) heightSpinner.getValue();
        int players = (int) playersSpinner.getValue();
        List<PlayerColor> order = Arrays.stream(PlayerColor.values()).limit(players).toList();

        engine = new GameEngine(width, height, order);
        lastResult = null;

        view = new BoardView(width, height);
        view.showBoard(engine.board());
        view.setEdgeClickHandler(this::play);
        boardScroll.setViewportView(view);

        updateStatus();
    }

    private void play(Edge edge) {
        try {
            lastResult = engine.applyMove(new Move(engine.currentPlayer(), edge));
        } catch (InvalidMoveException ex) {
            Toolkit.getDefaultToolkit().beep();
            status.setText("  " + ex.getMessage());
            return;
        }
        view.showBoard(engine.board());
        updateStatus();
    }

    private void updateStatus() {
        Map<PlayerColor, Integer> counts = engine.board().boxCounts();
        String scoreboard =
                engine.turnOrder().stream()
                        .map(color -> color + "=" + counts.getOrDefault(color, 0))
                        .collect(Collectors.joining("   "));

        String text;
        if (engine.isFinished() && lastResult != null && lastResult.outcome() != null) {
            GameOutcome outcome = lastResult.outcome();
            String winners =
                    outcome.winners().stream().map(Enum::name).collect(Collectors.joining(", "));
            text =
                    (outcome.draw() ? "Empate entre " : "Vencedor: ")
                            + winners
                            + "   |   placar: "
                            + scoreboard;
        } else {
            text = "Vez de " + engine.currentPlayer() + "   |   placar: " + scoreboard;
        }
        status.setText("  " + text);
    }
}
