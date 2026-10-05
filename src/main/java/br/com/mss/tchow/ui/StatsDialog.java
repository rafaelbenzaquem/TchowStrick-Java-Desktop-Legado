package br.com.mss.tchow.ui;

import br.com.mss.tchow.net.Dtos.PlayerStatsDto;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Mostra o resultado de {@code GetMyStats} ([E4d-01]) — a UI que faltava no desktop ([E4d-04]); o
 * cliente Mobile/Web (Godot) já tinha o equivalente (`stats_panel.tscn`). Só leitura, sem ação.
 */
public final class StatsDialog extends JDialog {

    public StatsDialog(Window owner, PlayerStatsDto stats) {
        super(owner, "Estatísticas", ModalityType.APPLICATION_MODAL);
        buildUi(stats);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public void showDialog() {
        setVisible(true);
    }

    private void buildUi(PlayerStatsDto stats) {
        JPanel grid = new JPanel(new GridLayout(5, 2, 8, 4));
        grid.setBorder(new EmptyBorder(10, 10, 10, 10));
        addRow(grid, "Partidas jogadas:", stats.played());
        addRow(grid, "Vitórias:", stats.won());
        addRow(grid, "Derrotas:", stats.lost());
        addRow(grid, "Empates:", stats.drawn());
        addRow(grid, "Caixas fechadas:", stats.boxesTotal());

        JButton ok = new JButton("Fechar");
        ok.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);

        setLayout(new BorderLayout());
        add(grid, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
    }

    private static void addRow(JPanel grid, String label, int value) {
        grid.add(new JLabel(label));
        grid.add(new JLabel(String.valueOf(value), JLabel.RIGHT));
    }
}
