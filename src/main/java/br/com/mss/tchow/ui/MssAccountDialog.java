package br.com.mss.tchow.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Conta MSS (M1): estado, perfil (nick e avatar) e saída. Só mostra e devolve a escolha do jogador;
 * quem abriu executa a ação na identidade.
 */
public final class MssAccountDialog extends JDialog {

    public enum Action {
        SAVE_PROFILE,
        CONFIRM_EMAIL,
        SWITCH_ACCOUNT,
        SIGN_OUT_THIS_DEVICE,
        SIGN_OUT_ALL_DEVICES
    }

    /** {@code nick}/{@code avatarId} só são relevantes em {@link Action#SAVE_PROFILE}. */
    public record Result(Action action, String nick, String avatarId) {}

    /** Dados exibidos; {@code nick} vazio quando o perfil não pôde ser lido. */
    public record View(
            String serverName,
            String stateText,
            String nick,
            String avatarId,
            String maskedContact,
            boolean contactVerified,
            boolean profileAvailable) {}

    private final JTextField nickField = new JTextField(20);
    private final JTextField avatarField = new JTextField(20);
    private Result result;

    public MssAccountDialog(Window owner, View view) {
        super(owner, "Conta MSS", ModalityType.APPLICATION_MODAL);
        buildUi(view);
        setResizable(false);
        UiSizing.packWithin(this, owner);
    }

    /** {@code null} se o jogador só fechou. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }

    private void buildUi(View view) {
        // BoxLayout (não GridLayout): cada linha com a própria altura, para textos longos
        // (servidor, estado da conta, contato) quebrarem linha sem cortar nem sobrepor
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        addInfo(info, UiSizing.escapeHtml("Servidor: " + view.serverName()));
        addInfo(info, "<b>" + UiSizing.escapeHtml(view.stateText()) + "</b>");
        if (view.profileAvailable()) {
            addInfo(
                    info,
                    UiSizing.escapeHtml(
                            "Contato: "
                                    + view.maskedContact()
                                    + (view.contactVerified() ? " (confirmado)" : " (pendente)")));
        } else {
            addInfo(info, "Perfil indisponível no momento.");
        }

        JPanel profile = new JPanel(new GridLayout(0, 1, 4, 4));
        profile.setBorder(BorderFactory.createTitledBorder("Perfil"));
        nickField.setText(view.nick());
        avatarField.setText(view.avatarId());
        nickField.setEnabled(view.profileAvailable());
        avatarField.setEnabled(view.profileAvailable());
        profile.add(new JLabel("Nick:"));
        profile.add(nickField);
        profile.add(new JLabel("Avatar (identificador; vazio = padrão):"));
        profile.add(avatarField);

        JButton save = new JButton("Salvar perfil");
        save.setEnabled(view.profileAvailable());
        save.addActionListener(
                e ->
                        finish(
                                new Result(
                                        Action.SAVE_PROFILE,
                                        nickField.getText(),
                                        avatarField.getText())));
        JButton confirm = new JButton("Confirmar e-mail…");
        confirm.addActionListener(e -> finish(new Result(Action.CONFIRM_EMAIL, null, null)));
        JButton switchAccount = new JButton("Trocar de conta…");
        switchAccount.addActionListener(e -> finish(new Result(Action.SWITCH_ACCOUNT, null, null)));
        JButton signOut = new JButton("Sair deste dispositivo");
        signOut.addActionListener(e -> finish(new Result(Action.SIGN_OUT_THIS_DEVICE, null, null)));
        JButton signOutAll = new JButton("Sair de todos");
        signOutAll.addActionListener(
                e -> finish(new Result(Action.SIGN_OUT_ALL_DEVICES, null, null)));
        JButton close = new JButton("Fechar");
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new GridLayout(0, 3, 4, 4));
        buttons.add(save);
        buttons.add(confirm);
        buttons.add(close);
        buttons.add(switchAccount);
        buttons.add(signOut);
        buttons.add(signOutAll);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(UiSizing.dialogPadding());
        content.add(info, BorderLayout.NORTH);
        content.add(profile, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private static void addInfo(JPanel info, String html) {
        JLabel label = new JLabel(UiSizing.wrappedHtml(html, UiSizing.TEXT_WIDTH));
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        info.add(label);
    }

    private void finish(Result chosen) {
        result = chosen;
        dispose();
    }
}
