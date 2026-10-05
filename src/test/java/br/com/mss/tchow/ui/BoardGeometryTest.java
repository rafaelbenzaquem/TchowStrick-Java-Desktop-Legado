package br.com.mss.tchow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.mss.tchow.domain.Edge;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardGeometryTest {

    private static final int CELL = 50;
    private static final int MARGIN = 30;
    private static final int HIT = 12;

    private static BoardGeometry geometry(int width, int height) {
        return new BoardGeometry(width, height, CELL, MARGIN, HIT);
    }

    @Test
    void tamanhoPreferido() {
        assertEquals(
                new Dimension(2 * MARGIN + 5 * CELL, 2 * MARGIN + 4 * CELL),
                geometry(5, 4).preferredSize());
    }

    @Test
    void centroDosPontos() {
        BoardGeometry g = geometry(5, 4);
        assertEquals(new Point(MARGIN, MARGIN), g.dotCenter(0, 0));
        assertEquals(new Point(MARGIN + 3 * CELL, MARGIN + 2 * CELL), g.dotCenter(2, 3));
    }

    @Test
    void cliqueSobreArestaHorizontalRetornaAquelaAresta() {
        BoardGeometry g = geometry(3, 3);
        Point meio = new Point(MARGIN + CELL + CELL / 2, MARGIN); // meio da H(0,1)
        assertEquals(Optional.of(Edge.horizontal(0, 1)), g.edgeAt(meio));
    }

    @Test
    void cliqueSobreArestaVerticalRetornaAquelaAresta() {
        BoardGeometry g = geometry(3, 3);
        Point meio = new Point(MARGIN + 2 * CELL, MARGIN + CELL / 2); // meio da V(0,2)
        assertEquals(Optional.of(Edge.vertical(0, 2)), g.edgeAt(meio));
    }

    @Test
    void cliqueNoCentroDoQuadroNaoRetornaAresta() {
        BoardGeometry g = geometry(3, 3);
        Point centro = new Point(MARGIN + CELL / 2, MARGIN + CELL / 2); // centro do quadro (0,0)
        assertEquals(Optional.empty(), g.edgeAt(centro));
    }

    @Test
    void quantidadeDeArestas() {
        // 3x2: horizontais (3 * 3) + verticais (2 * 4) = 17
        assertEquals(17, geometry(3, 2).allEdges().size());
    }
}
