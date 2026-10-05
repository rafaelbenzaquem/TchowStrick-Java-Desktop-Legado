package br.com.mss.tchow.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Inserir o código de confirmação de contato ({@code [E6-13]}) — mostrado logo após criar/promover
 * a conta oficial, ou sob pedido (menu "Jogador → Confirmar contato…"), ou quando o servidor recusa
 * {@code Join} por carência vencida ({@code [E6-11]}). "Reenviar código" delega pro {@code
 * onResend} passado por quem abriu o diálogo — esta classe não fala com a rede sozinha.
 */
public final class ConfirmContactCodeDialog extends JDialog {

    public record Result(String code) {}

    private final JTextField codeField = new JTextField(10);
    private Result result;

    public ConfirmContactCodeDialog(Window owner, String maskedContact, Runnable onResend) {
        super(owner, "Confirmar contato", ModalityType.APPLICATION_MODAL);
        buildUi(maskedContact, onResend);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Abre o diálogo. {@code null} se o jogador cancelou (ou fechou sem confirmar). */
    public Result showDialog() {
        setVisible(true);
        return result;
    }

    private void buildUi(String maskedContact, Runnable onResend) {
        JLabel info =
                new JLabel(
                        "<html>Digite o código enviado pra <b>"
                                + escapeHtml(maskedContact)
                                + "</b>:</html>");

        JButton resend = new JButton("Reenviar código");
        resend.addActionListener(e -> onResend.run());

        JButton confirm = new JButton("Confirmar");
        confirm.addActionListener(e -> confirm());
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> dispose());

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(info, BorderLayout.NORTH);
        JPanel field = new JPanel(new FlowLayout(FlowLayout.LEFT));
        field.add(new JLabel("Código:"));
        field.add(codeField);
        field.add(resend);
        top.add(field, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.add(confirm);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(top, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(confirm);
    }

    private void confirm() {
        String code = codeField.getText().strip();
        if (code.isBlank()) {
            JOptionPane.showMessageDialog(
                    this, "Informe o código.", "Código vazio", JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = new Result(code);
        dispose();
    }

    private static String escapeHtml(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
