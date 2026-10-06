package br.com.mss.tchow.sandbox;

import br.com.mss.tchow.LaunchOptions;
import br.com.mss.tchow.Main;
import br.com.mss.tchow.app.DataProfile;
import br.com.mss.tchow.app.LocalAccountsService;
import br.com.mss.tchow.app.LocalProfileStore;
import br.com.mss.tchow.app.SessionTokenStore;
import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.GameEngine;
import br.com.mss.tchow.domain.Move;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.domain.ai.AiLevel;
import br.com.mss.tchow.domain.history.BoardSpec;
import br.com.mss.tchow.domain.history.MoveLog;
import br.com.mss.tchow.net.Dtos.PlayerDto;
import br.com.mss.tchow.net.Dtos.PlayerStatsDto;
import br.com.mss.tchow.net.GameTransport;
import br.com.mss.tchow.net.LocalTransport;
import br.com.mss.tchow.net.MatchDiscovery;
import br.com.mss.tchow.net.match.MatchId;
import br.com.mss.tchow.net.match.OpenMatchSummary;
import br.com.mss.tchow.ui.ChatPanel;
import br.com.mss.tchow.ui.ConfirmContactCodeDialog;
import br.com.mss.tchow.ui.CreateOfficialAccountDialog;
import br.com.mss.tchow.ui.HostDialog;
import br.com.mss.tchow.ui.JoinDialog;
import br.com.mss.tchow.ui.ManageAccountsDialog;
import br.com.mss.tchow.ui.MssAccountDialog;
import br.com.mss.tchow.ui.ProfileDialog;
import br.com.mss.tchow.ui.ReplayViewer;
import br.com.mss.tchow.ui.ServerPickerDialog;
import br.com.mss.tchow.ui.StatsDialog;
import br.com.mss.tchow.ui.UiSizing;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Harness de capturas da GUI — <b>não faz parte do jogo distribuído</b> (vive em {@code src/test},
 * fora do jar, e não roda no {@code verify}).
 *
 * <p>Instancia cada janela/diálogo com dados sintéticos longos (nicks, e-mails, mensagens de erro,
 * placar de 5 jogadores, tabuleiros 2x2/5x5/12x12, chat cheio, Gerenciar contas com várias linhas),
 * sem rede: preferências só em memória ({@link MemoryPreferencesFactory}), perfil de dados em
 * diretório temporário e {@code servers.json} próprio apontando só para {@code localhost}. Pinta
 * cada janela num PNG na escala do {@code -Dsun.java2d.uiScale} (padrão 1.0) e imprime um
 * diagnóstico: componentes menores que o tamanho preferido (texto cortado) e janelas maiores que a
 * área útil de telas simuladas (1366×768 e 1920×1080 físicos).
 *
 * <p>Rodar (classpath de {@code dependency:build-classpath} + {@code target/classes} + {@code
 * target/test-classes}): {@code java -Dsun.java2d.uiScale=1.5 -cp ... GuiShots <dirSaida>}.
 */
public final class GuiShots {

    private static final String LONG_NICK = "Maria Fernanda de Albuquerque Nascimento";
    private static final String LONG_EMAIL =
            "maria.fernanda.albuquerque.nascimento@exemplo-muito-longo.com.br";
    private static final String LONG_ERROR =
            "Não foi possível conectar ao servidor localhost:5050: UNAVAILABLE: io exception —"
                    + " Connection refused: no further information: localhost/127.0.0.1:5050."
                    + " Verifique se o servidor está no ar e tente de novo.";

    private final Path outDir;
    private final double scale;
    private final List<String> report = new ArrayList<>();
    private final Path dataDir;

    private GuiShots(Path outDir, double scale, Path dataDir) {
        this.outDir = outDir;
        this.scale = scale;
        this.dataDir = dataDir;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty(
                "java.util.prefs.PreferencesFactory", MemoryPreferencesFactory.class.getName());
        Path out = Path.of(args.length > 0 ? args[0] : "gui-shots");
        Files.createDirectories(out);
        Path dataDir = Files.createTempDirectory("tchow-gui-shots");
        System.setProperty(DataProfile.DATA_DIR_PROPERTY, dataDir.toString());
        Path servers = dataDir.resolve("servers.json");
        Files.writeString(
                servers,
                """
                [
                  {"name": "Servidor de testes da comunidade MSS — Belém (PA)", "host": "localhost",
                   "port": 5050, "tls": false, "default": true, "official": false,
                   "identity": "localhost:9100", "identityTls": false},
                  {"name": "Local (sem conta)", "host": "localhost", "port": 5050, "tls": false,
                   "default": false, "official": false},
                  {"name": "Servidor alternativo com nome bem comprido para testar a lista",
                   "host": "servidor-alternativo.exemplo.local", "port": 50555, "tls": true,
                   "default": false, "official": false}
                ]
                """,
                StandardCharsets.UTF_8);
        System.setProperty("tchow.servers.file", servers.toString());
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        String rawScale = System.getProperty("sun.java2d.uiScale", "1.0");
        double scale = Double.parseDouble(rawScale.replace("x", ""));
        GuiShots shots = new GuiShots(out, scale, dataDir);
        SwingUtilities.invokeAndWait(shots::runAll);
        Files.write(
                out.resolve("relatorio-" + rawScale + ".txt"),
                shots.report,
                StandardCharsets.UTF_8);
        shots.report.forEach(System.out::println);
        System.exit(0);
    }

    private void runAll() {
        try {
            shotMain();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        JFrame owner = new JFrame("owner");
        DataProfile dialogsProfile = DataProfile.acquire(dataDir, "dialogos");
        LocalProfileStore profiles = new LocalProfileStore(dialogsProfile);
        profiles.create(LONG_NICK);
        profiles.create("Zé");
        profiles.create("Jogador com um nome de perfil razoavelmente comprido 2");

        shot("host", () -> new HostDialog(owner, LONG_NICK));
        shot("join", () -> joinDialog(owner));
        shot("server-picker", () -> new ServerPickerDialog(owner, null));
        shot("profile", () -> new ProfileDialog(owner, profiles));
        shot(
                "stats",
                () -> new StatsDialog(owner, new PlayerStatsDto(123456, 98765, 24690, 1, 9876543)));
        shot(
                "mss-account",
                () ->
                        new MssAccountDialog(
                                owner,
                                new MssAccountDialog.View(
                                        "Servidor de testes da comunidade MSS — Belém (PA) · perfil"
                                                + " local perfil-trabalho-2",
                                        "Conta restrita: confirme o e-mail em até 7 dias para"
                                                + " continuar jogando partidas em rede.",
                                        LONG_NICK,
                                        "avatar-padrao-tchow-azul",
                                        "ma***************@exemplo-muito-longo.com.br",
                                        false,
                                        true)));
        shot(
                "mss-account-indisponivel",
                () ->
                        new MssAccountDialog(
                                owner,
                                new MssAccountDialog.View(
                                        "Local", "Conta ativa", "", "", "", false, false)));
        shot("manage-accounts", () -> new ManageAccountsDialog(owner, manageActions()));
        shot(
                "confirm-code",
                () ->
                        new ConfirmContactCodeDialog(
                                owner, "ma***************@exemplo-muito-longo.com.br", () -> {}));
        shot(
                "create-official",
                () -> new CreateOfficialAccountDialog(owner, LONG_NICK, true, true, true));
        shot("replay-12x12", () -> replay(owner, 12, 12, 5, 260));
        shot("replay-2x2", () -> replay(owner, 2, 2, 2, 12));

        // JOptionPanes com textos longos (mesmos textos do Main).
        shot("msg-erro-transporte", () -> message(owner, LONG_ERROR, JOptionPane.WARNING_MESSAGE));
        shot(
                "msg-exige-conta",
                () ->
                        message(
                                owner,
                                "O servidor Servidor de testes da comunidade MSS — Belém (PA) só"
                                        + " aceita jogadores com conta MSS. Entre ou crie a conta"
                                        + " em Jogador → Conta MSS… para jogar.",
                                JOptionPane.WARNING_MESSAGE));
        shot(
                "msg-conta-antiga",
                () ->
                        message(
                                owner,
                                "A conta oficial antiga (tchowstrick.auth.v1) só existe em"
                                        + " servidores sem identidade MSS que a aceitem. O servidor"
                                        + " oficial agora usa conta MSS: use Adicionar conta MSS….",
                                JOptionPane.WARNING_MESSAGE));
        shot(
                "msg-adicionar-mss",
                () ->
                        message(
                                owner,
                                "Esta janela (perfil local perfil-trabalho-2) já está na conta MSS "
                                        + LONG_NICK
                                        + ".\nPara jogar com outra conta ao mesmo tempo, abra uma"
                                        + " nova janela: ela usa outro perfil local, com a sua"
                                        + " própria conta.",
                                JOptionPane.QUESTION_MESSAGE));
        shot(
                "msg-nova-janela",
                () ->
                        input(
                                owner,
                                "Nome do perfil local da nova janela (vazio = próximo livre, ex.:"
                                        + " perfil-2):"));
        shot("msg-remover", () -> removeEntryDialog(owner));
    }

    // ------------------------------------------------------------------ Main

    private void shotMain() throws Exception {
        DataProfile mainProfile = DataProfile.acquire(dataDir, "perfil-trabalho-2");
        LocalProfileStore store = new LocalProfileStore(mainProfile);
        store.create(LONG_NICK);
        Main idle = new Main(LaunchOptions.parse(new String[0]), mainProfile);
        render(idle, "main-idle");
        // Partidas locais contra a IA em três tamanhos.
        matchShot(idle, "main-match-2x2", 2, 2, 2);
        leave(idle);
        matchShot(idle, "main-match-2x2-5p", 2, 2, 5);
        leave(idle);
        matchShot(idle, "main-match-5x5-5p", 5, 5, 5);
        leave(idle);
        matchShot(idle, "main-match-12x12-5p", 12, 12, 5);
        idle.dispose();
    }

    private void matchShot(Main main, String name, int w, int h, int players) throws Exception {
        List<PlayerColor> roster = List.of(PlayerColor.values()).subList(0, players);
        List<LocalTransport.Bot> bots = new ArrayList<>();
        for (PlayerColor c : roster.subList(1, roster.size())) {
            bots.add(new LocalTransport.Bot(c, "IA Difícil", AiLevel.HARD.newStrategy()));
        }
        LocalTransport transport =
                new LocalTransport(w, h, roster, roster.get(0), LONG_NICK, bots, 42L);
        Method start = Main.class.getDeclaredMethod("startMatch", GameTransport.class);
        start.setAccessible(true);
        start.invoke(main, transport);
        ChatPanel chat = find(main, ChatPanel.class);
        if (chat != null) {
            chat.system("partida criada: tabuleiro " + w + "x" + h + ", " + players + " jogadores");
            chat.append(
                    LONG_NICK,
                    "boa sorte a todos, que vença o melhor jogador desta rodada!",
                    Color.RED);
            chat.append(
                    "IA Difícil", "😀 obrigado! vamos ver quem fecha mais quadrados", Color.BLUE);
            chat.system("você desfez sua jogada (restam 0 desfazer nesta partida)");
        }
        render(main, name);
    }

    private static void leave(Main main) throws Exception {
        Method leave = Main.class.getDeclaredMethod("onLeave");
        leave.setAccessible(true);
        leave.invoke(main);
    }

    // ------------------------------------------------------------------ diálogos

    private JoinDialog joinDialog(Window owner) {
        MatchDiscovery discovery =
                new MatchDiscovery() {
                    @Override
                    public br.com.mss.tchow.net.Dtos.MatchInfoDto peek(
                            String host, int port, boolean tls) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public List<OpenMatchSummary> listOpenMatches(
                            String host, int port, boolean tls) {
                        return List.of();
                    }
                };
        SessionTokenStore tokens =
                new SessionTokenStore() {
                    @Override
                    public Optional<String> find(MatchId matchId, PlayerColor color) {
                        return Optional.empty();
                    }

                    @Override
                    public void save(MatchId matchId, PlayerColor color, String token) {}
                };
        JoinDialog dialog =
                new JoinDialog(owner, discovery, tokens, LONG_NICK, "localhost", 5050, false);
        List<OpenMatchSummary> matches = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            matches.add(
                    new OpenMatchSummary(
                            MatchId.random(),
                            12,
                            10 + (i % 3),
                            List.of(new PlayerDto(PlayerColor.RED, LONG_NICK)),
                            List.of(
                                    PlayerColor.BLUE,
                                    PlayerColor.GREEN,
                                    PlayerColor.YELLOW,
                                    PlayerColor.PINK),
                            5,
                            i % 2 == 0));
        }
        try {
            Method apply = JoinDialog.class.getDeclaredMethod("applyMatches", List.class);
            apply.setAccessible(true);
            apply.invoke(dialog, matches);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        @SuppressWarnings("unchecked")
        JList<OpenMatchSummary> list = find(dialog, JList.class);
        list.setSelectedIndex(0);
        return dialog;
    }

    private static ManageAccountsDialog.Actions manageActions() {
        List<LocalAccountsService.Entry> entries = new ArrayList<>();
        LocalAccountsService.Kind[] kinds = LocalAccountsService.Kind.values();
        for (int i = 0; i < 9; i++) {
            entries.add(
                    new LocalAccountsService.Entry(
                            kinds[i % kinds.length],
                            i == 0 ? "padrao" : "perfil-trabalho-" + i,
                            "k" + i,
                            i % 2 == 0 ? LONG_NICK : "Zé",
                            i % 3 == 0
                                    ? "identidade localhost:9100 (Servidor de testes da comunidade MSS)"
                                    : "conta oficial antiga (tchowstrick.auth.v1)",
                            i % 2 == 0 ? LONG_EMAIL : "—",
                            i % 2 == 0 ? "sessão salva (expira em 29/10/2026 18:30)" : "sem sessão",
                            LocalAccountsService.Usage.values()[i % 3]));
        }
        return new ManageAccountsDialog.Actions() {
            @Override
            public List<LocalAccountsService.Entry> list() {
                return entries;
            }

            @Override
            public void addMss() {}

            @Override
            public void addLegacy() {}

            @Override
            public void addPlayer() {}

            @Override
            public void addWindow() {}

            @Override
            public void remove(LocalAccountsService.Entry entry) {}
        };
    }

    private static ReplayViewer replay(Window owner, int w, int h, int players, int moves) {
        List<PlayerColor> roster = List.of(PlayerColor.values()).subList(0, players);
        BoardSpec spec = new BoardSpec(w, h, roster);
        GameEngine engine = spec.newEngine();
        Random random = new Random(7);
        MoveLog log = MoveLog.empty();
        for (int i = 0; i < moves && !engine.isFinished(); i++) {
            List<Edge> free = engine.freeEdges();
            Move move = new Move(engine.currentPlayer(), free.get(random.nextInt(free.size())));
            engine.applyMove(move);
            log = log.append(move);
        }
        ReplayViewer viewer =
                new ReplayViewer(
                        owner,
                        "Replay — %dx%d, %d jogada(s)".formatted(w, h, log.size()),
                        spec,
                        log);
        return viewer;
    }

    private static JDialog message(Window owner, Object message, int type) {
        Object content = message instanceof String text ? UiSizing.message(text) : message;
        return new JOptionPane(content, type).createDialog(owner, "TchowStrick");
    }

    private static JDialog input(Window owner, String message) {
        JOptionPane pane =
                new JOptionPane(
                        UiSizing.message(message),
                        JOptionPane.PLAIN_MESSAGE,
                        JOptionPane.OK_CANCEL_OPTION);
        pane.setWantsInput(true);
        return pane.createDialog(owner, "Nova janela");
    }

    /** Réplica do formulário de {@code Main#removeLocalEntry}. */
    private static JDialog removeEntryDialog(Window owner) {
        JPanel form = new JPanel(new java.awt.BorderLayout(0, 8));
        form.add(
                UiSizing.wrappedLabel(
                        "Remover deste computador a conta MSS "
                                + LONG_NICK
                                + " ("
                                + LONG_EMAIL
                                + ") guardada no perfil local perfil-trabalho-2. A sessão salva"
                                + " neste computador será apagada; a conta continua existindo no"
                                + " servidor e pode ser usada de novo entrando com o e-mail."),
                java.awt.BorderLayout.CENTER);
        form.add(
                new javax.swing.JCheckBox("Sair também no servidor (só este dispositivo)", false),
                java.awt.BorderLayout.SOUTH);
        JOptionPane pane =
                new JOptionPane(form, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        return pane.createDialog(owner, "Remover deste computador");
    }

    // ------------------------------------------------------------------ captura

    private void shot(String name, Supplier<Window> factory) {
        Window window;
        try {
            window = factory.get();
        } catch (RuntimeException e) {
            report.add(name + ": FALHOU ao criar — " + e);
            return;
        }
        render(window, name);
        window.dispose();
    }

    private void render(Window window, String name) {
        if (!window.isDisplayable()) {
            window.addNotify(); // cria o peer sem mostrar, para o layout acontecer
        }
        settle(window);
        Dimension size = window.getSize();
        Container root = ((RootPaneContainer) window).getRootPane();
        int w = Math.max(1, (int) Math.ceil(root.getWidth() * scale));
        int h = Math.max(1, (int) Math.ceil(root.getHeight() * scale));
        BufferedImage image = new BufferedImage(w + 2, h + 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, w + 2, h + 2);
        g.translate(1, 1);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        root.printAll(g);
        g.dispose();
        String file = name + "@" + scale + ".png";
        try {
            ImageIO.write(image, "png", outDir.resolve(file).toFile());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        report.add(
                "%s: janela %dx%d (pref %s, min %s) → %s"
                        .formatted(
                                name,
                                size.width,
                                size.height,
                                fmt(window.getPreferredSize()),
                                fmt(window.getMinimumSize()),
                                file));
        boolean tooBig = checkScreens(name, size);
        inspect(name, root);
        if (tooBig && !name.endsWith("-tela1366")) {
            // como a janela fica numa tela 1366x768 (área útil simulada), com as regras de UiSizing
            UiSizing.fitWithin(window, small1366(), null);
            render(window, name + "-tela1366");
        }
    }

    private Rectangle small1366() {
        return new Rectangle(0, 0, (int) (1366 / scale), (int) (768 / scale) - 48);
    }

    /** Valida duas vezes: layouts que dependem da largura (WrapLayout) assentam na 2ª passada. */
    private static void settle(Window window) {
        window.validate();
        invalidateTree(window);
        window.validate();
    }

    private static void invalidateTree(Container container) {
        container.invalidate();
        for (Component child : container.getComponents()) {
            if (child instanceof Container c) {
                invalidateTree(c);
            } else {
                child.invalidate();
            }
        }
    }

    private boolean checkScreens(String name, Dimension size) {
        boolean tooBig = false;
        int[][] screens = {{1366, 768}, {1920, 1080}};
        for (int[] s : screens) {
            // área útil lógica: tela física / escala, descontando ~48 px lógicos de barra de
            // tarefas
            int uw = (int) (s[0] / scale);
            int uh = (int) (s[1] / scale) - 48;
            if (size.width > uw || size.height > uh) {
                report.add(
                        "  ! maior que a área útil de %dx%d@%s (%dx%d lógicos)"
                                .formatted(s[0], s[1], scale, uw, uh));
                tooBig |= s[0] == 1366;
            }
        }
        return tooBig;
    }

    /** Aponta rótulos/botões menores que o preferido e irmãos sobrepostos. */
    private void inspect(String name, Container container) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof JLabel || child instanceof AbstractButton) {
                Dimension pref = child.getPreferredSize();
                if (child.getWidth() + 1 < pref.width || child.getHeight() + 1 < pref.height) {
                    if (!(SwingUtilities.getAncestorOfClass(JViewport.class, child) != null
                            && child.getParent() instanceof JComponent jc
                            && jc.getClass().getName().contains("CellRendererPane"))) {
                        report.add(
                                "  ! cortado: %s \"%s\" tem %dx%d, precisa %dx%d"
                                        .formatted(
                                                child.getClass().getSimpleName(),
                                                textOf(child),
                                                child.getWidth(),
                                                child.getHeight(),
                                                pref.width,
                                                pref.height));
                    }
                }
            }
            Rectangle bounds = child.getBounds();
            if (child.getParent() != null
                    && !(child.getParent() instanceof javax.swing.JLayeredPane)) {
                Rectangle parent = new Rectangle(child.getParent().getSize());
                if (!parent.contains(bounds) && !(child.getParent() instanceof JViewport)) {
                    report.add(
                            "  ! fora do pai: %s \"%s\" %s em %s"
                                    .formatted(
                                            child.getClass().getSimpleName(),
                                            textOf(child),
                                            fmt(bounds),
                                            fmt(parent.getSize())));
                }
            }
            if (child instanceof Container c && !(child instanceof JList)) {
                inspect(name, c);
            }
        }
        Component[] kids = container.getComponents();
        for (int i = 0; i < kids.length; i++) {
            for (int j = i + 1; j < kids.length; j++) {
                if (kids[i].isVisible()
                        && kids[j].isVisible()
                        && !(container instanceof javax.swing.JLayeredPane)
                        && !(container instanceof javax.swing.JRootPane)
                        && kids[i].getBounds().intersects(kids[j].getBounds())) {
                    report.add(
                            "  ! sobreposição: \"%s\" × \"%s\""
                                    .formatted(textOf(kids[i]), textOf(kids[j])));
                }
            }
        }
    }

    private static String textOf(Component c) {
        if (c instanceof JLabel l) {
            return abbreviate(l.getText());
        }
        if (c instanceof AbstractButton b) {
            return abbreviate(b.getText());
        }
        return c.getClass().getSimpleName();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 60 ? s.substring(0, 57) + "…" : s;
    }

    private static String fmt(Dimension d) {
        return d.width + "x" + d.height;
    }

    private static String fmt(Rectangle r) {
        return r.x + "," + r.y + " " + r.width + "x" + r.height;
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return (T) c;
            }
            if (c instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
