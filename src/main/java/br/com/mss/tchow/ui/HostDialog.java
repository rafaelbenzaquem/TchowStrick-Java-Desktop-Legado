package br.com.mss.tchow.ui;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.domain.ai.AiLevel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Diálogo modal para criar uma partida ([E4.5-04]: renomeado de "Hospedar" para "Criar partida" na
 * UI — internamente continua {@code HostDialog}). Escrito à mão (sem NetBeans {@code .form}).
 */
public final class HostDialog extends JDialog {

    /**
     * Escolhas de quem cria a partida. {@code maxPlayers} é só a <b>quantidade</b> ([E4.5-05],
     * ADR-0015) — não trava quais cores; {@code color} é a cor de quem cria, escolhida livremente
     * entre as 5. Em rede, as outras cores ficam livres para quem entrar depois escolher (nenhuma
     * pré-reservada); em partida local contra IA, os bots preenchem as vagas restantes com as
     * demais cores em ordem, já que não há ninguém real pra escolher por eles. {@code ai} nulo =
     * partida em rede (humano×humano); não-nulo = local contra a IA. {@code password} vazia =
     * partida pública; senão trava por senha simples (ADR-0012, [E4a-05b]) — ignorada no modo local
     * contra a IA. Sem campo de servidor/porta ([E4.5-07], ADR-0017): quem chama já sabe qual
     * servidor usar (o ativo, escolhido no {@code ServerPickerDialog}, ou {@code --port=} no modo
     * embutido).
     */
    public record Result(
            int width,
            int height,
            int maxPlayers,
            PlayerColor color,
            String nick,
            AiLevel ai,
            String password) {}

    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 12, 1));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 12, 1));
    private final JSpinner playersSpinner = new JSpinner(new SpinnerNumberModel(2, 2, 5, 1));

    /** Todas as 5 cores, sempre — [E4.5-05] tira a restrição de "só as N primeiras". */
    private final JComboBox<PlayerColor> colorCombo = new JComboBox<>(PlayerColor.values());

    private final JComboBox<String> adversaryCombo =
            new JComboBox<>(
                    new String[] {"Humano (em rede)", "IA — Fácil", "IA — Média", "IA — Difícil"});
    private final JTextField nickField = new JTextField(12);
    private final JPasswordField passwordField = new JPasswordField(12);

    private Result result;

    /** {@code initialNick} pré-preenche o campo (nome do perfil ativo); editável. */
    public HostDialog(Window owner, String initialNick) {
        super(owner, "Criar partida", ModalityType.APPLICATION_MODAL);
        nickField.setText(
                initialNick == null || initialNick.isBlank() ? "host" : initialNick.strip());
        buildUi();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Abre o diálogo (bloqueia) e devolve as escolhas, ou {@code null} se cancelado. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }

    private AiLevel selectedAi() {
        return switch (adversaryCombo.getSelectedIndex()) {
            case 1 -> AiLevel.EASY;
            case 2 -> AiLevel.MEDIUM;
            case 3 -> AiLevel.HARD;
            default -> null;
        };
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "Largura (quadros):", widthSpinner);
        addRow(form, c, row++, "Altura (quadros):", heightSpinner);
        addRow(form, c, row++, "Nº de jogadores:", playersSpinner);
        addRow(form, c, row++, "Sua cor:", colorCombo);
        addRow(form, c, row++, "Adversário:", adversaryCombo);
        addRow(form, c, row++, "Nick:", nickField);
        addRow(form, c, row++, "Senha (opcional, só em rede):", passwordField);

        JButton ok = new JButton("Criar partida");
        ok.addActionListener(
                e -> {
                    result =
                            new Result(
                                    (int) widthSpinner.getValue(),
                                    (int) heightSpinner.getValue(),
                                    (int) playersSpinner.getValue(),
                                    (PlayerColor) colorCombo.getSelectedItem(),
                                    nickField.getText(),
                                    selectedAi(),
                                    new String(passwordField.getPassword()));
                    dispose();
                });
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel();
        buttons.add(ok);
        buttons.add(cancel);

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        form.add(buttons, c);

        setContentPane(form);
        getRootPane().setDefaultButton(ok);
    }

    private static void addRow(
            JPanel form, GridBagConstraints c, int row, String label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        form.add(field, c);
    }
}
