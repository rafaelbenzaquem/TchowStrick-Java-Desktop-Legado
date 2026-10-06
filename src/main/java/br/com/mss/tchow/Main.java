package br.com.mss.tchow;

import br.com.mss.tchow.app.AccountSessionStore;
import br.com.mss.tchow.app.DataProfile;
import br.com.mss.tchow.app.DataProfileException;
import br.com.mss.tchow.app.IdentityAccountException;
import br.com.mss.tchow.app.IdentityAccountGateway;
import br.com.mss.tchow.app.IdentityClientGateway;
import br.com.mss.tchow.app.IdentityGameCredentials;
import br.com.mss.tchow.app.IdentitySessionStore;
import br.com.mss.tchow.app.LocalAccountSessionStore;
import br.com.mss.tchow.app.LocalIdentitySessionStore;
import br.com.mss.tchow.app.LocalProfileStore;
import br.com.mss.tchow.app.LocalServerChoiceStore;
import br.com.mss.tchow.app.LocalSessionTokenStore;
import br.com.mss.tchow.app.LocalWalletStore;
import br.com.mss.tchow.app.MatchController;
import br.com.mss.tchow.app.MssAccountFlow;
import br.com.mss.tchow.app.PlayerProfile;
import br.com.mss.tchow.app.ProfileStore;
import br.com.mss.tchow.app.ServerChoiceStore;
import br.com.mss.tchow.app.SessionTokenStore;
import br.com.mss.tchow.app.StoredAccountSession;
import br.com.mss.tchow.app.UndoWallet;
import br.com.mss.tchow.app.WalletStore;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.domain.ai.AiLevel;
import br.com.mss.tchow.domain.history.GameReducer;
import br.com.mss.tchow.net.AccountCredentials;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.PlayerStatsDto;
import br.com.mss.tchow.net.GameTransport;
import br.com.mss.tchow.net.LocalTransport;
import br.com.mss.tchow.net.SaveMaterial;
import br.com.mss.tchow.net.TransportException;
import br.com.mss.tchow.net.config.IdentityTarget;
import br.com.mss.tchow.net.config.ServerDirectory;
import br.com.mss.tchow.net.config.ServerPreset;
import br.com.mss.tchow.net.grpc.GrpcAccountClient;
import br.com.mss.tchow.net.grpc.GrpcClientTransport;
import br.com.mss.tchow.net.grpc.GrpcDiscovery;
import br.com.mss.tchow.net.grpc.GrpcHostTransport;
import br.com.mss.tchow.net.match.MatchId;
import br.com.mss.tchow.save.SaveMeta;
import br.com.mss.tchow.save.Savegame;
import br.com.mss.tchow.save.SavegameCodec;
import br.com.mss.tchow.save.SavegameFormatException;
import br.com.mss.tchow.ui.BoardView;
import br.com.mss.tchow.ui.ChatPanel;
import br.com.mss.tchow.ui.ConfirmContactCodeDialog;
import br.com.mss.tchow.ui.CreateOfficialAccountDialog;
import br.com.mss.tchow.ui.HostDialog;
import br.com.mss.tchow.ui.JoinDialog;
import br.com.mss.tchow.ui.MssAccountDialog;
import br.com.mss.tchow.ui.MssSignInDialog;
import br.com.mss.tchow.ui.PlayersPanel;
import br.com.mss.tchow.ui.ProfileDialog;
import br.com.mss.tchow.ui.ReplayViewer;
import br.com.mss.tchow.ui.ServerPickerDialog;
import br.com.mss.tchow.ui.SplashPanel;
import br.com.mss.tchow.ui.StatsDialog;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Janela principal. Só monta a UI e liga criar partida/entrar/sair ao {@link MatchController}. */
public final class Main extends JFrame {

    private final JLabel statusLabel = new JLabel(" ");

    /**
     * Perfil local de dados desta janela (M1): sessão MSS, tokens de assento, perfis de jogador,
     * carteira e sessão oficial antiga ficam nele, travado enquanto a janela estiver aberta — duas
     * janelas nunca compartilham conta nem sessão. A escolha de servidor continua comum a todas.
     */
    private final DataProfile dataProfile;

    private final ProfileStore profileStore;
    private final WalletStore walletStore;
    private final SessionTokenStore sessionTokenStore;
    private final ServerChoiceStore serverChoiceStore = new LocalServerChoiceStore();
    private final AccountSessionStore accountSessionStore;
    private final GrpcAccountClient accountClient = new GrpcAccountClient();
    private PlayerProfile profile;

    /**
     * Conta MSS do servidor ativo (M1), criada sob demanda para o destino de identidade do {@link
     * #activeServer}; {@code null} se o servidor não usa identidade MSS.
     */
    private IdentityAccountGateway identityGateway;

    private IdentityTarget identityGatewayTarget;

    /** Sessão MSS (deste perfil local) por trás do {@link #identityGateway}. */
    private IdentitySessionStore identityStore;

    private SplashPanel splash;
    private GameTransport transport;
    private MatchController controller;

    /** Nível da IA da partida local atual ({@code null} em rede) — vai no save. */
    private AiLevel currentAiLevel;

    /**
     * {@code --embedded-server} ([E4a-02], ADR-0002): {@code true} reproduz o modo LAN de hoje
     * ("Criar partida" sobe o servidor no próprio processo, {@link GrpcHostTransport}); {@code
     * false} (padrão) faz "Criar partida" criar a partida num {@code ServerMain} externo ({@code
     * CreateMatch} remoto) e entrar nela como um cliente qualquer ({@link GrpcClientTransport}).
     */
    private final boolean embeddedServer;

    /** Opções de linha de comando resolvidas em {@link #main(String[])} ([E4.5-02], ADR-0014). */
    private final LaunchOptions launchOptions;

    /**
     * Servidor que "Criar partida"/"Entrar em partida" vão sugerir agora ([E4.5-04]/[E4.5-06],
     * ADR-0014/ADR-0017). Resolvido na hora de abrir ({@link ConnectionResolver#resolveDefault});
     * só troca quando o jogador pede, pelo {@link ServerPickerDialog} (ver {@link
     * #switchServerFlow()}). Nunca {@code null}.
     */
    private ServerPreset activeServer;

    public Main(LaunchOptions launchOptions, DataProfile dataProfile) {
        super("TchowStrick");
        this.dataProfile = dataProfile;
        this.profileStore = new LocalProfileStore(dataProfile);
        this.walletStore = new LocalWalletStore(dataProfile);
        this.sessionTokenStore = new LocalSessionTokenStore(dataProfile);
        this.accountSessionStore = new LocalAccountSessionStore(dataProfile);
        this.launchOptions = launchOptions;
        this.embeddedServer = launchOptions.embeddedServer();
        this.activeServer =
                ConnectionResolver.resolveDefault(
                        launchOptions, ServerDirectory.load(), serverChoiceStore);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.profile = profileStore.active().orElse(null);
        applyProfileToTitle();
        setJMenuBar(buildMenuBar());
        showIdle();
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        if (transport != null) {
                            transport.disconnect();
                        }
                        closeIdentityGateway();
                    }
                });
        setSize(480, 340);
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        if (options.embeddedServer() && "prod".equals(System.getenv("TCHOW_ENV"))) {
            System.err.println(
                    "--embedded-server não é permitido com TCHOW_ENV=prod"
                            + " — hospede num ServerMain de verdade.");
            System.exit(1);
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // segue com o Look and Feel padrão
        }
        DataProfile dataProfile;
        try {
            dataProfile = DataProfile.acquire(DataProfile.defaultDataDir(), options.dataProfile());
        } catch (DataProfileException e) {
            System.err.println(e.getMessage());
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(
                        null, e.getMessage(), "TchowStrick", JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> new Main(options, dataProfile).setVisible(true));
    }

    /** Abre o {@link ServerPickerDialog} (só fora de partida) e grava a escolha, se houver. */
    private void switchServerFlow() {
        if (transport != null) {
            warn("Saia da partida antes de trocar de servidor.");
            return;
        }
        ServerPreset chosen = new ServerPickerDialog(this, activeServer).showDialog();
        if (chosen != null) {
            setActiveServer(chosen);
            serverChoiceStore.remember(chosen);
        }
    }

    private void setActiveServer(ServerPreset preset) {
        this.activeServer = preset;
        if (transport == null) {
            showIdle(); // atualiza o rótulo "Servidor: X" da tela inicial
        }
    }

    /** Garante um perfil ativo antes de hospedar/entrar. {@code false} = o usuário desistiu. */
    private boolean ensureProfile() {
        if (profile == null) {
            createProfileFlow();
        }
        return profile != null;
    }

    /** Cria um perfil novo (nome escolhido pelo usuário) e o torna o ativo. */
    private void createProfileFlow() {
        String name =
                JOptionPane.showInputDialog(
                        this, "Nome do perfil:", "Criar perfil", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        setActiveProfile(profileStore.create(name.strip()));
    }

    /** Abre o seletor de perfis (só fora de partida). */
    private void switchProfileFlow() {
        if (transport != null) {
            warn("Saia da partida antes de trocar de perfil.");
            return;
        }
        if (profileStore.list().isEmpty()) {
            createProfileFlow();
            return;
        }
        PlayerProfile chosen = new ProfileDialog(this, profileStore).showDialog();
        if (chosen != null) {
            setActiveProfile(chosen);
        }
    }

    private void setActiveProfile(PlayerProfile chosen) {
        this.profile = chosen;
        applyProfileToTitle();
        if (transport == null) {
            showIdle(); // atualiza a barra de perfil da tela inicial
        }
    }

    private void applyProfileToTitle() {
        String title = profile == null ? "TchowStrick" : "TchowStrick — " + profile.displayName();
        // Perfil local de dados (M1): só aparece no título quando não é o padrão, para distinguir
        // as janelas abertas ao mesmo tempo.
        setTitle(
                dataProfile.isDefault()
                        ? title
                        : title + " [perfil local: " + dataProfile.displayName() + "]");
    }

    /**
     * Portão de conta oficial ({@code [E6-13]}, docs/PLANO.md §E6) — só se aplica ao caminho de
     * rede, fora do modo embutido, no {@link #activeServer} oficial. Só cobra a criação da conta
     * uma vez por perfil (a primeira sessão salva já basta); a validação em si do contato (dentro
     * ou fora da carência) é decidida pelo servidor no {@code Join} ({@code OfficialAccountGuard},
     * [E6-11]) — aqui a UI só reage a um eventual {@code PERMISSION_DENIED} via {@link #warn}, não
     * duplica a checagem. {@code false} = o jogador desistiu de criar a conta.
     */
    private boolean ensureOfficialAccount() {
        if (!embeddedServer && activeServer.usesMssIdentity()) {
            return ensureMssAccount();
        }
        if (embeddedServer || !activeServer.official()) {
            return true;
        }
        if (!ServerDirectory.isTrustedIdentityEndpoint(activeServer)) {
            warn("Este endereço não é um servidor de contas confiável.");
            return false;
        }
        if (currentAccountSession().isPresent()) {
            return true;
        }
        return createOfficialAccountFlow();
    }

    /** Credentials are available only to the shipped official TLS endpoint. */
    private Optional<StoredAccountSession> currentAccountSession() {
        if (profile == null
                || embeddedServer
                || activeServer.usesMssIdentity()
                || !ServerDirectory.isTrustedIdentityEndpoint(activeServer))
            return Optional.empty();
        return accountSessionStore
                .sessionFor(profile.id())
                .filter(s -> s.expiresAtEpochSeconds() > java.time.Instant.now().getEpochSecond());
    }

    private String accountToken() {
        return currentAccountSession().map(StoredAccountSession::token).orElse("");
    }

    /**
     * Identificador online do jogador. Com identidade MSS, o {@code account_id} da sessão (o
     * servidor usa o {@code account_id} como {@code guest_id} quando a conta não tem histórico
     * legado — decisão de 04/10/2026); senão a sessão oficial antiga ou o perfil local.
     */
    private String networkGuestId() {
        if (!embeddedServer && activeServer.usesMssIdentity()) {
            IdentityAccountGateway gateway = identityGateway();
            if (gateway != null) {
                Optional<IdentityAccountGateway.AccountStatus> account = gateway.currentAccount();
                if (account.isPresent()) {
                    return account.get().accountId();
                }
            }
        }
        return currentAccountSession()
                .map(StoredAccountSession::guestId)
                .orElse(profile.id().value());
    }

    /**
     * Credencial das chamadas de jogo: acesso de jogo da identidade MSS (renovado a cada chamada,
     * se preciso) quando o servidor usa identidade; senão a sessão oficial antiga ou nenhuma.
     */
    private AccountCredentials accountCredentials() {
        if (!embeddedServer && activeServer.usesMssIdentity()) {
            IdentityAccountGateway gateway = identityGateway();
            return gateway == null
                    ? AccountCredentials.none()
                    : new IdentityGameCredentials(gateway);
        }
        return AccountCredentials.fixed(accountToken());
    }

    // --- conta MSS (M1, MSSIdentity M4-04) ---------------------------------

    /** Gateway do destino de identidade do servidor ativo; troca junto com o servidor. */
    private IdentityAccountGateway identityGateway() {
        IdentityTarget target = activeServer.identity();
        if (target == null) {
            closeIdentityGateway();
            return null;
        }
        if (identityGateway != null && target.equals(identityGatewayTarget)) {
            return identityGateway;
        }
        closeIdentityGateway();
        try {
            IdentitySessionStore store = new LocalIdentitySessionStore(dataProfile, target);
            identityGateway =
                    new IdentityClientGateway(target, store, dataProfile.identityDeviceId());
            identityStore = store;
            identityGatewayTarget = target;
        } catch (IllegalArgumentException e) {
            warn(e.getMessage());
            return null;
        }
        return identityGateway;
    }

    private void closeIdentityGateway() {
        if (identityGateway != null) {
            try {
                identityGateway.close();
            } catch (RuntimeException ignored) {
                // fechar o canal é best-effort
            }
            identityGateway = null;
            identityGatewayTarget = null;
            identityStore = null;
        }
    }

    /** Conta MSS ativa neste perfil local, para a barra: nick lembrado ou "conectada". */
    private String mssAccountLabel() {
        IdentityAccountGateway gateway = identityGateway();
        if (gateway == null) {
            return "indisponível";
        }
        Optional<IdentityAccountGateway.AccountStatus> account = gateway.currentAccount();
        if (account.isEmpty()) {
            return "não conectada";
        }
        return identityStore
                .nickFor(account.get().accountId())
                .orElse("conectada (" + shortId(account.get().accountId()) + ")");
    }

    private static String shortId(String accountId) {
        return accountId == null || accountId.length() <= 8
                ? String.valueOf(accountId)
                : accountId.substring(0, 8) + "…";
    }

    /** Lembra o nick da conta para a barra; best-effort (sem rede, nada muda). */
    private void rememberMssNick(IdentityAccountGateway gateway) {
        try {
            Optional<IdentityAccountGateway.AccountStatus> account = gateway.currentAccount();
            if (account.isPresent() && identityStore != null) {
                identityStore.rememberNick(account.get().accountId(), gateway.profile().nick());
            }
        } catch (IdentityAccountException e) {
            // a barra mostra "conectada" sem o nick
        }
    }

    /** "Sair/Trocar de conta": sai só deste perfil local e entra com outra conta MSS. */
    private void switchMssAccountFlow() {
        if (transport != null) {
            warn("Saia da partida antes de trocar de conta.");
            return;
        }
        IdentityAccountGateway gateway = identityGateway();
        if (gateway == null) {
            warn("Este servidor não usa conta MSS.");
            return;
        }
        if (gateway.currentAccount().isPresent()
                && JOptionPane.showConfirmDialog(
                                this,
                                "Sair da conta MSS "
                                        + mssAccountLabel()
                                        + " nesta janela (perfil local "
                                        + dataProfile.displayName()
                                        + ") e entrar com outra?",
                                "Trocar de conta",
                                JOptionPane.YES_NO_OPTION)
                        != JOptionPane.YES_OPTION) {
            return;
        }
        if (mssFlow(gateway).switchAccount().isPresent()) {
            rememberMssNick(gateway);
        }
        showIdle();
    }

    private MssAccountFlow mssFlow(IdentityAccountGateway gateway) {
        return new MssAccountFlow(
                gateway,
                new MssAccountFlow.Prompts() {
                    @Override
                    public MssAccountFlow.SignInChoice askSignIn() {
                        MssSignInDialog.Result r =
                                MssSignInDialog.show(
                                        Main.this,
                                        activeServer.name(),
                                        profile == null ? "" : profile.displayName());
                        return r == null
                                ? null
                                : new MssAccountFlow.SignInChoice(
                                        r.newAccount(), r.nick(), r.email());
                    }

                    @Override
                    public String askEmail(String title) {
                        return JOptionPane.showInputDialog(
                                Main.this,
                                "E-mail da conta MSS:",
                                title,
                                JOptionPane.PLAIN_MESSAGE);
                    }

                    @Override
                    public String askCode(
                            String email, IdentityAccountGateway.Purpose purpose, Runnable resend) {
                        var dialog = new ConfirmContactCodeDialog(Main.this, email, resend);
                        dialog.setTitle(
                                purpose == IdentityAccountGateway.Purpose.RECOVER_ACCOUNT
                                        ? "Entrar na conta MSS"
                                        : "Confirmar e-mail");
                        ConfirmContactCodeDialog.Result result = dialog.showDialog();
                        return result == null ? null : result.code();
                    }

                    @Override
                    public boolean confirm(String message) {
                        return JOptionPane.showConfirmDialog(
                                        Main.this, message, "Conta MSS", JOptionPane.YES_NO_OPTION)
                                == JOptionPane.YES_OPTION;
                    }

                    @Override
                    public void info(String message) {
                        statusLabel.setText(message);
                        JOptionPane.showMessageDialog(
                                Main.this, message, "Conta MSS", JOptionPane.INFORMATION_MESSAGE);
                    }

                    @Override
                    public void warn(String message) {
                        Main.this.warn(message);
                    }
                });
    }

    /** Portão de rede com identidade MSS: exige sessão guardada; o resto o servidor decide. */
    private boolean ensureMssAccount() {
        IdentityAccountGateway gateway = identityGateway();
        if (gateway == null) {
            return false;
        }
        Optional<IdentityAccountGateway.AccountStatus> account = gateway.currentAccount();
        if (account.isPresent()) {
            if (account.get().state() == IdentityAccountGateway.AccountState.RESTRICTED) {
                statusLabel.setText(
                        MssAccountFlow.stateMessage(
                                IdentityAccountGateway.AccountState.RESTRICTED));
            }
            return true;
        }
        if (mssFlow(gateway).signIn().isPresent()) {
            rememberMssNick(gateway);
            if (transport == null) {
                showIdle();
            }
            return true;
        }
        warn(
                "O servidor "
                        + activeServer.name()
                        + " só aceita jogadores com conta MSS. Entre ou crie a conta em"
                        + " Jogador → Conta MSS… para jogar.");
        return false;
    }

    /** "Jogador → Conta MSS…": entrar, ou estado/perfil/sair se já entrou. */
    private void mssAccountFlow() {
        if (transport != null) {
            warn("Saia da partida antes de alterar sua conta.");
            return;
        }
        IdentityAccountGateway gateway = identityGateway();
        if (gateway == null) {
            warn("Este servidor não usa conta MSS.");
            return;
        }
        MssAccountFlow flow = mssFlow(gateway);
        Optional<IdentityAccountGateway.AccountStatus> status = flow.refreshStatus();
        if (status.isEmpty()) {
            if (flow.signIn().isPresent()) {
                rememberMssNick(gateway);
            }
            showIdle();
            return;
        }
        Optional<IdentityAccountGateway.Profile> accountProfile = flow.profile();
        accountProfile.ifPresent(
                p -> identityStore.rememberNick(status.get().accountId(), p.nick()));
        MssAccountDialog.Result result =
                new MssAccountDialog(
                                this,
                                new MssAccountDialog.View(
                                        activeServer.name()
                                                + " · perfil local "
                                                + dataProfile.displayName(),
                                        MssAccountFlow.stateMessage(status.get().state()),
                                        accountProfile
                                                .map(IdentityAccountGateway.Profile::nick)
                                                .orElse(""),
                                        accountProfile
                                                .map(IdentityAccountGateway.Profile::avatarId)
                                                .orElse(""),
                                        accountProfile
                                                .map(IdentityAccountGateway.Profile::maskedContact)
                                                .orElse(""),
                                        accountProfile
                                                .map(
                                                        IdentityAccountGateway.Profile
                                                                ::contactVerified)
                                                .orElse(false),
                                        accountProfile.isPresent()))
                        .showDialog();
        if (result == null) {
            return;
        }
        switch (result.action()) {
            case SAVE_PROFILE ->
                    flow.updateProfile(result.nick(), result.avatarId())
                            .ifPresent(
                                    p -> {
                                        identityStore.rememberNick(p.accountId(), p.nick());
                                        showIdle();
                                        statusLabel.setText("Perfil MSS salvo: " + p.nick());
                                    });
            case CONFIRM_EMAIL -> flow.confirmEmail();
            case SWITCH_ACCOUNT -> {
                if (flow.switchAccount().isPresent()) {
                    rememberMssNick(gateway);
                }
                showIdle();
            }
            case SIGN_OUT_THIS_DEVICE -> {
                flow.signOut(false);
                showIdle();
            }
            case SIGN_OUT_ALL_DEVICES -> {
                if (JOptionPane.showConfirmDialog(
                                this,
                                "Encerrar a conta MSS em todos os dispositivos?",
                                "Sair de todos",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE)
                        == JOptionPane.YES_OPTION) {
                    flow.signOut(true);
                    showIdle();
                }
            }
        }
    }

    /** Executa {@code action} com o fluxo MSS se o servidor usa identidade; {@code false} senão. */
    private boolean withMssFlow(java.util.function.Consumer<MssAccountFlow> action) {
        if (embeddedServer || !activeServer.usesMssIdentity()) {
            return false;
        }
        if (transport != null) {
            warn("Saia da partida antes de alterar sua conta.");
            return true;
        }
        IdentityAccountGateway gateway = identityGateway();
        if (gateway != null) {
            try {
                action.accept(mssFlow(gateway));
            } catch (IdentityAccountException e) {
                warn(e.getMessage());
            }
        }
        return true;
    }

    private boolean createOfficialAccountFlow() {
        GrpcAccountClient.Capabilities capabilities;
        try {
            capabilities =
                    accountClient.capabilities(
                            activeServer.host(), activeServer.port(), activeServer.tls());
        } catch (TransportException e) {
            warn(e.getMessage());
            return false;
        }
        if (!capabilities.email() && !capabilities.phone()) {
            warn("Cadastro temporariamente indisponível.");
            return false;
        }
        CreateOfficialAccountDialog.Result choice =
                new CreateOfficialAccountDialog(
                                this,
                                profile.displayName(),
                                capabilities.email(),
                                capabilities.sms(),
                                capabilities.whatsapp())
                        .showDialog();
        if (choice == null) {
            return false;
        }
        try {
            GrpcAccountClient.Registration registration =
                    accountClient.register(
                            activeServer.host(),
                            activeServer.port(),
                            activeServer.tls(),
                            choice.nick(),
                            choice.fullName(),
                            choice.isEmail(),
                            choice.contactValue(),
                            choice.channel());
            var session = registration.session();
            session.ifPresent(s -> accountSessionStore.save(profile.id(), toStoredSession(s)));
            statusLabel.setText(
                    session.isPresent()
                            ? "Conta oficial criada; perfil local preservado."
                            : "Confirme o contato para acessar sua conta.");
            runChallengeDialog(
                    choice.isEmail(),
                    choice.contactValue(),
                    session.isPresent()
                            ? GrpcAccountClient.Purpose.VERIFY_CONTACT
                            : GrpcAccountClient.Purpose.RECOVER_ACCOUNT,
                    registration.challenge(),
                    choice.channel());
        } catch (TransportException e) {
            warn(e.getMessage());
            return false;
        }
        // Não bloqueia o acesso ("acesso imediato", docs/PLANO.md §E6): cancelar aqui não desfaz a
        // conta nem impede seguir para a partida, só adia a confirmação para depois ([E6-11]).

        return currentAccountSession().isPresent();
    }

    /** Menu "Jogador → Confirmar contato…" — sob pedido, fora do fluxo de criar/entrar. */
    private void confirmContactFlow() {
        if (withMssFlow(MssAccountFlow::confirmEmail)) {
            return;
        }
        if (!ServerDirectory.isTrustedIdentityEndpoint(activeServer)) {
            warn("Selecione o servidor oficial para acessar sua conta.");
            return;
        }
        if (!ensureProfile()) {
            warn("Crie um perfil para jogar.");
            return;
        }
        ContactInput contact = askContact();
        if (contact != null) {
            runChallengeDialog(
                    contact.isEmail(),
                    contact.value(),
                    GrpcAccountClient.Purpose.VERIFY_CONTACT,
                    null,
                    contact.channel());
        }
    }

    /**
     * Menu "Jogador → Estatísticas…" ([E4d-04]) — a UI que faltava para {@code GetMyStats}
     * ([E4d-01]); o cliente Mobile/Web (Godot) já tinha o equivalente. Mesmo portão de conta do
     * {@link #onHost()}/{@link #onJoin()}: só exige conta oficial contra o servidor oficial.
     */
    private void statsFlow() {
        if (!ensureProfile()) {
            warn("Crie um perfil para jogar.");
            return;
        }
        if (!ensureOfficialAccount()) {
            return;
        }
        try {
            PlayerStatsDto stats =
                    new GrpcDiscovery(accountCredentials())
                            .getMyStats(
                                    activeServer.host(),
                                    activeServer.port(),
                                    networkGuestId(),
                                    activeServer.tls());
            new StatsDialog(this, stats).showDialog();
        } catch (TransportException e) {
            warn(e.getMessage());
        }
    }

    private record ContactInput(
            boolean isEmail, String value, GrpcAccountClient.DeliveryChannel channel) {}

    /** Pequeno formulário e-mail/telefone — usado só pelo fluxo de confirmação sob pedido. */
    private ContactInput askContact() {
        JRadioButton emailOption = new JRadioButton("E-mail", true);
        JRadioButton phoneOption = new JRadioButton("WhatsApp (E.164, ex.: +5591988887777)");
        JRadioButton smsOption = new JRadioButton("SMS (E.164, ex.: +5591988887777)");
        try {
            var caps =
                    accountClient.capabilities(
                            activeServer.host(), activeServer.port(), activeServer.tls());
            if (!caps.email() && !caps.phone()) {
                warn("Nenhum canal de contato disponível.");
                return null;
            }
            emailOption.setEnabled(caps.email());
            phoneOption.setEnabled(caps.whatsapp());
            smsOption.setEnabled(caps.sms());
            if (!caps.email()) {
                if (caps.whatsapp()) phoneOption.setSelected(true);
                else smsOption.setSelected(true);
            }
        } catch (TransportException e) {
            warn(e.getMessage());
            return null;
        }
        ButtonGroup group = new ButtonGroup();
        group.add(emailOption);
        group.add(phoneOption);
        group.add(smsOption);
        JTextField value = new JTextField(20);
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(emailOption);
        form.add(phoneOption);
        form.add(smsOption);
        form.add(new JLabel("Contato:"));
        form.add(value);
        int ok =
                JOptionPane.showConfirmDialog(
                        this, form, "Confirmar contato", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION || value.getText().isBlank()) {
            return null;
        }
        return new ContactInput(
                emailOption.isSelected(),
                value.getText().strip(),
                emailOption.isSelected()
                        ? GrpcAccountClient.DeliveryChannel.DEFAULT
                        : smsOption.isSelected()
                                ? GrpcAccountClient.DeliveryChannel.SMS
                                : GrpcAccountClient.DeliveryChannel.WHATSAPP);
    }

    /**
     * Abre o diálogo de código + fia o reenvio nele; ao confirmar, salva a sessão nova que o
     * servidor devolve (mesmo padrão de {@link #createOfficialAccountFlow}: confirmar também renova
     * a sessão de dispositivo). Cancelar não é erro — só não confirma agora.
     */
    private void accountChallengeFlow(GrpcAccountClient.Purpose purpose) {
        if (activeServer.usesMssIdentity() && !embeddedServer) {
            if (purpose == GrpcAccountClient.Purpose.RECOVER_ACCOUNT) {
                withMssFlow(MssAccountFlow::recover);
            } else {
                warn("Excluir a conta MSS ainda não está disponível no desktop.");
            }
            return;
        }
        if (transport != null) {
            warn("Saia da partida antes de alterar sua conta.");
            return;
        }
        if (!ServerDirectory.isTrustedIdentityEndpoint(activeServer)) {
            warn("Selecione o servidor oficial.");
            return;
        }
        if (!ensureProfile()) return;
        ContactInput contact = askContact();
        if (contact == null) return;
        if (purpose == GrpcAccountClient.Purpose.DELETE_ACCOUNT
                && JOptionPane.showConfirmDialog(
                                this,
                                "Excluir definitivamente a conta deste contato? As sessões serão revogadas. O perfil local será preservado.",
                                "Excluir conta",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE)
                        != JOptionPane.YES_OPTION) return;
        runChallengeDialog(contact.isEmail(), contact.value(), purpose, null, contact.channel());
    }

    private void runChallengeDialog(
            boolean email,
            String contact,
            GrpcAccountClient.Purpose purpose,
            GrpcAccountClient.ChallengeDto initial) {
        runChallengeDialog(
                email, contact, purpose, initial, GrpcAccountClient.DeliveryChannel.DEFAULT);
    }

    private void runChallengeDialog(
            boolean email,
            String contact,
            GrpcAccountClient.Purpose purpose,
            GrpcAccountClient.ChallengeDto initial,
            GrpcAccountClient.DeliveryChannel deliveryChannel) {
        var current = new java.util.concurrent.atomic.AtomicReference<>(initial);
        try {
            if (initial == null)
                current.set(
                        accountClient.requestChallenge(
                                activeServer.host(),
                                activeServer.port(),
                                activeServer.tls(),
                                email,
                                contact,
                                purpose,
                                deliveryChannel));
            var dialog =
                    new ConfirmContactCodeDialog(
                            this,
                            contact,
                            () -> {
                                try {
                                    current.set(
                                            accountClient.requestChallenge(
                                                    activeServer.host(),
                                                    activeServer.port(),
                                                    activeServer.tls(),
                                                    email,
                                                    contact,
                                                    purpose,
                                                    deliveryChannel));
                                    statusLabel.setText(
                                            "Código solicitado, sujeito aos limites de envio.");
                                } catch (TransportException e) {
                                    warn(e.getMessage());
                                }
                            });
            dialog.setTitle(
                    switch (purpose) {
                        case VERIFY_CONTACT -> "Confirmar contato";
                        case RECOVER_ACCOUNT -> "Recuperar conta";
                        case DELETE_ACCOUNT -> "Confirmar exclusão da conta";
                    });
            var result = dialog.showDialog();
            if (result == null) return;
            if (purpose == GrpcAccountClient.Purpose.DELETE_ACCOUNT
                    && JOptionPane.showConfirmDialog(
                                    this,
                                    "Confirmar a exclusão definitiva agora?",
                                    "Excluir conta",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE)
                            != JOptionPane.YES_OPTION) return;
            var session =
                    accountClient.completeChallenge(
                            activeServer.host(),
                            activeServer.port(),
                            activeServer.tls(),
                            email,
                            contact,
                            current.get(),
                            result.code());
            if (session.isPresent())
                accountSessionStore.save(profile.id(), toStoredSession(session.get()));
            else accountSessionStore.clear(profile.id());
            statusLabel.setText(
                    purpose == GrpcAccountClient.Purpose.DELETE_ACCOUNT
                            ? "Conta excluída; perfil local preservado."
                            : "Contato verificado; sessão atualizada.");
        } catch (TransportException e) {
            warn(e.getMessage());
        }
    }

    private static StoredAccountSession toStoredSession(GrpcAccountClient.AccountSessionDto dto) {
        return new StoredAccountSession(
                dto.token(), dto.accountId(), dto.guestId(), dto.expiresAtEpochSeconds());
    }

    private JMenuBar buildMenuBar() {
        JMenuItem host = new JMenuItem("Criar partida…");
        host.addActionListener(e -> onHost());
        JMenuItem join = new JMenuItem("Entrar…");
        join.addActionListener(e -> onJoin());
        JMenuItem save = new JMenuItem("Salvar partida…");
        save.addActionListener(e -> onSave());
        JMenuItem open = new JMenuItem("Abrir partida…");
        open.addActionListener(e -> onOpen());
        JMenuItem replay = new JMenuItem("Ver replay…");
        replay.addActionListener(e -> onReplay());
        JMenuItem leave = new JMenuItem("Sair da partida");
        leave.addActionListener(e -> onLeave());

        JMenu match = new JMenu("Partida");
        match.add(host);
        match.add(join);
        match.addSeparator();
        match.add(save);
        match.add(open);
        match.add(replay);
        match.addSeparator();
        match.add(leave);

        JMenuItem createProfile = new JMenuItem("Criar perfil…");
        createProfile.addActionListener(e -> createProfileFlow());
        JMenuItem switchProfile = new JMenuItem("Trocar perfil…");
        switchProfile.addActionListener(e -> switchProfileFlow());
        JMenuItem accessAccount = new JMenuItem("Criar ou acessar conta…");
        accessAccount.addActionListener(
                e -> {
                    if (transport != null) {
                        warn("Saia da partida antes de acessar outra sessão.");
                    } else if (!embeddedServer && activeServer.usesMssIdentity()) {
                        mssAccountFlow();
                    } else if (!ServerDirectory.isTrustedIdentityEndpoint(activeServer)) {
                        warn("Selecione o servidor oficial para acessar sua conta.");
                    } else if (ensureProfile()) {
                        createOfficialAccountFlow();
                    }
                });
        JMenuItem mssAccount = new JMenuItem("Conta MSS…");
        mssAccount.addActionListener(e -> mssAccountFlow());
        JMenuItem switchMss = new JMenuItem("Sair/Trocar de conta MSS…");
        switchMss.addActionListener(e -> switchMssAccountFlow());
        JMenuItem confirmContact = new JMenuItem("Confirmar contato…");
        confirmContact.addActionListener(e -> confirmContactFlow());
        JMenuItem stats = new JMenuItem("Estatísticas…");
        stats.addActionListener(e -> statsFlow());
        JMenu player = new JMenu("Jogador");
        player.add(createProfile);
        player.add(switchProfile);
        player.addSeparator();
        player.add(accessAccount);
        player.add(mssAccount);
        player.add(switchMss);
        player.add(confirmContact);
        player.add(stats);
        JMenuItem recover = new JMenuItem("Recuperar conta…");
        recover.addActionListener(
                e -> accountChallengeFlow(GrpcAccountClient.Purpose.RECOVER_ACCOUNT));
        player.add(recover);
        JMenuItem delete = new JMenuItem("Excluir conta…");
        delete.addActionListener(
                e -> accountChallengeFlow(GrpcAccountClient.Purpose.DELETE_ACCOUNT));
        player.add(delete);

        JMenuBar bar = new JMenuBar();
        bar.add(match);
        bar.add(player);
        return bar;
    }

    private void showIdle() {
        disposeSplash();
        getContentPane().removeAll();
        setLayout(new BorderLayout());
        add(buildTopBar(), BorderLayout.NORTH);
        splash = new SplashPanel();
        add(splash, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        statusLabel.setText(" ");
        revalidate();
        repaint();
    }

    /**
     * Perfil (sempre) + servidor ativo ([E4.5-04]) — este último só faz sentido fora do modo
     * embutido, onde "o servidor" é a própria máquina, não algo pra escolher.
     */
    private JPanel buildTopBar() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildProfileBar());
        if (!embeddedServer) {
            top.add(buildServerBar());
            if (activeServer.usesMssIdentity()) {
                top.add(buildMssAccountBar());
            }
        }
        return top;
    }

    /** Barra da tela inicial: mostra o perfil ativo e deixa criar/trocar. */
    private JPanel buildProfileBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.add(
                new JLabel(
                        (profile == null ? "Perfil: nenhum" : "Perfil: " + profile.displayName())
                                + (dataProfile.isDefault()
                                        ? ""
                                        : " · perfil local: " + dataProfile.displayName())));
        JButton button = new JButton(profile == null ? "Criar perfil…" : "Trocar perfil…");
        button.addActionListener(
                e -> {
                    if (profile == null) {
                        createProfileFlow();
                    } else {
                        switchProfileFlow();
                    }
                });
        bar.add(button);
        return bar;
    }

    /** Servidor que "Criar partida"/"Entrar em partida" vão sugerir agora ([E4.5-06]). */
    private JPanel buildServerBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(
                new JLabel(
                        "Servidor: "
                                + activeServer.name()
                                + (activeServer.usesMssIdentity() ? " (conta MSS)" : "")));
        JButton button = new JButton("Trocar servidor…");
        button.addActionListener(e -> switchServerFlow());
        bar.add(button);
        return bar;
    }

    /**
     * Conta MSS ativa nesta janela (M1) — servidores com identidade. Mostra o perfil local, para
     * que duas janelas abertas ao mesmo tempo fiquem distinguíveis, e permite trocar de conta.
     */
    private JPanel buildMssAccountBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        IdentityAccountGateway gateway = identityGateway();
        boolean signedIn = gateway != null && gateway.currentAccount().isPresent();
        bar.add(
                new JLabel(
                        "Conta MSS: "
                                + mssAccountLabel()
                                + " · perfil local: "
                                + dataProfile.displayName()));
        JButton button = new JButton(signedIn ? "Sair/Trocar de conta…" : "Entrar…");
        button.addActionListener(
                e -> {
                    if (signedIn) {
                        switchMssAccountFlow();
                    } else {
                        mssAccountFlow();
                    }
                });
        bar.add(button);
        return bar;
    }

    private void disposeSplash() {
        if (splash != null) {
            splash.stop();
            splash = null;
        }
    }

    private void onHost() {
        if (transport != null) {
            warn("Você já está numa partida.");
            return;
        }
        if (!ensureProfile()) {
            warn("Crie um perfil para jogar.");
            return;
        }
        HostDialog.Result choice = new HostDialog(this, profile.displayName()).showDialog();
        if (choice == null) {
            return;
        }
        if (choice.ai() == null && !ensureOfficialAccount()) {
            return;
        }
        try {
            GameTransport t =
                    choice.ai() != null
                            ? localVsAi(choice, profile.displayName())
                            : hostNetworkMatch(choice, profile);
            this.currentAiLevel = choice.ai(); // null em rede
            startMatch(t);
        } catch (TransportException e) {
            warn(e.getMessage());
        }
    }

    /**
     * "Criar partida" em rede: com {@code --embedded-server} sobe o servidor no próprio processo,
     * na porta de {@code launchOptions.embeddedPort()} (modo LAN de hoje); senão cria a partida no
     * {@link #activeServer} ({@code CreateMatch} remoto) e entra nela como um {@link
     * GrpcClientTransport} qualquer ([E4a-02]). Nenhum dos dois casos pergunta host/porta ao
     * jogador ([E4.5-07], ADR-0017) — já vêm resolvidos antes do diálogo abrir.
     */
    private GameTransport hostNetworkMatch(HostDialog.Result choice, PlayerProfile profile)
            throws TransportException {
        String nick = orDefault(choice.nick(), profile.displayName());
        if (embeddedServer) {
            return new GrpcHostTransport(
                    launchOptions.embeddedPort(),
                    choice.width(),
                    choice.height(),
                    choice.maxPlayers(),
                    choice.color(),
                    nick,
                    profile.id().value(),
                    choice.password(),
                    launchOptions.discoveryEnabled());
        }
        MatchId matchId =
                new GrpcDiscovery(accountCredentials())
                        .createMatch(
                                activeServer.host(),
                                activeServer.port(),
                                choice.width(),
                                choice.height(),
                                choice.maxPlayers(),
                                choice.password(),
                                activeServer.tls());
        // Partida recém-criada: nunca houve join nela antes, então não há token guardado ainda
        // (ADR-0013, [E4c-01]) — a busca é feita do mesmo jeito, por consistência com onJoin.
        String previousToken = sessionTokenStore.find(matchId, choice.color()).orElse("");
        return new GrpcClientTransport(
                activeServer.host(),
                activeServer.port(),
                nick,
                networkGuestId(),
                choice.color(),
                matchId,
                choice.password(),
                previousToken,
                activeServer.tls(),
                accountCredentials());
    }

    private static LocalTransport localVsAi(HostDialog.Result choice, String defaultNick) {
        AiLevel level = choice.ai();
        List<PlayerColor> roster = rosterForLocalMatch(choice.maxPlayers(), choice.color());
        List<LocalTransport.Bot> bots = new ArrayList<>();
        for (PlayerColor color : roster) {
            if (color != choice.color()) {
                bots.add(new LocalTransport.Bot(color, "IA " + level.label(), level.newStrategy()));
            }
        }
        return new LocalTransport(
                choice.width(),
                choice.height(),
                roster,
                choice.color(),
                orDefault(choice.nick(), defaultNick),
                bots,
                System.nanoTime());
    }

    /**
     * Elenco local vs-IA ([E4.5-05]): a cor do humano primeiro, depois as demais em ordem de {@link
     * PlayerColor#values()} até completar {@code maxPlayers} — os bots não se importam com qual cor
     * têm, então não há UI pra escolher a deles.
     */
    private static List<PlayerColor> rosterForLocalMatch(int maxPlayers, PlayerColor humanColor) {
        return Stream.concat(
                        Stream.of(humanColor),
                        Arrays.stream(PlayerColor.values()).filter(c -> c != humanColor))
                .limit(maxPlayers)
                .toList();
    }

    private void onJoin() {
        if (transport != null) {
            warn("Você já está numa partida.");
            return;
        }
        if (!ensureProfile()) {
            warn("Crie um perfil para jogar.");
            return;
        }
        if (!ensureOfficialAccount()) {
            return;
        }
        JoinDialog.Result choice =
                new JoinDialog(
                                this,
                                new GrpcDiscovery(),
                                sessionTokenStore,
                                profile.displayName(),
                                activeServer.host(),
                                activeServer.port(),
                                activeServer.tls())
                        .showDialog();
        if (choice == null) {
            return;
        }
        try {
            // Token guardado de uma reconexão anterior a esta mesma partida/cor (ADR-0013,
            // [E4c-01]); vazio se este perfil nunca entrou nela.
            String previousToken =
                    sessionTokenStore.find(choice.matchId(), choice.color()).orElse("");
            GrpcClientTransport t =
                    new GrpcClientTransport(
                            activeServer.host(),
                            activeServer.port(),
                            orDefault(choice.nick(), profile.displayName()),
                            networkGuestId(),
                            choice.color(),
                            choice.matchId(),
                            choice.password(),
                            previousToken,
                            activeServer.tls(),
                            accountCredentials());
            this.currentAiLevel = null; // partida em rede
            startMatch(t);
        } catch (TransportException e) {
            warn(e.getMessage());
        }
    }

    // --- salvar / abrir ([E3-09]) ---------------------------------------

    private void onSave() {
        if (transport == null) {
            warn("Nenhuma partida aberta.");
            return;
        }
        Optional<SaveMaterial> material = transport.saveMaterial();
        if (material.isEmpty()) {
            warn("Por ora só dá para salvar partidas contra a IA.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Salvar partida");
        chooser.setFileFilter(
                new FileNameExtensionFilter("Partida TchowStrick (*.tchow)", "tchow"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = withTchowExtension(chooser.getSelectedFile().toPath());
        if (Files.exists(path)
                && JOptionPane.showConfirmDialog(
                                this,
                                path.getFileName() + " já existe. Substituir?",
                                "Salvar partida",
                                JOptionPane.YES_NO_OPTION)
                        != JOptionPane.YES_OPTION) {
            return;
        }
        SaveMeta meta =
                SaveMeta.now(
                        appVersion(),
                        transport.localColor(),
                        currentAiLevel == null ? "" : currentAiLevel.name());
        Savegame savegame = Savegame.of(material.get().spec(), material.get().log(), meta);
        try {
            Files.write(path, SavegameCodec.encode(savegame));
            statusLabel.setText("Partida salva em " + path.getFileName());
        } catch (IOException e) {
            warn("Falha ao salvar: " + e.getMessage());
        }
    }

    /**
     * Seletor de arquivo {@code .tchow} + decode. {@code null} = cancelou ou inválido (com aviso).
     */
    private Savegame chooseSavegame(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(
                new FileNameExtensionFilter("Partida TchowStrick (*.tchow)", "tchow"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        try {
            return SavegameCodec.decode(Files.readAllBytes(chooser.getSelectedFile().toPath()));
        } catch (IOException e) {
            warn("Não foi possível ler o arquivo: " + e.getMessage());
            return null;
        } catch (SavegameFormatException e) {
            warn("Save inválido: " + e.getMessage());
            return null;
        }
    }

    private void onOpen() {
        if (transport != null) {
            warn("Saia da partida antes de abrir um save.");
            return;
        }
        if (!ensureProfile()) {
            warn("Crie um perfil para jogar.");
            return;
        }
        Savegame savegame = chooseSavegame("Abrir partida");
        if (savegame == null) {
            return;
        }

        List<PlayerColor> order = savegame.boardSpec().turnOrder();

        // O save diz quem era humano e o nível da IA; só pergunta o que faltar
        // (save antigo ou de partida em rede).
        PlayerColor myColor = savegame.meta().humanColor();
        if (myColor == null || !order.contains(myColor)) {
            myColor = askResumeColor(savegame, order);
        }
        if (myColor == null) {
            return;
        }
        AiLevel level = parseAiLevel(savegame.meta().aiLevel());
        if (level == null) {
            level = askAiLevel();
        }
        if (level == null) {
            return;
        }
        this.currentAiLevel = level;

        final PlayerColor humanColor = myColor;
        List<LocalTransport.Bot> bots = new ArrayList<>();
        for (PlayerColor color : order) {
            if (color != humanColor) {
                bots.add(new LocalTransport.Bot(color, "IA " + level.label(), level.newStrategy()));
            }
        }
        startMatch(
                new LocalTransport(
                        savegame.boardSpec().width(),
                        savegame.boardSpec().height(),
                        order,
                        humanColor,
                        profile.displayName(),
                        bots,
                        System.nanoTime(),
                        savegame.moveLog()));
    }

    /** {@code "EASY"}/{@code "MEDIUM"} → {@link AiLevel}; {@code null} se vazio ou desconhecido. */
    private static AiLevel parseAiLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AiLevel.valueOf(raw.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void onReplay() {
        Savegame savegame = chooseSavegame("Ver replay");
        if (savegame == null) {
            return;
        }
        try {
            GameReducer.replay(savegame.boardSpec(), savegame.moveLog()); // valida o log
        } catch (RuntimeException e) {
            warn("Não dá para reproduzir este save: " + e.getMessage());
            return;
        }
        new ReplayViewer(
                        this,
                        "Replay — %dx%d, %d jogada(s)"
                                .formatted(
                                        savegame.boardSpec().width(),
                                        savegame.boardSpec().height(),
                                        savegame.moveLog().moves().size()),
                        savegame.boardSpec(),
                        savegame.moveLog())
                .setVisible(true);
    }

    private PlayerColor askResumeColor(Savegame savegame, List<PlayerColor> order) {
        JComboBox<PlayerColor> colors = new JComboBox<>(order.toArray(new PlayerColor[0]));
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(
                new JLabel(
                        "Tabuleiro %dx%d · %d jogada(s) salvas"
                                .formatted(
                                        savegame.boardSpec().width(),
                                        savegame.boardSpec().height(),
                                        savegame.moveLog().moves().size())));
        form.add(new JLabel("Você joga com:"));
        form.add(colors);
        int ok =
                JOptionPane.showConfirmDialog(
                        this, form, "Abrir partida", JOptionPane.OK_CANCEL_OPTION);
        return ok == JOptionPane.OK_OPTION ? (PlayerColor) colors.getSelectedItem() : null;
    }

    private AiLevel askAiLevel() {
        JComboBox<AiLevel> levels = new JComboBox<>(AiLevel.values());
        levels.setSelectedItem(AiLevel.MEDIUM);
        levels.setRenderer(
                (list, value, index, selected, focus) ->
                        new JLabel(value == null ? "" : value.label()));
        int ok =
                JOptionPane.showConfirmDialog(
                        this, levels, "Nível da IA", JOptionPane.OK_CANCEL_OPTION);
        return ok == JOptionPane.OK_OPTION ? (AiLevel) levels.getSelectedItem() : null;
    }

    private static Path withTchowExtension(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".tchow")
                ? path
                : path.resolveSibling(path.getFileName() + ".tchow");
    }

    private static String appVersion() {
        String version = Main.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    private void startMatch(GameTransport newTransport) {
        GameSnapshotDto snapshot;
        try {
            snapshot = newTransport.connect();
        } catch (TransportException e) {
            newTransport.disconnect();
            warn(e.getMessage());
            return;
        }

        this.transport = newTransport;

        // Guarda o token que o servidor acabou de emitir, pra uma eventual reconexão futura a esta
        // mesma partida/cor (ADR-0013, [E4c-01]).
        if (newTransport instanceof GrpcClientTransport grpc && grpc.matchId() != null) {
            sessionTokenStore.save(grpc.matchId(), grpc.localColor(), grpc.issuedSessionToken());
        }

        BoardView boardView = new BoardView(snapshot.width(), snapshot.height());
        PlayersPanel playersPanel = new PlayersPanel();
        ChatPanel chatPanel = new ChatPanel();

        JButton rematch = new JButton("Revanche");
        rematch.setVisible(false);
        rematch.addActionListener(e -> controller.requestRematch());

        JButton undo = new JButton("Desfazer");
        JButton redo = new JButton("Refazer");
        undo.setVisible(newTransport.supportsUndo());
        redo.setVisible(newTransport.supportsRedo());
        undo.addActionListener(e -> controller.requestUndo());
        redo.addActionListener(e -> controller.requestRedo());
        java.util.function.BiConsumer<Boolean, Boolean> onHistoryButtons =
                (canUndo, canRedo) -> {
                    undo.setEnabled(canUndo);
                    redo.setEnabled(canRedo);
                };

        // Carteira de tokens de desfazer (ADR-0008): só em rede, e sempre a do
        // perfil ativo (nunca um saldo "do dispositivo"). No modo local o desfazer
        // é grátis (limite de 1 por partida), então não há carteira nem rótulo.
        UndoWallet wallet =
                newTransport.undoUsesTokens() ? walletStore.walletFor(profile.id()) : null;
        JLabel tokens = new JLabel(" ");
        tokens.setVisible(wallet != null);
        java.util.function.BiConsumer<Integer, Integer> onUndoTokens =
                (globalBalance, availableThisMatch) ->
                        tokens.setText(
                                "🎟 %d tokens · %d nesta partida"
                                        .formatted(globalBalance, availableThisMatch));

        this.controller =
                new MatchController(
                        newTransport,
                        boardView,
                        playersPanel,
                        chatPanel,
                        statusLabel,
                        rematch::setVisible,
                        onHistoryButtons,
                        this::askRematchConsent,
                        wallet,
                        onUndoTokens,
                        snapshot);

        JPanel historyButtons = new JPanel();
        historyButtons.add(undo);
        historyButtons.add(redo);
        historyButtons.add(tokens);
        JPanel south = new JPanel(new BorderLayout(6, 0));
        south.add(historyButtons, BorderLayout.WEST);
        south.add(statusLabel, BorderLayout.CENTER);
        south.add(rematch, BorderLayout.EAST);

        disposeSplash();
        getContentPane().removeAll();
        setLayout(new BorderLayout(6, 6));
        add(playersPanel, BorderLayout.NORTH);
        add(new JScrollPane(boardView), BorderLayout.CENTER);
        add(chatPanel, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        revalidate();
        repaint();
        pack();
        setLocationRelativeTo(null);
    }

    private void onLeave() {
        if (transport == null) {
            return;
        }
        transport.disconnect();
        transport = null;
        controller = null;
        currentAiLevel = null;
        showIdle();
        setSize(480, 340);
        setLocationRelativeTo(null);
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "TchowStrick", JOptionPane.WARNING_MESSAGE);
    }

    /** Pergunta ao jogador local se aceita a revanche pedida pelo oponente (E2-07). */
    private boolean askRematchConsent(br.com.mss.tchow.domain.PlayerColor requester) {
        int answer =
                JOptionPane.showConfirmDialog(
                        this,
                        requester + " quer uma revanche. Aceitar?",
                        "Pedido de revanche",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
