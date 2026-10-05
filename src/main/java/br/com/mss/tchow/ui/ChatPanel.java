package br.com.mss.tchow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Chat simples. O texto do usuário é inserido como <b>conteúdo</b> do documento (nunca como
 * markup), o que elimina a injeção de HTML do {@code PanelChat} legado. Estilização e cor por
 * jogador ficam para a Fase 4.
 */
public final class ChatPanel extends JPanel {

    private final JTextPane history = new JTextPane();
    private final JTextField input = new JTextField();
    private transient Consumer<String> sendHandler = text -> {};

    public ChatPanel() {
        super(new BorderLayout(4, 4));

        history.setEditable(false);
        JScrollPane scroll = new JScrollPane(history);
        scroll.setPreferredSize(new Dimension(220, 240));
        add(scroll, BorderLayout.CENTER);

        JButton send = new JButton("Enviar");
        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.add(input, BorderLayout.CENTER);
        south.add(send, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        ActionListener fire =
                e -> {
                    String text = input.getText().strip();
                    if (!text.isEmpty()) {
                        sendHandler.accept(text);
                        input.setText("");
                    }
                };
        send.addActionListener(fire);
        input.addActionListener(fire);
    }

    public void setSendHandler(Consumer<String> handler) {
        this.sendHandler = Objects.requireNonNull(handler);
    }

    /** Todo o texto do histórico, sem formatação — para testes / "copiar conversa". */
    public String transcript() {
        try {
            StyledDocument doc = history.getStyledDocument();
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final Color SYSTEM_FG = new Color(0x77, 0x77, 0x77);

    /**
     * Linha de "sistema" (eventos da partida — desfazer, vitória, revanche…). Itálico, cinza, sem
     * {@code "nick:"}, para não se confundir com fala de jogador.
     */
    public void system(String text) {
        StyledDocument doc = history.getStyledDocument();

        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, SYSTEM_FG);
        StyleConstants.setItalic(style, true);

        try {
            doc.insertString(doc.getLength(), "• " + text + "\n", style);
        } catch (BadLocationException impossible) {
            throw new IllegalStateException(impossible);
        }
        history.setCaretPosition(doc.getLength());
    }

    /** Acrescenta uma linha ao histórico. */
    public void append(String nick, String text, Color color) {
        StyledDocument doc = history.getStyledDocument();

        SimpleAttributeSet nameStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(nameStyle, color);
        StyleConstants.setBold(nameStyle, true);

        try {
            doc.insertString(doc.getLength(), nick + ": ", nameStyle);
            doc.insertString(doc.getLength(), text + "\n", new SimpleAttributeSet());
        } catch (BadLocationException impossible) {
            throw new IllegalStateException(impossible);
        }
        history.setCaretPosition(doc.getLength());
    }
}
