package br.com.mss.tchow.ui;

import br.com.mss.tchow.domain.GameEngine;
import br.com.mss.tchow.domain.Move;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.domain.history.BoardSpec;
import br.com.mss.tchow.domain.history.MoveLog;
import br.com.mss.tchow.domain.history.ReplayCursor;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * Janela de replay: navega uma partida gravada jogada a jogada (início/voltar/play/avançar/fim +
 * uma barra). Só desenha — usa o {@link ReplayCursor}, que reconstrói o estado por replay do log.
 */
public final class ReplayViewer extends JDialog {

    private static final int STEP_MS = 900;

    private final ReplayCursor cursor;
    private final BoardSpec spec;
    private final Set<PlayerColor> allSeats;

    private final BoardView board;
    private final PlayersPanel players = new PlayersPanel();
    private final JLabel position = new JLabel(" ");
    private final JSlider slider;
    private final JButton first = new JButton("|◀");
    private final JButton back = new JButton("◀");
    private final JButton play = new JButton("▶");
    private final JButton forward = new JButton("▶");
    private final JButton last = new JButton("▶|");
    private final Timer timer;

    public ReplayViewer(Window owner, String title, BoardSpec spec, MoveLog log) {
        super(owner, title, ModalityType.MODELESS);
        this.spec = spec;
        this.cursor = new ReplayCursor(spec, log);
        this.allSeats = Set.copyOf(spec.turnOrder());
        this.board = new BoardView(spec.width(), spec.height());
        this.board.setInteractive(false);
        this.slider = new JSlider(0, cursor.total(), cursor.index());
        this.timer = new Timer(STEP_MS, e -> tick());

        buildUi();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        render();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        first.addActionListener(e -> jump(cursor::first));
        back.addActionListener(e -> jump(cursor::back));
        forward.addActionListener(e -> jump(cursor::forward));
        last.addActionListener(e -> jump(cursor::last));
        play.addActionListener(e -> togglePlay());

        slider.addChangeListener(
                e -> {
                    if (slider.getValue() != cursor.index()) {
                        stopPlay();
                        cursor.seek(slider.getValue());
                        render();
                    }
                });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        buttons.add(first);
        buttons.add(back);
        buttons.add(play);
        buttons.add(forward);
        buttons.add(last);

        JPanel south = new JPanel(new BorderLayout(6, 0));
        south.add(buttons, BorderLayout.WEST);
        south.add(slider, BorderLayout.CENTER);
        south.add(position, BorderLayout.EAST);

        setLayout(new BorderLayout(6, 6));
        add(players, BorderLayout.NORTH);
        add(new JScrollPane(board), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void jump(Runnable move) {
        stopPlay();
        move.run();
        render();
    }

    private void tick() {
        if (cursor.canForward()) {
            cursor.forward();
            render();
        } else {
            stopPlay();
        }
    }

    private void togglePlay() {
        if (timer.isRunning()) {
            stopPlay();
            return;
        }
        if (!cursor.canForward()) {
            cursor.first(); // no fim: rebobina e toca de novo
            render();
        }
        timer.start();
        play.setText("⏸");
    }

    private void stopPlay() {
        timer.stop();
        play.setText("▶");
    }

    private void render() {
        GameEngine engine = cursor.state();
        board.showBoard(engine.board());
        Move lastMove = cursor.lastMove();
        board.highlightLastMove(lastMove == null ? null : lastMove.edge());

        players.render(
                spec.turnOrder(),
                allSeats,
                engine.scores(),
                engine.currentPlayer(),
                engine.isFinished(),
                null);

        position.setText("  jogada " + cursor.index() + " / " + cursor.total() + "  ");
        first.setEnabled(cursor.canBack());
        back.setEnabled(cursor.canBack());
        forward.setEnabled(cursor.canForward());
        last.setEnabled(cursor.canForward());
        play.setEnabled(cursor.total() > 0);
        if (slider.getValue() != cursor.index()) {
            slider.setValue(cursor.index());
        }
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }
}
