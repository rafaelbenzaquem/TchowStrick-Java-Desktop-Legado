package br.com.mss.tchow.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;

/**
 * Regras comuns de dimensionamento da GUI: janelas cabem na área útil da tela (sem barra de
 * tarefas), textos longos quebram linha em vez de esticar diálogos além da tela, e margens internas
 * uniformes. Tudo em unidades lógicas do Swing — a escala da tela (DPI) é aplicada pelo Java2D,
 * então nada aqui depende de pixels físicos.
 */
public final class UiSizing {

    /** Largura de texto corrido em diálogos e mensagens, em pixels lógicos. */
    public static final int TEXT_WIDTH = 420;

    /** Mensagens até este tamanho, sem quebra, ficam como texto simples no {@code JOptionPane}. */
    static final int SHORT_MESSAGE = 70;

    private UiSizing() {}

    /** Margem interna padrão de diálogos. */
    public static Border dialogPadding() {
        return BorderFactory.createEmptyBorder(10, 12, 10, 12);
    }

    /**
     * Área útil (sem barra de tarefas) da tela onde {@code component} está, ou da tela principal se
     * ele ainda não estiver em nenhuma.
     */
    public static Rectangle usableBounds(Component component) {
        if (GraphicsEnvironment.isHeadless()) {
            return new Rectangle(0, 0, 1366, 728);
        }
        GraphicsConfiguration config =
                component == null ? null : component.getGraphicsConfiguration();
        if (config == null) {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        }
        Rectangle bounds = config.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
    }

    /** {@code preferred} limitado a {@code usable}, dimensão a dimensão. */
    public static Dimension fit(Dimension preferred, Dimension usable) {
        return new Dimension(
                Math.min(preferred.width, usable.width), Math.min(preferred.height, usable.height));
    }

    /**
     * Empacota a janela, limita-a à área útil da tela e centraliza sobre {@code relativeTo} (ou na
     * tela). O tamanho mínimo passa a ser o menor entre o mínimo do layout e a área útil, para o
     * usuário não conseguir esconder componentes encolhendo a janela.
     */
    public static void packWithin(Window window, Component relativeTo) {
        window.setMinimumSize(null); // um mínimo antigo (de outra tela da janela) impede o pack
        window.pack();
        fitWithin(window, usableBounds(relativeTo != null ? relativeTo : window), relativeTo);
    }

    /** Variante testável de {@link #packWithin}: limita a {@code usable} sem consultar a tela. */
    public static void fitWithin(Window window, Rectangle usable, Component relativeTo) {
        window.setMinimumSize(null); // volta ao mínimo do layout atual (a tela pode ter mudado)
        Dimension size = fit(window.getSize(), usable.getSize());
        window.setSize(size);
        window.setMinimumSize(fit(window.getMinimumSize(), size));
        window.setLocationRelativeTo(relativeTo);
        Rectangle bounds = window.getBounds();
        // setLocationRelativeTo pode deixar a janela parcialmente fora da área útil
        int x = Math.max(usable.x, Math.min(bounds.x, usable.x + usable.width - bounds.width));
        int y = Math.max(usable.y, Math.min(bounds.y, usable.y + usable.height - bounds.height));
        window.setLocation(x, y);
    }

    /**
     * Depois de um conteúdo crescer (ex.: mensagem de erro de várias linhas), aumenta a janela até
     * o novo tamanho preferido — sem encolher o que o usuário já ampliou e sem passar da área útil.
     */
    public static void growToPreferred(Window window) {
        if (!window.isDisplayable()) {
            return;
        }
        window.validate();
        Dimension preferred = window.getPreferredSize();
        Dimension current = window.getSize();
        Dimension wanted =
                fit(
                        new Dimension(
                                Math.max(current.width, preferred.width),
                                Math.max(current.height, preferred.height)),
                        usableBounds(window).getSize());
        if (!wanted.equals(current)) {
            window.setSize(wanted);
            window.validate();
        }
    }

    /**
     * Conteúdo para {@code JOptionPane}: mensagens curtas ficam como estão; longas viram um rótulo
     * com quebra de linha em {@link #TEXT_WIDTH} — sem isso o diálogo cresce numa linha só, às
     * vezes mais larga que a tela.
     */
    public static Object message(String message) {
        if (message == null || !needsWrapping(message)) {
            return message;
        }
        return wrappedLabel(message);
    }

    static boolean needsWrapping(String message) {
        for (String line : message.split("\n", -1)) {
            if (line.length() > SHORT_MESSAGE) {
                return true;
            }
        }
        return false;
    }

    /** Rótulo com quebra de linha automática em {@link #TEXT_WIDTH}; respeita {@code \n}. */
    public static JLabel wrappedLabel(String text) {
        return wrappedLabel(text, TEXT_WIDTH);
    }

    /** Rótulo com quebra de linha automática em {@code width} pixels lógicos. */
    public static JLabel wrappedLabel(String text, int width) {
        JLabel label = new JLabel(wrappedHtml(escapeHtml(text), width));
        label.setVerticalAlignment(JLabel.TOP);
        return label;
    }

    /**
     * HTML já escapado (pode conter {@code <b>}) com quebra em {@code width}. Só fixa a largura
     * quando o texto não cabe nela — um texto curto não estica o rótulo.
     */
    public static String wrappedHtml(String escapedHtml, int width) {
        String body = escapedHtml.replace("\n", "<br>");
        JLabel probe = new JLabel("<html>" + body + "</html>");
        if (probe.getPreferredSize().width <= width) {
            return "<html>" + body + "</html>";
        }
        // O CSS do Swing (sem "w3cLengthUnits") multiplica "px" por 1,3; compensa para a quebra
        // acontecer em ~width pixels lógicos.
        int cssWidth = Math.round(width / 1.3f);
        return "<html><body style='width: " + cssWidth + "px'>" + body + "</body></html>";
    }

    public static String escapeHtml(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Coluna de componentes, cada um na sua altura preferida e esticado na largura. Ao contrário do
     * {@code GridLayout(0, 1)}, um rótulo de duas linhas não dobra a altura de todas as linhas.
     */
    public static JPanel column(Component... rows) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 0);
        for (Component row : rows) {
            panel.add(row, c);
        }
        return panel;
    }

    /** Aplica a margem padrão a {@code component} e o devolve (para encadear). */
    public static <T extends JComponent> T padded(T component) {
        component.setBorder(dialogPadding());
        return component;
    }
}
