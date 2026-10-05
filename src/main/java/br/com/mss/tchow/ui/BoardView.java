package br.com.mss.tchow.ui;

import br.com.mss.tchow.domain.Board;
import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.PlayerColor;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JComponent;

/**
 * Componente Swing que desenha um {@link Board} com pintura vetorial (sem imagens): pontos, arestas
 * coloridas pela cor de quem as marcou e quadros capturados preenchidos com a cor do dono.
 *
 * <p>É puramente visual: não conhece {@code GameEngine}, turnos nem rede. Ao clicar numa aresta
 * livre, chama o {@code edgeClickHandler} registrado.
 */
public final class BoardView extends JComponent {

    private static final Color BACKGROUND = new Color(0xFA, 0xFA, 0xFA);
    private static final Color DOT = new Color(0x37, 0x37, 0x37);
    private static final Color EDGE_FREE = new Color(0xDD, 0xDD, 0xDD);
    private static final Color EDGE_HOVER = new Color(0x90, 0x90, 0x90);
    private static final int DOT_RADIUS = 4;
    private static final BasicStroke MARKED_STROKE =
            new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke FREE_STROKE = new BasicStroke(2f);
    private static final BasicStroke HALO_STROKE =
            new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Color LAST_MOVE_HALO = new Color(0, 0, 0, 48);

    private final int boardWidth;
    private final int boardHeight;
    private final BoardGeometry geometry;

    private transient Board board;
    private transient Edge hovered;
    private transient Edge lastMove;
    private boolean interactive = true;
    private transient Consumer<Edge> edgeClickHandler = edge -> {};

    public BoardView(int boardWidth, int boardHeight) {
        this.boardWidth = boardWidth;
        this.boardHeight = boardHeight;
        this.geometry = new BoardGeometry(boardWidth, boardHeight);
        setOpaque(true);
        setBackground(BACKGROUND);

        MouseAdapter mouse =
                new MouseAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        Edge next = freeEdgeAt(e.getPoint());
                        if (!Objects.equals(next, hovered)) {
                            hovered = next;
                            repaint();
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        if (hovered != null) {
                            hovered = null;
                            repaint();
                        }
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        Edge edge = freeEdgeAt(e.getPoint());
                        if (edge != null) {
                            edgeClickHandler.accept(edge);
                        }
                    }
                };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Troca o tabuleiro exibido e repinta. */
    public void showBoard(Board board) {
        this.board = board;
        this.hovered = null;
        repaint();
    }

    /** Destaca (ou limpa, com {@code null}) a última aresta marcada. */
    public void highlightLastMove(Edge edge) {
        this.lastMove = edge;
        repaint();
    }

    /** Quando {@code false}, ignora hover e cliques (partida não iniciada ou terminada). */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            this.hovered = null;
        }
        repaint();
    }

    public void setEdgeClickHandler(Consumer<Edge> handler) {
        this.edgeClickHandler = Objects.requireNonNull(handler);
    }

    @Override
    public Dimension getPreferredSize() {
        return geometry.preferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            paintCapturedBoxes(g2);
            paintEdges(g2);
            paintDots(g2);
        } finally {
            g2.dispose();
        }
    }

    private void paintCapturedBoxes(Graphics2D g2) {
        if (board == null) {
            return;
        }
        for (int r = 0; r < boardHeight; r++) {
            for (int c = 0; c < boardWidth; c++) {
                PlayerColor owner = board.owner(r, c);
                if (owner != null) {
                    Rectangle rect = geometry.boxRect(r, c);
                    g2.setColor(PlayerColors.fill(owner));
                    g2.fillRect(rect.x, rect.y, rect.width, rect.height);
                }
            }
        }
    }

    private void paintEdges(Graphics2D g2) {
        for (Edge edge : geometry.allEdges()) {
            PlayerColor owner = board == null ? null : board.edgeOwner(edge);
            Point[] segment = geometry.edgeSegment(edge);

            if (owner != null && edge.equals(lastMove)) {
                g2.setStroke(HALO_STROKE);
                g2.setColor(LAST_MOVE_HALO);
                g2.drawLine(segment[0].x, segment[0].y, segment[1].x, segment[1].y);
            }

            if (owner != null) {
                g2.setStroke(MARKED_STROKE);
                g2.setColor(PlayerColors.awt(owner));
            } else if (edge.equals(hovered)) {
                g2.setStroke(MARKED_STROKE);
                g2.setColor(EDGE_HOVER);
            } else {
                g2.setStroke(FREE_STROKE);
                g2.setColor(EDGE_FREE);
            }
            g2.drawLine(segment[0].x, segment[0].y, segment[1].x, segment[1].y);
        }
    }

    private void paintDots(Graphics2D g2) {
        g2.setColor(DOT);
        for (int r = 0; r <= boardHeight; r++) {
            for (int c = 0; c <= boardWidth; c++) {
                Point p = geometry.dotCenter(r, c);
                g2.fillOval(p.x - DOT_RADIUS, p.y - DOT_RADIUS, 2 * DOT_RADIUS, 2 * DOT_RADIUS);
            }
        }
    }

    private Edge freeEdgeAt(Point point) {
        if (!interactive) {
            return null;
        }
        return geometry.edgeAt(point)
                .filter(edge -> board == null || board.edgeOwner(edge) == null)
                .orElse(null);
    }
}
