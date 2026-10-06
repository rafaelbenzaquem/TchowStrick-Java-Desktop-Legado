package br.com.mss.tchow.ui;

import br.com.mss.tchow.app.SessionTokenStore;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.MatchDiscovery;
import br.com.mss.tchow.net.TransportException;
import br.com.mss.tchow.net.match.MatchId;
import br.com.mss.tchow.net.match.OpenMatchSummary;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/**
 * Diálogo modal para entrar numa partida. Lobby (ADR-0012, {@code [E4a-05c]}): lista as partidas
 * abertas do servidor ({@code Buscar partidas}) — escolher uma linha conecta com o {@code match_id}
 * dela, nunca mais "a única partida aberta" às cegas. Uma partida trancada (🔒) pede a senha antes
 * de habilitar {@code Conectar}.
 *
 * <p><b>Entrar vs. Retornar</b> (achado testando reconexão de verdade com o cliente Godot mobile,
 * 2026-09-20): se este perfil já tem um {@code session_token} salvo pra partida selecionada ({@link
 * SessionTokenStore#knownColorFor}), a cor não é mais uma escolha — é a mesma de sempre, travada no
 * combo, e o botão vira "Retornar". Antes disto, o jogador tinha que lembrar/adivinhar qual das
 * cores "livres" era a dele numa partida que já tinha jogado; escolher errado criava um assento
 * novo em vez de retomar o seu. Uma partida trancada ainda pede a senha mesmo retornando — o {@code
 * session_token} prova a cor, não a senha da sala (o servidor confere as duas à parte).
 */
public final class JoinDialog extends JDialog {

    /**
     * Sem campo de servidor/porta ([E4.5-07], ADR-0017): quem chama já sabe qual servidor usar (o
     * ativo, escolhido no {@code ServerPickerDialog}) — só passado ao construtor, nunca exibido.
     */
    public record Result(String nick, PlayerColor color, MatchId matchId, String password) {}

    private final String host;
    private final int port;
    private final boolean tls;
    private final JTextField nickField = new JTextField(18);
    private final DefaultListModel<OpenMatchSummary> matchesModel = new DefaultListModel<>();
    private final JList<OpenMatchSummary> matchesList = new JList<>(matchesModel);
    private final JComboBox<PlayerColor> colorCombo = new FittingComboBox<>();
    private final JLabel passwordLabel = new JLabel("Senha:");
    private final JPasswordField passwordField = new JPasswordField(12);
    private final JLabel infoLabel = new JLabel(" ");
    private final JButton searchButton = new JButton("Buscar partidas");
    private final JButton connectButton = new JButton("Conectar");

    private final transient MatchDiscovery discovery;
    private final transient SessionTokenStore sessionTokenStore;
    private Result result;

    /**
     * {@code initialNick} pré-preenche o campo (nome do perfil ativo); editável. {@code host}/
     * {@code port}/{@code tls} são o servidor ativo ([E4.5-06]/[E5-02], ADR-0017/ADR-0005) — usados
     * direto por {@link #search()}, nunca exibidos nem editáveis aqui. {@code sessionTokenStore} é
     * o mesmo do resto do app ({@code Main#sessionTokenStore}) — decide Entrar vs. Retornar por
     * linha.
     */
    public JoinDialog(
            Window owner,
            MatchDiscovery discovery,
            SessionTokenStore sessionTokenStore,
            String initialNick,
            String host,
            int port,
            boolean tls) {
        super(owner, "Entrar numa partida", ModalityType.APPLICATION_MODAL);
        this.discovery = discovery;
        this.sessionTokenStore = sessionTokenStore;
        this.host = host == null || host.isBlank() ? "localhost" : host;
        this.port = port;
        this.tls = tls;
        nickField.setText(
                initialNick == null || initialNick.isBlank() ? "jogador" : initialNick.strip());
        buildUi();
        colorCombo.setEnabled(false);
        connectButton.setEnabled(false);
        setPasswordEnabled(false);
        // redimensionável: a lista de partidas cresce junto; o mínimo mantém tudo visível
        UiSizing.packWithin(this, owner);
    }

    /** Abre o diálogo (bloqueia) e devolve as escolhas, ou {@code null} se cancelado. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }

    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(UiSizing.dialogPadding());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, "Nick:", nickField);

        c.gridx = 1;
        c.gridy = row++;
        c.gridwidth = 1;
        form.add(searchButton, c);

        matchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        matchesList.setVisibleRowCount(5);
        matchesList.setCellRenderer(new MatchCellRenderer());
        JScrollPane matchesScroll = new JScrollPane(matchesList);
        // Largura/altura fixas, independente do texto da linha ([E4.5-05]: "livres:" agora pode
        // listar até 5 cores, bem mais longo que antes) — sem isso, o GridBagLayout recalcula as
        // colunas pelo conteúdo mais largo já carregado (o diálogo já foi empacotado com a lista
        // vazia, `setResizable(false)`) e o formulário inteiro se desconfigura. Texto mais longo
        // que isso rola horizontalmente dentro da lista, sem afetar o resto do diálogo.
        matchesScroll.setPreferredSize(new Dimension(440, 120));
        matchesScroll.setMinimumSize(new Dimension(220, 60));
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        form.add(matchesScroll, c);
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.weighty = 0;

        addRow(form, c, row++, "Sua cor:", colorCombo);

        addRow(form, c, row, passwordLabel, passwordField);
        row++;

        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(infoLabel, c);
        c.fill = GridBagConstraints.NONE;

        JButton cancel = new JButton("Cancelar");
        JPanel buttons = new JPanel();
        buttons.add(connectButton);
        buttons.add(cancel);
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        form.add(buttons, c);

        searchButton.addActionListener(e -> search());
        matchesList.addListSelectionListener(
                e -> {
                    if (!e.getValueIsAdjusting()) {
                        onSelectionChanged();
                    }
                });
        connectButton.addActionListener(
                e -> {
                    OpenMatchSummary selected = matchesList.getSelectedValue();
                    if (selected == null) {
                        return;
                    }
                    result =
                            new Result(
                                    nickField.getText(),
                                    (PlayerColor) colorCombo.getSelectedItem(),
                                    selected.id(),
                                    new String(passwordField.getPassword()));
                    dispose();
                });
        cancel.addActionListener(e -> dispose());

        setContentPane(form);
        getRootPane().setDefaultButton(searchButton);
    }

    private void search() {
        searchButton.setEnabled(false);
        showInfo("procurando…");
        matchesModel.clear();
        onSelectionChanged();

        new SwingWorker<List<OpenMatchSummary>, Void>() {
            private String error;

            @Override
            protected List<OpenMatchSummary> doInBackground() {
                try {
                    return discovery.listOpenMatches(host, port, tls);
                } catch (TransportException ex) {
                    error = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                List<OpenMatchSummary> matches;
                try {
                    matches = get();
                } catch (Exception ex) {
                    matches = null;
                    error = ex.getMessage();
                }
                if (matches == null) {
                    showInfo(error);
                    return;
                }
                applyMatches(matches);
            }
        }.execute();
    }

    private void applyMatches(List<OpenMatchSummary> matches) {
        matches.forEach(matchesModel::addElement);
        showInfo(
                matches.isEmpty()
                        ? "nenhuma partida aberta neste servidor"
                        : matches.size() + " partida(s) aberta(s) — escolha uma");
    }

    private void onSelectionChanged() {
        OpenMatchSummary selected = matchesList.getSelectedValue();
        colorCombo.removeAllItems();
        if (selected == null) {
            colorCombo.setEnabled(false);
            connectButton.setEnabled(false);
            connectButton.setText("Conectar");
            setPasswordEnabled(false);
            return;
        }
        Optional<PlayerColor> known = sessionTokenStore.knownColorFor(selected.id());
        if (known.isPresent()) {
            // Retornar: a cor não é escolha, é a de sempre — trava o combo nela (ainda visível,
            // só não editável) em vez de deixar as `availableColors()` livres à mostra, que
            // incluiriam a nossa cor misturada com cores de gente que nunca jogou aqui.
            colorCombo.addItem(known.get());
            colorCombo.setSelectedItem(known.get());
            colorCombo.setEnabled(false);
            connectButton.setText("Retornar");
        } else {
            selected.availableColors().forEach(colorCombo::addItem);
            colorCombo.setEnabled(true);
            connectButton.setText("Conectar");
        }
        connectButton.setEnabled(true);
        setPasswordEnabled(selected.locked());
        getRootPane().setDefaultButton(connectButton);
    }

    /**
     * Mensagem de estado/erro abaixo da lista. Erros de rede podem ser longos: quebram linha na
     * largura da lista e a janela cresce na altura, em vez de esticar o formulário para os lados.
     */
    private void showInfo(String text) {
        String value = text == null || text.isBlank() ? " " : text;
        infoLabel.setText(UiSizing.wrappedHtml(UiSizing.escapeHtml(value), 400));
        UiSizing.growToPreferred(this);
    }

    /** Só habilita o campo de senha para partida trancada — e limpa ao desabilitar. */
    private void setPasswordEnabled(boolean enabled) {
        passwordLabel.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        if (!enabled) {
            passwordField.setText("");
        }
    }

    private static void addRow(
            JPanel form, GridBagConstraints c, int row, String label, java.awt.Component field) {
        addRow(form, c, row, new JLabel(label), field);
    }

    private static void addRow(
            JPanel form, GridBagConstraints c, int row, JLabel label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(label, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(field, c);
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
    }

    /** "WxH · entraram/total jogadores · livres: X, Y" — 🔒 se trancada por senha. */
    private static final class MatchCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
            if (value instanceof OpenMatchSummary summary) {
                // maxPlayers vem explícito ([E4.5-05], ADR-0015) — joined+availableColors não é
                // mais o total numa partida de elenco aberto (availableColors pode listar até as 5
                // cores mesmo faltando 1 vaga só).
                String free =
                        summary.availableColors().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining(", "));
                setText(
                        "%dx%d · %d/%d jogadores · livres: %s%s"
                                .formatted(
                                        summary.width(),
                                        summary.height(),
                                        summary.joined().size(),
                                        summary.maxPlayers(),
                                        free,
                                        summary.locked() ? "  🔒" : ""));
            }
            return this;
        }
    }
}
