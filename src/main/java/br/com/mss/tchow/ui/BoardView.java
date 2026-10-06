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
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * Componente Swing que desenha um {@link Board} com pintura vetorial (sem imagens): pontos, arestas
 * coloridas pela cor de quem as marcou e quadros capturados preenchidos com a cor do dono.
 *
 * <p>É puramente visual: não conhece {@code GameEngine}, turnos nem rede. Ao clicar numa aresta
 * livre, chama o {@code edgeClickHandler} registrado.
 *
 * <p>O desenho acompanha o tamanho do componente: a célula cresce ou encolhe (entre {@link
 * #MIN_CELL} e {@link #MAX_CELL}) para caber, centralizada, e traços/pontos escalam junto. Dentro
 * de um {@code JScrollPane}, só aparecem barras de rolagem abaixo do tamanho mínimo.
 */
public final class BoardView extends JComponent implements Scrollable {

    /** Menor célula (pixels lógicos) antes de o tabuleiro passar a rolar. */
    static final int MIN_CELL = 24;

    /** Maior célula: tabuleiros pequenos em janelas grandes não viram quadros gigantes. */
    static final int MAX_CELL = 96;

    private static final Color BACKGROUND = new Color(0xFA, 0xFA, 0xFA);
    private static final Color DOT = new Color(0x37, 0x37, 0x37);
    private static final Color EDGE_FREE = new Color(0xDD, 0xDD, 0xDD);
    private static final Color EDGE_HOVER = new Color(0x90, 0x90, 0x90);
    private static final Color LAST_MOVE_HALO = new Color(0, 0, 0, 48);

    private final int boardWidth;
    private final int boardHeight;

    /** Geometria no tamanho padrão: define o tamanho preferido. */
    private final BoardGeometry preferredGeometry;

    private transient Board board;
    private transient Edge hovered;
    private transient Edge lastMove;
    private boolean interactive = true;
    private transient Consumer<Edge> edgeClickHandler = edge -> {};

    public BoardView(int boardWidth, int boardHeight) {
        this.boardWidth = boardWidth;
        this.boardHeight = boardHeight;
        this.preferredGeometry = new BoardGeometry(boardWidth, boardHeight);
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
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        // Num JScrollPane, o tamanho pedido ao painel vem de getPreferredScrollableViewportSize;
        // aqui vale o mínimo, que é o tamanho usado na direção em que o viewport não comporta o
        // tabuleiro (rola com a menor célula, em vez de ficar enorme e centralizado fora da vista).
        return getParent() instanceof JViewport
                ? getMinimumSize()
                : preferredGeometry.preferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        return BoardGeometry.fitting(boardWidth, boardHeight, 0, 0, MIN_CELL, MIN_CELL)
                .preferredSize();
    }

    /** Geometria para o tamanho atual do componente (centralizada por {@link #origin}). */
    BoardGeometry currentGeometry() {
        int w = getWidth() > 0 ? getWidth() : getPreferredSize().width;
        int h = getHeight() > 0 ? getHeight() : getPreferredSize().height;
        return BoardGeometry.fitting(boardWidth, boardHeight, w, h, MIN_CELL, MAX_CELL);
    }

    /** Deslocamento que centraliza a grade de {@code geometry} no componente. */
    private Point origin(BoardGeometry geometry) {
        Dimension size = geometry.preferredSize();
        return new Point(
                Math.max(0, (getWidth() - size.width) / 2),
                Math.max(0, (getHeight() - size.height) / 2));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            BoardGeometry geometry = currentGeometry();
            Point origin = origin(geometry);
            g2.translate(origin.x, origin.y);
            paintCapturedBoxes(g2, geometry);
            paintEdges(g2, geometry);
            paintDots(g2, geometry);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Traço proporcional à célula; na célula padrão (56) mede {@code atDefaultCell}, como antes.
     */
    private static Stroke stroke(BoardGeometry geometry, float atDefaultCell, boolean round) {
        float width = Math.max(1f, atDefaultCell * geometry.cell() / BoardGeometry.DEFAULT_CELL);
        return round
                ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                : new BasicStroke(width);
    }

    private void paintCapturedBoxes(Graphics2D g2, BoardGeometry geometry) {
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

    private void paintEdges(Graphics2D g2, BoardGeometry geometry) {
        Stroke marked = stroke(geometry, 6f, true);
        Stroke free = stroke(geometry, 2f, false);
        Stroke halo = stroke(geometry, 12f, true);
        for (Edge edge : geometry.allEdges()) {
            PlayerColor owner = board == null ? null : board.edgeOwner(edge);
            Point[] segment = geometry.edgeSegment(edge);

            if (owner != null && edge.equals(lastMove)) {
                g2.setStroke(halo);
                g2.setColor(LAST_MOVE_HALO);
                g2.drawLine(segment[0].x, segment[0].y, segment[1].x, segment[1].y);
            }

            if (owner != null) {
                g2.setStroke(marked);
                g2.setColor(PlayerColors.awt(owner));
            } else if (edge.equals(hovered)) {
                g2.setStroke(marked);
                g2.setColor(EDGE_HOVER);
            } else {
                g2.setStroke(free);
                g2.setColor(EDGE_FREE);
            }
            g2.drawLine(segment[0].x, segment[0].y, segment[1].x, segment[1].y);
        }
    }

    private void paintDots(Graphics2D g2, BoardGeometry geometry) {
        int radius = Math.max(2, Math.round(4f * geometry.cell() / BoardGeometry.DEFAULT_CELL));
        g2.setColor(DOT);
        for (int r = 0; r <= boardHeight; r++) {
            for (int c = 0; c <= boardWidth; c++) {
                Point p = geometry.dotCenter(r, c);
                g2.fillOval(p.x - radius, p.y - radius, 2 * radius, 2 * radius);
            }
        }
    }

    private Edge freeEdgeAt(Point point) {
        if (!interactive) {
            return null;
        }
        BoardGeometry geometry = currentGeometry();
        Point origin = origin(geometry);
        return geometry.edgeAt(new Point(point.x - origin.x, point.y - origin.y))
                .filter(edge -> board == null || board.edgeOwner(edge) == null)
                .orElse(null);
    }

    // --- Scrollable: acompanha o viewport enquanto ele comportar o tamanho mínimo ---------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return isPreferredSizeSet() ? super.getPreferredSize() : preferredGeometry.preferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return MIN_CELL;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.HORIZONTAL ? visible.width : visible.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport viewport
                && viewport.getWidth() >= getMinimumSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport
                && viewport.getHeight() >= getMinimumSize().height;
    }
}
