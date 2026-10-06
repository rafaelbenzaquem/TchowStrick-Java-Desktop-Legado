package br.com.mss.tchow.ui;

import java.awt.Dimension;
import javax.swing.JComboBox;

/**
 * {@link JComboBox} cuja largura preferida comporta o item mais longo. No Look and Feel do Windows
 * a área do valor atual é desenhada com mais folga do que a calculada no tamanho preferido, e o
 * item mais longo aparece truncado com "…" (ex.: "Humano (em re…"). A folga extra corrige isso sem
 * fixar pixels: é proporcional à altura do próprio combo (logo, acompanha a escala da tela).
 */
public class FittingComboBox<E> extends JComboBox<E> {

    public FittingComboBox() {
        super();
    }

    public FittingComboBox(E[] items) {
        super(items);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (!isPreferredSizeSet()) {
            size.width += Math.max(8, size.height / 2);
        }
        return size;
    }
}
