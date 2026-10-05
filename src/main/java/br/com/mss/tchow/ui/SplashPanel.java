package br.com.mss.tchow.ui;

import br.com.mss.tchow.domain.Board;
import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.PlayerColor;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Tela de abertura: um mini tabuleiro que se preenche sozinho em loop, com o título por cima.
 * Animação por {@link javax.swing.Timer} (roda na EDT). Um clique congela o desenho.
 */
public final class SplashPanel extends JComponent {

    private static final int COLS = 6;
    private static final int ROWS = 4;
    private static final Color BACKGROUND = new Color(0x22, 0x24, 0x2A);
    private static final Color TITLE = new Color(0xF5, 0xF5, 0xF5);
    private static final Color SUBTITLE = new Color(0xA8, 0xB0, 0xBC);
    private static final Color DOT = new Color(0x6A, 0x72, 0x80);
    private static final Color EDGE_FREE = new Color(0x3A, 0x3E, 0x48);

    private final BoardGeometry geometry = new BoardGeometry(COLS, ROWS, 34, 26, 8);
    private final Random random = new Random();
    private final List<Edge> order = new ArrayList<>();

    private Board board = new Board(COLS, ROWS);
    private int revealed;
    private int holdTicks;
    private boolean frozen;

    private final Timer timer = new Timer(90, e -> tick());

    public SplashPanel() {
        setOpaque(true);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        order.addAll(geometry.allEdges());
        reshuffle();

        Dimension boardSize = geometry.preferredSize();
        setPreferredSize(new Dimension(boardSize.width + 80, boardSize.height + 150));

        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        freeze();
                    }
                });

        timer.setInitialDelay(400);
        timer.start();
    }

    /** Para a animação com o tabuleiro cheio. Chamado ao clicar ou ao sair da tela. */
    public void freeze() {
        if (frozen) {
            return;
        }
        frozen = true;
        timer.stop();
        for (int i = revealed; i < order.size(); i++) {
            markEdge(order.get(i), colorAt(i));
        }
        repaint();
    }

    public void stop() {
        timer.stop();
    }

    private void tick() {
        if (holdTicks > 0) {
            holdTicks--;
            if (holdTicks == 0) {
                board = new Board(COLS, ROWS);
                revealed = 0;
                reshuffle();
                repaint();
            }
            return;
        }
        if (revealed >= order.size()) {
            holdTicks = 14;
            return;
        }
        markEdge(order.get(revealed), colorAt(revealed));
        revealed++;
        repaint();
    }

    private void reshuffle() {
        Collections.shuffle(order, random);
    }

    private static PlayerColor colorAt(int index) {
        PlayerColor[] colors = PlayerColor.values();
        return colors[index % colors.length];
    }

    /** Marca a aresta e pinta qualquer quadro que ela tenha fechado. */
    private void markEdge(Edge edge, PlayerColor owner) {
        board.markEdge(edge, owner);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board.owner(r, c) == null && board.isBoxComplete(r, c)) {
                    board.setOwner(r, c, owner);
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BACKGROUND);
            g2.fillRect(0, 0, getWidth(), getHeight());

            Dimension boardSize = geometry.preferredSize();
            int ox = (getWidth() - boardSize.width) / 2;
            int oy = (getHeight() - boardSize.height) / 2 + 26;
            g2.translate(ox, oy);
            paintBoard(g2);
            g2.translate(-ox, -oy);

            paintText(g2);
        } finally {
            g2.dispose();
        }
    }

    private void paintBoard(Graphics2D g2) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                PlayerColor owner = board.owner(r, c);
                if (owner != null) {
                    Rectangle rect = geometry.boxRect(r, c);
                    g2.setColor(PlayerColors.fill(owner));
                    g2.fillRect(rect.x, rect.y, rect.width, rect.height);
                }
            }
        }
        for (Edge edge : geometry.allEdges()) {
            PlayerColor owner = board.edgeOwner(edge);
            g2.setStroke(
                    new BasicStroke(
                            owner != null ? 4f : 1.5f,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
            g2.setColor(owner != null ? PlayerColors.awt(owner) : EDGE_FREE);
            Point[] segment = geometry.edgeSegment(edge);
            g2.drawLine(segment[0].x, segment[0].y, segment[1].x, segment[1].y);
        }
        g2.setColor(DOT);
        for (int r = 0; r <= ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                Point p = geometry.dotCenter(r, c);
                g2.fillOval(p.x - 3, p.y - 3, 6, 6);
            }
        }
    }

    private void paintText(Graphics2D g2) {
        g2.setColor(TITLE);
        g2.setFont(getFont().deriveFont(Font.BOLD, 34f));
        drawCentered(g2, "TchowStrick", 46);

        g2.setColor(SUBTITLE);
        g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
        drawCentered(g2, "jogo dos pontinhos", 68);
        drawCentered(g2, "use o menu “Partida” para hospedar ou entrar", getHeight() - 24);
    }

    private void drawCentered(Graphics2D g2, String text, int y) {
        int width = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, (getWidth() - width) / 2, y);
    }
}
