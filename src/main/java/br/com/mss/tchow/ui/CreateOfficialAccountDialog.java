package br.com.mss.tchow.ui;

import br.com.mss.tchow.net.grpc.GrpcAccountClient.DeliveryChannel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * Creates a fresh online account or requests contact proof for an existing account. The local
 * profile and history remain independent of the server-issued identity.
 */
public final class CreateOfficialAccountDialog extends JDialog {

    /** {@code fullName} pode ser vazio ("não precisa exigir completo", docs/issues.md §E6-10). */
    public record Result(
            String nick,
            String fullName,
            boolean isEmail,
            String contactValue,
            DeliveryChannel channel) {}

    private final JTextField nickField = new JTextField(28);
    private final JTextField fullNameField = new JTextField(28);
    private final JRadioButton emailOption = new JRadioButton("E-mail", true);
    private final JRadioButton phoneOption = new JRadioButton("WhatsApp (telefone E.164)");
    private final JRadioButton smsOption = new JRadioButton("SMS (telefone E.164)");
    private final JTextField contactField = new JTextField(28);

    private Result result;

    public CreateOfficialAccountDialog(Window owner, String initialNick) {
        this(owner, initialNick, true, false);
    }

    public CreateOfficialAccountDialog(
            Window owner, String initialNick, boolean emailEnabled, boolean phoneEnabled) {
        this(owner, initialNick, emailEnabled, false, phoneEnabled);
    }

    public CreateOfficialAccountDialog(
            Window owner,
            String initialNick,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled) {
        super(owner, "Criar ou acessar conta", ModalityType.APPLICATION_MODAL);
        nickField.setText(initialNick == null ? "" : initialNick);
        buildUi();
        emailOption.setEnabled(emailEnabled);
        phoneOption.setEnabled(whatsappEnabled);
        smsOption.setEnabled(smsEnabled);
        if (!emailEnabled) {
            if (whatsappEnabled) phoneOption.setSelected(true);
            else if (smsEnabled) smsOption.setSelected(true);
        }
        setResizable(false);
        UiSizing.packWithin(this, owner);
    }

    /** Abre o diálogo. {@code null} se o jogador cancelou. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }

    private void buildUi() {
        ButtonGroup contactType = new ButtonGroup();
        contactType.add(emailOption);
        contactType.add(phoneOption);
        contactType.add(smsOption);

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        form.add(new JLabel("Nick:"));
        form.add(nickField);
        form.add(new JLabel("Nome completo (opcional):"));
        form.add(fullNameField);
        form.add(emailOption);
        form.add(phoneOption);
        form.add(smsOption);
        form.add(new JLabel("Contato:"));
        form.add(contactField);

        JButton create = new JButton("Continuar");
        create.addActionListener(e -> confirm());
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel();
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        buttons.add(create);
        buttons.add(cancel);

        setLayout(new BorderLayout(0, 6));
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(create);
    }

    private void confirm() {
        String nick = nickField.getText().strip();
        String contactValue = contactField.getText().strip();
        if (nick.isBlank()) {
            warn("Informe um nick.");
            return;
        }
        if (contactValue.isBlank()) {
            warn("Informe um e-mail ou telefone.");
            return;
        }
        String fullName = fullNameField.getText().strip();
        result =
                new Result(
                        nick,
                        fullName.isBlank() ? null : fullName,
                        emailOption.isSelected(),
                        contactValue,
                        emailOption.isSelected()
                                ? DeliveryChannel.DEFAULT
                                : smsOption.isSelected()
                                        ? DeliveryChannel.SMS
                                        : DeliveryChannel.WHATSAPP);
        dispose();
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(
                this, UiSizing.message(message), "Dados incompletos", JOptionPane.WARNING_MESSAGE);
    }
}
