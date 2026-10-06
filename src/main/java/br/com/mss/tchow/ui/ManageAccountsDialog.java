package br.com.mss.tchow.ui;

import br.com.mss.tchow.app.LocalAccountsService.Entry;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * "Jogador → Gerenciar contas…" (M1): contas e perfis guardados <b>neste computador</b> — contas
 * MSS, conta oficial antiga, perfis de jogador e perfis locais (janelas). Só mostra e repassa as
 * escolhas; quem abriu executa (fluxos existentes de adicionar, confirmação e remoção local).
 * Operações no servidor (excluir conta, sair de todos) continuam em "Conta MSS…".
 */
public final class ManageAccountsDialog extends JDialog {

    /** Ações executadas por quem abriu o painel; a tabela é recarregada depois de cada uma. */
    public interface Actions {
        List<Entry> list();

        void addMss();

        void addLegacy();

        void addPlayer();

        void addWindow();

        void remove(Entry entry);
    }

    private static final String[] COLUMNS = {
        "Tipo", "Nome/nick", "Servidor ou identidade", "Conta", "Perfil local", "Estado", "Em uso"
    };

    private final transient Actions actions;
    private final EntriesModel model = new EntriesModel();
    private final JTable table = new JTable(model);
    private final JButton remove = new JButton("Remover deste computador…");

    public ManageAccountsDialog(Window owner, Actions actions) {
        super(owner, "Gerenciar contas neste computador", ModalityType.APPLICATION_MODAL);
        this.actions = actions;
        buildUi();
        reload();
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.setAutoCreateRowSorter(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(900, 260));

        JButton addMss = new JButton("Adicionar conta MSS…");
        addMss.addActionListener(e -> run(actions::addMss));
        JButton addLegacy = new JButton("Adicionar conta oficial antiga…");
        addLegacy.addActionListener(e -> run(actions::addLegacy));
        JButton addPlayer = new JButton("Novo perfil de jogador…");
        addPlayer.addActionListener(e -> run(actions::addPlayer));
        JButton addWindow = new JButton("Nova janela (perfil local)…");
        addWindow.addActionListener(e -> run(actions::addWindow));
        remove.addActionListener(
                e -> {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        Entry entry = model.entries.get(row);
                        run(() -> actions.remove(entry));
                    }
                });
        JButton refresh = new JButton("Atualizar");
        refresh.addActionListener(e -> reload());
        JButton close = new JButton("Fechar");
        close.addActionListener(e -> dispose());

        JPanel add = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        add.add(addMss);
        add.add(addLegacy);
        add.add(addPlayer);
        add.add(addWindow);
        JPanel manage = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        manage.add(remove);
        manage.add(refresh);
        manage.add(close);
        JPanel south = new JPanel(new BorderLayout());
        south.add(add, BorderLayout.NORTH);
        south.add(manage, BorderLayout.SOUTH);

        JLabel hint =
                new JLabel(
                        "<html>Dados guardados só neste computador (nunca tokens). Remover apaga"
                                + " apenas os dados locais; excluir a conta no servidor fica em"
                                + " Jogador → Conta MSS….</html>");
        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(hint, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void run(Runnable action) {
        action.run();
        reload();
    }

    private void reload() {
        model.entries = List.copyOf(actions.list());
        model.fireTableDataChanged();
        updateButtons();
    }

    private void updateButtons() {
        remove.setEnabled(table.getSelectedRow() >= 0);
    }

    private static final class EntriesModel extends AbstractTableModel {
        private List<Entry> entries = List.of();

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Entry e = entries.get(row);
            return switch (column) {
                case 0 -> e.kind().label();
                case 1 -> e.name();
                case 2 -> e.destination();
                case 3 -> e.account();
                case 4 -> e.dataProfileLabel();
                case 5 -> e.state();
                default -> e.usage().label();
            };
        }
    }
}
