package br.com.mss.tchow.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * {@link FlowLayout} que informa a altura das linhas quebradas. O {@code FlowLayout} comum calcula
 * o tamanho preferido como se tudo coubesse numa linha só: quando a largura disponível é menor, ele
 * quebra os componentes numa segunda linha que fica fora da área reservada — botões e chips "somem"
 * ou aparecem cortados. Aqui o preferido é calculado para a largura atual do contêiner.
 */
public final class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /** Altura preferida da última disposição, para pedir novo layout quando as linhas mudam. */
    private int lastPreferredHeight = -1;

    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        int height = preferredLayoutSize(target).height;
        if (lastPreferredHeight >= 0
                && height != lastPreferredHeight
                && target instanceof javax.swing.JComponent component) {
            // a largura mudou o número de linhas: o pai precisa reservar a nova altura
            SwingUtilities.invokeLater(component::revalidate);
        }
        lastPreferredHeight = height;
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = availableWidth(target);
            Insets insets = target.getInsets();
            int horizontal = insets.left + insets.right + getHgap() * 2;
            int maxWidth = targetWidth - horizontal;

            Dimension result = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            for (Component c : target.getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + d.width > maxWidth) {
                    addRow(result, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth > 0) {
                    rowWidth += getHgap();
                }
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(result, rowWidth, rowHeight);

            result.width += horizontal;
            result.height += insets.top + insets.bottom + getVgap() * 2;
            // Dentro de um JScrollPane, não force a largura do viewport (evita laço de relayout).
            Container scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scroll != null && target.isValid()) {
                result.width -= getHgap() + 1;
            }
            return result;
        }
    }

    /**
     * Largura do contêiner. Antes do primeiro layout (0), usa a do ancestral já disposto; numa
     * janela ainda não exibida (sendo empacotada), "sem limite" — tudo numa linha, que é o tamanho
     * preferido de verdade. Sem essa exceção, o tamanho mínimo que o sistema impõe à janela recém-
     * criada (bem estreito) faria cada componente ir para uma linha própria.
     */
    private static int availableWidth(Container target) {
        Container container = target;
        while (container.getSize().width == 0 && container.getParent() != null) {
            container = container.getParent();
        }
        if (container instanceof java.awt.Window window && !window.isShowing()) {
            return Integer.MAX_VALUE;
        }
        int width = container.getSize().width;
        return width == 0 ? Integer.MAX_VALUE : width;
    }

    private void addRow(Dimension result, int rowWidth, int rowHeight) {
        if (rowWidth == 0) {
            return;
        }
        result.width = Math.max(result.width, rowWidth);
        if (result.height > 0) {
            result.height += getVgap();
        }
        result.height += rowHeight;
    }
}
