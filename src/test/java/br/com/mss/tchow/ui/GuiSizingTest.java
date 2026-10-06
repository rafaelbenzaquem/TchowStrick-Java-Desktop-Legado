package br.com.mss.tchow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.domain.PlayerColor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

/** Regras de dimensionamento da GUI (BUG-0001), sem tela: tudo roda headless. */
class GuiSizingTest {

    @Test
    void geometriaAjustadaCabeNoEspacoEMantemLimitesDeCelula() {
        BoardGeometry g = BoardGeometry.fitting(12, 12, 600, 450, 24, 96);
        Dimension size = g.preferredSize();
        assertTrue(size.width <= 600 && size.height <= 450, size.toString());
        assertTrue(g.cell() >= 24);

        // espaço de sobra: a célula para no máximo
        assertEquals(96, BoardGeometry.fitting(2, 2, 2000, 2000, 24, 96).cell());
        // espaço de menos: a célula não passa do mínimo (o tabuleiro rola)
        assertEquals(24, BoardGeometry.fitting(12, 12, 100, 100, 24, 96).cell());
        // na célula padrão, mesma proporção da geometria padrão
        BoardGeometry standard = new BoardGeometry(5, 5);
        assertEquals(
                standard,
                BoardGeometry.fitting(
                        5,
                        5,
                        standard.preferredSize().width,
                        standard.preferredSize().height,
                        1,
                        BoardGeometry.DEFAULT_CELL));
    }

    @Test
    void tabuleiroEscalaComOComponenteEAcompanhaOViewport() {
        BoardView board = new BoardView(12, 12);
        board.setSize(400, 700);
        BoardGeometry g = board.currentGeometry();
        assertTrue(g.preferredSize().width <= 400 && g.cell() > BoardView.MIN_CELL, g.toString());

        JScrollPane scroll = new JScrollPane(board);
        scroll.setSize(500, 500);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        assertTrue(board.getScrollableTracksViewportWidth());
        assertTrue(board.getScrollableTracksViewportHeight());
        assertEquals(scroll.getViewport().getExtentSize(), board.getSize());

        // abaixo do mínimo, rola em vez de encolher mais
        scroll.setSize(120, 120);
        scroll.doLayout();
        assertFalse(board.getScrollableTracksViewportHeight());
    }

    @Test
    void wrapLayoutReservaAlturaDasLinhasQuebradas() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
        for (int i = 0; i < 6; i++) {
            panel.add(new JButton("Botão com texto " + i));
        }
        int oneRow = panel.getPreferredSize().height; // sem largura ainda: uma linha só
        panel.setSize(200, oneRow);
        Dimension wrapped = panel.getPreferredSize();
        assertTrue(wrapped.height > oneRow * 3, wrapped.toString());

        panel.setSize(200, wrapped.height);
        panel.doLayout();
        Rectangle area = new Rectangle(panel.getSize());
        for (Component c : panel.getComponents()) {
            assertTrue(area.contains(c.getBounds()), c.getBounds().toString());
        }
    }

    @Test
    void chipsDosJogadoresQuebramLinhaSemSumir() {
        PlayersPanel players = new PlayersPanel();
        List<PlayerColor> all = List.of(PlayerColor.values());
        players.render(all, Set.copyOf(all), Map.of(), PlayerColor.RED, false, PlayerColor.RED);
        int oneRow = players.getPreferredSize().height;
        players.setSize(220, oneRow);
        assertTrue(players.getPreferredSize().height > oneRow);
    }

    @Test
    void mensagensLongasQuebramLinha() {
        assertEquals("Informe o código.", UiSizing.message("Informe o código."));
        Object wrapped = UiSizing.message("x".repeat(30) + " " + "palavra ".repeat(40));
        JLabel label = assertInstanceOf(JLabel.class, wrapped);
        assertTrue(label.getText().contains("width"), label.getText());
        // largura limitada (com folga de borda), altura de várias linhas
        assertTrue(label.getPreferredSize().width <= UiSizing.TEXT_WIDTH + 40);
        assertTrue(label.getPreferredSize().height > 2 * new JLabel("x").getPreferredSize().height);
        // HTML do usuário é escapado
        assertTrue(UiSizing.wrappedLabel("<b>nick</b>").getText().contains("&lt;b&gt;"));
    }

    @Test
    void janelaLimitadaAAreaUtil() {
        assertEquals(
                new Dimension(1366, 728),
                UiSizing.fit(new Dimension(2000, 900), new Dimension(1366, 728)));
        assertEquals(
                new Dimension(400, 300),
                UiSizing.fit(new Dimension(400, 300), new Dimension(1366, 728)));
    }
}
