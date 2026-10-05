package br.com.mss.tchow.ui;

import br.com.mss.tchow.domain.PlayerColor;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Faixa com um "chip" por assento do elenco: quadradinho da cor, placar, seta na vez atual,
 * "(você)" no seu assento e "aguardando" enquanto o jogador não conectou.
 */
public final class PlayersPanel extends JPanel {

    private static final Color WAITING_FG = new Color(0x90, 0x90, 0x90);

    public PlayersPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 8, 6));
    }

    public void render(
            List<PlayerColor> turnOrder,
            Set<PlayerColor> joined,
            Map<PlayerColor, Integer> scores,
            PlayerColor current,
            boolean finished,
            PlayerColor localColor) {
        removeAll();
        for (PlayerColor color : turnOrder) {
            boolean connected = joined.contains(color);
            boolean isTurn = color == current && !finished;
            int score = scores.getOrDefault(color, 0);

            StringBuilder text = new StringBuilder();
            if (isTurn) {
                text.append("▶ ");
            }
            text.append(color).append("  ").append(score);
            if (color == localColor) {
                text.append("  (você)");
            }
            if (!connected) {
                text.append("  — aguardando");
            }

            JLabel chip =
                    new JLabel(
                            text.toString(),
                            new ColorIcon(PlayerColors.awt(color), 12),
                            SwingConstants.LEFT);
            chip.setIconTextGap(6);
            chip.setOpaque(true);
            chip.setForeground(connected ? getForeground() : WAITING_FG);
            chip.setBackground(connected ? PlayerColors.fill(color) : getBackground());
            chip.setFont(chip.getFont().deriveFont(isTurn ? Font.BOLD : Font.PLAIN));
            chip.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(PlayerColors.awt(color), isTurn ? 2 : 1),
                            BorderFactory.createEmptyBorder(3, 8, 3, 8)));
            add(chip);
        }
        revalidate();
        repaint();
    }
}
