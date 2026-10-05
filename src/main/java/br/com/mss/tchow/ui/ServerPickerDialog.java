package br.com.mss.tchow.ui;

import br.com.mss.tchow.net.config.ServerDirectory;
import br.com.mss.tchow.net.config.ServerPreset;
import br.com.mss.tchow.net.discovery.DiscoveredServer;
import br.com.mss.tchow.net.discovery.LanServerFinder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.Duration;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/**
 * Diálogo de troca de servidor ([E4.5-06], ADR-0017) — substitui o pop-up reativo do {@code
 * [E4.5-04]}: em vez de perguntar uma vez se quer trocar pro que a descoberta achou, mostra uma
 * lista de verdade (presets do {@code servers.json}, sempre incluindo o "Oficial") e deixa buscar
 * mais na rede local sob pedido. "Endereço personalizado…" continua disponível pra quem realmente
 * precisa digitar um IP:porta específico, mas fica dentro deste diálogo — nunca é o caminho comum.
 */
public final class ServerPickerDialog extends JDialog {

    private final DefaultListModel<ServerPreset> model = new DefaultListModel<>();
    private final JList<ServerPreset> list = new JList<>(model);
    private final JButton searchButton = new JButton("Procurar na rede local");
    private final JButton okButton = new JButton("Usar este servidor");
    private final JLabel infoLabel = new JLabel(" ");

    private ServerPreset result;

    /** {@code current} pré-seleciona a linha correspondente na lista, se houver uma igual. */
    public ServerPickerDialog(Window owner, ServerPreset current) {
        super(owner, "Trocar servidor", ModalityType.APPLICATION_MODAL);
        buildUi();
        loadPresets(current);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Abre o diálogo (bloqueia) e devolve o servidor escolhido, ou {@code null} se cancelado. */
    public ServerPreset showDialog() {
        setVisible(true);
        return result;
    }

    private void loadPresets(ServerPreset current) {
        ServerDirectory.load().presets().forEach(model::addElement);
        selectMatching(current);
    }

    private void selectMatching(ServerPreset target) {
        if (target == null) {
            return;
        }
        for (int i = 0; i < model.size(); i++) {
            ServerPreset preset = model.get(i);
            if (preset.host().equals(target.host()) && preset.port() == target.port()) {
                list.setSelectedIndex(i);
                return;
            }
        }
    }

    private void buildUi() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ServerCellRenderer());
        list.addListSelectionListener(e -> okButton.setEnabled(list.getSelectedValue() != null));

        JScrollPane scroll = new JScrollPane(list);
        // Tamanho fixo, independente de quantos servidores a busca em LAN acrescentar depois do
        // pack() — mesmo bug de layout do JoinDialog (v1.1.5) evitado de propósito.
        scroll.setPreferredSize(new Dimension(360, 120));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(searchButton);
        JButton customButton = new JButton("Endereço personalizado…");
        top.add(customButton);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(infoLabel, BorderLayout.NORTH);
        JButton cancel = new JButton("Cancelar");
        JPanel buttons = new JPanel();
        buttons.add(okButton);
        buttons.add(cancel);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> search());
        customButton.addActionListener(e -> chooseCustomAddress());
        okButton.addActionListener(e -> confirmSelection());
        cancel.addActionListener(e -> dispose());
        okButton.setEnabled(!model.isEmpty());

        setContentPane(content);
        getRootPane().setDefaultButton(okButton);
    }

    private void confirmSelection() {
        ServerPreset selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        result = selected;
        dispose();
    }

    private void search() {
        searchButton.setEnabled(false);
        infoLabel.setText("procurando…");
        new SwingWorker<List<DiscoveredServer>, Void>() {
            @Override
            protected List<DiscoveredServer> doInBackground() {
                return LanServerFinder.find(Duration.ofSeconds(1));
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                List<DiscoveredServer> found;
                try {
                    found = get();
                } catch (Exception ex) {
                    found = List.of();
                }
                int added = appendNewOnes(found);
                infoLabel.setText(
                        added == 0
                                ? "nenhum servidor novo encontrado na rede local"
                                : added + " servidor(es) encontrado(s) na rede local");
            }
        }.execute();
    }

    private int appendNewOnes(List<DiscoveredServer> found) {
        int added = 0;
        for (DiscoveredServer server : found) {
            if (!containsHostPort(server.host(), server.port())) {
                // Descoberta em LAN é sempre em claro (sem Caddy/cert por trás na rede local) e
                // nunca oficial ([E6-13] — só o preset do servers.json marcado exige conta).
                model.addElement(
                        new ServerPreset(
                                server.name(), server.host(), server.port(), false, false, false));
                added++;
            }
        }
        return added;
    }

    private boolean containsHostPort(String host, int port) {
        for (int i = 0; i < model.size(); i++) {
            ServerPreset preset = model.get(i);
            if (preset.host().equals(host) && preset.port() == port) {
                return true;
            }
        }
        return false;
    }

    private void chooseCustomAddress() {
        JCheckBox secureCheckbox = new JCheckBox("Usar conexão segura (TLS)");
        JTextField addressField = new JTextField();
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(secureCheckbox);
        panel.add(new JLabel("Endereço (host:porta):"));
        panel.add(addressField);

        int choice =
                JOptionPane.showConfirmDialog(
                        this,
                        panel,
                        "Endereço personalizado",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        String input = addressField.getText();
        if (input == null || input.isBlank()) {
            return;
        }
        int separator = input.lastIndexOf(':');
        if (separator <= 0 || separator == input.length() - 1) {
            warnInvalidAddress("Formato esperado: host:porta");
            return;
        }
        String host = input.substring(0, separator).strip();
        int port;
        try {
            port = Integer.parseInt(input.substring(separator + 1).strip());
        } catch (NumberFormatException e) {
            warnInvalidAddress("Porta inválida.");
            return;
        }
        // Nunca oficial ([E6-13]) — endereço digitado à mão não passa pelo fluxo de conta guiado.
        result =
                new ServerPreset(
                        "personalizado", host, port, secureCheckbox.isSelected(), false, false);
        dispose();
    }

    private void warnInvalidAddress(String message) {
        JOptionPane.showMessageDialog(
                this, message, "Endereço inválido", JOptionPane.WARNING_MESSAGE);
    }

    /** {@code "<nome> (<host>:<porta>)"} — 🔒 marca TLS, ★ marca o preset padrão. */
    private static final class ServerCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
            if (value instanceof ServerPreset preset) {
                setText(
                        "%s%s (%s:%d)%s"
                                .formatted(
                                        preset.tls() ? "🔒 " : "",
                                        preset.name(),
                                        preset.host(),
                                        preset.port(),
                                        preset.isDefault() ? "  ★" : ""));
            }
            return this;
        }
    }
}
