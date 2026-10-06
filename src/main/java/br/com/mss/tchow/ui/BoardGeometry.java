package br.com.mss.tchow.ui;

import br.com.mss.tchow.domain.Edge;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Conversões entre coordenadas do tabuleiro ({@link Edge}, pontos, quadros) e pixels. Sem Swing —
 * código puro, testável sem tela.
 *
 * @param width quadros na horizontal
 * @param height quadros na vertical
 * @param cell distância em pixels entre dois pontos vizinhos
 * @param margin borda em pixels ao redor da grade
 * @param hitRadius tolerância em pixels para "acertar" uma aresta no clique
 */
public record BoardGeometry(int width, int height, int cell, int margin, int hitRadius) {

    public static final int DEFAULT_CELL = 56;
    public static final int DEFAULT_MARGIN = 40;
    public static final int DEFAULT_HIT_RADIUS = 14;

    public BoardGeometry {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("tabuleiro deve ter ao menos 1x1");
        }
        if (cell < 1 || margin < 0 || hitRadius < 1) {
            throw new IllegalArgumentException("parâmetros de pixel inválidos");
        }
    }

    public BoardGeometry(int width, int height) {
        this(width, height, DEFAULT_CELL, DEFAULT_MARGIN, DEFAULT_HIT_RADIUS);
    }

    /**
     * Geometria com a mesma proporção da padrão (margem e tolerância relativas à célula), com a
     * maior célula — de no mínimo {@code minCell} e no máximo {@code maxCell} — que cabe em {@code
     * availableWidth}×{@code availableHeight} pixels.
     */
    public static BoardGeometry fitting(
            int width,
            int height,
            int availableWidth,
            int availableHeight,
            int minCell,
            int maxCell) {
        // tamanho total = célula × (quadros + 2 × margem/célula)
        int byWidth = availableWidth * DEFAULT_CELL / (width * DEFAULT_CELL + 2 * DEFAULT_MARGIN);
        int byHeight =
                availableHeight * DEFAULT_CELL / (height * DEFAULT_CELL + 2 * DEFAULT_MARGIN);
        int cell = Math.max(minCell, Math.min(maxCell, Math.min(byWidth, byHeight)));
        cell = Math.max(1, cell);
        return new BoardGeometry(
                width,
                height,
                cell,
                cell * DEFAULT_MARGIN / DEFAULT_CELL,
                Math.max(1, cell * DEFAULT_HIT_RADIUS / DEFAULT_CELL));
    }

    /**
     * Centro, em pixels, do ponto ({@code dotRow} em [0, height], {@code dotCol} em [0, width]).
     */
    public Point dotCenter(int dotRow, int dotCol) {
        return new Point(margin + dotCol * cell, margin + dotRow * cell);
    }

    /** Retângulo da área de preenchimento do quadro ({@code boxRow}, {@code boxCol}). */
    public Rectangle boxRect(int boxRow, int boxCol) {
        Point topLeft = dotCenter(boxRow, boxCol);
        return new Rectangle(topLeft.x, topLeft.y, cell, cell);
    }

    /** Os dois extremos, em pixels, do segmento que representa {@code edge}. */
    public Point[] edgeSegment(Edge edge) {
        return switch (edge.orientation()) {
            case HORIZONTAL ->
                    new Point[] {
                        dotCenter(edge.row(), edge.col()), dotCenter(edge.row(), edge.col() + 1)
                    };
            case VERTICAL ->
                    new Point[] {
                        dotCenter(edge.row(), edge.col()), dotCenter(edge.row() + 1, edge.col())
                    };
        };
    }

    /** Todas as arestas do tabuleiro (horizontais e depois verticais). */
    public List<Edge> allEdges() {
        List<Edge> edges = new ArrayList<>(2 * width * height + width + height);
        for (int r = 0; r <= height; r++) {
            for (int c = 0; c < width; c++) {
                edges.add(Edge.horizontal(r, c));
            }
        }
        for (int r = 0; r < height; r++) {
            for (int c = 0; c <= width; c++) {
                edges.add(Edge.vertical(r, c));
            }
        }
        return edges;
    }

    /** A aresta mais próxima de {@code p}, se houver alguma dentro de {@link #hitRadius}. */
    public Optional<Edge> edgeAt(Point p) {
        Edge best = null;
        double bestDistance = hitRadius + 1.0;
        for (Edge edge : allEdges()) {
            double distance = distanceToSegment(p, edge);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = edge;
            }
        }
        return Optional.ofNullable(best);
    }

    public Dimension preferredSize() {
        return new Dimension(2 * margin + width * cell, 2 * margin + height * cell);
    }

    private double distanceToSegment(Point p, Edge edge) {
        Point[] segment = edgeSegment(edge);
        double x1 = segment[0].x;
        double y1 = segment[0].y;
        double dx = segment[1].x - x1;
        double dy = segment[1].y - y1;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : ((p.x - x1) * dx + (p.y - y1) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;
        return Math.hypot(p.x - projX, p.y - projY);
    }
}
