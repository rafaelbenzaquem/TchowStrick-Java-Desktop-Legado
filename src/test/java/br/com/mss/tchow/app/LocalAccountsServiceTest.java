package br.com.mss.tchow.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.app.IdentitySessionStore.StoredIdentitySession;
import br.com.mss.tchow.app.LocalAccountsService.Entry;
import br.com.mss.tchow.app.LocalAccountsService.Kind;
import br.com.mss.tchow.app.LocalAccountsService.Usage;
import br.com.mss.tchow.net.config.IdentityTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** "Gerenciar contas" (M1): listagem e remoção só dos dados locais, por tipo e perfil local. */
class LocalAccountsServiceTest {

    private static final long NOW = 1_800_000_000L;
    private static final IdentityTarget LOCAL = new IdentityTarget("localhost", 9100, false);
    private static final IdentityTarget PROD =
            new IdentityTarget("identity.minashonsoftware.com.br", 443, true);

    @TempDir Path dataDir;

    private Preferences appRoot;
    private final List<DataProfile> opened = new ArrayList<>();
    private DataProfile current;
    private LocalAccountsService service;

    @BeforeEach
    void setUp() {
        appRoot =
                Preferences.userRoot()
                        .node("br/com/mss/tchow/test/localAccounts/" + System.nanoTime());
        current = open(null); // padrão = esta janela
        service =
                new LocalAccountsService(
                        dataDir,
                        current,
                        appRoot,
                        Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    @AfterEach
    void cleanUp() throws BackingStoreException {
        opened.forEach(DataProfile::close);
        appRoot.removeNode();
    }

    private DataProfile open(String name) {
        DataProfile p = DataProfile.acquire(dataDir, name, appRoot);
        opened.add(p);
        return p;
    }

    private static StoredIdentitySession session(String token, String account, long expiresAt) {
        return new StoredIdentitySession(token, account, expiresAt, "ACTIVE");
    }

    private Entry find(Kind kind, String profile) {
        return service.list().stream()
                .filter(e -> e.kind() == kind && e.dataProfile().equals(profile))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void listaOsQuatroTiposPorPerfilLocalSemTokens() {
        var mss = new LocalIdentitySessionStore(current, PROD);
        mss.save(session("tok-secreto-mss", "11111111-aaaa-bbbb", NOW + 3600));
        mss.rememberProfile("11111111-aaaa-bbbb", "Ana", "an***@***.com");
        PlayerProfile bia = new LocalProfileStore(current).create("Bia");
        new LocalAccountSessionStore(current)
                .save(
                        bia.id(),
                        new StoredAccountSession(
                                "tok-secreto-antigo", "22222222-cccc", "guest-x", NOW - 1));
        DataProfile other = open(null); // perfil-2, aberto em "outra janela"
        new LocalIdentitySessionStore(other, LOCAL)
                .save(session("tok-secreto-2", "33333333-dddd", NOW + 10));

        List<Entry> entries = service.list();

        Entry mssEntry = find(Kind.MSS, "padrao");
        assertEquals("Ana", mssEntry.name());
        assertEquals("an***@***.com", mssEntry.account());
        assertEquals("identity.minashonsoftware.com.br:443 (TLS)", mssEntry.destination());
        assertEquals("ativa" + LocalAccountsService.LAST_KNOWN, mssEntry.state());
        assertEquals(Usage.THIS_WINDOW, mssEntry.usage());
        assertEquals("padrão", mssEntry.dataProfileLabel());

        Entry legacy = find(Kind.LEGACY_OFFICIAL, "padrao");
        assertEquals("Bia", legacy.name());
        assertEquals("expirada", legacy.state());
        assertEquals("22222222…", legacy.account());

        Entry player = find(Kind.PLAYER_PROFILE, "padrao");
        assertEquals("Bia", player.name());
        assertEquals("ativo", player.state());

        Entry otherMss = find(Kind.MSS, "perfil-2");
        assertEquals("—", otherMss.name());
        assertEquals("33333333…", otherMss.account());
        assertEquals("localhost:9100 (sem TLS)", otherMss.destination());
        assertEquals(Usage.OTHER_WINDOW, otherMss.usage());
        assertEquals(Usage.OTHER_WINDOW, find(Kind.DATA_PROFILE, "perfil-2").usage());
        assertEquals(Kind.DATA_PROFILE, entries.get(0).kind());
        assertEquals("padrao", entries.get(0).dataProfile());

        for (Entry e : entries) {
            assertFalse(e.toString().contains("tok-secreto"), e.toString());
        }
    }

    @Test
    void perfilLivreAparecePeloArquivoDeTravaENaoCriaNos() throws Exception {
        // Só o arquivo de trava (perfil já usado por uma janela, sem dados ainda).
        Files.createDirectories(dataDir.resolve("perfis"));
        Files.writeString(dataDir.resolve("perfis").resolve("perfil-3.lock"), "");

        Entry free = find(Kind.DATA_PROFILE, "perfil-3");

        assertEquals(Usage.FREE, free.usage());
        assertFalse(appRoot.nodeExists("perfis/perfil-3"));
    }

    @Test
    void registroAntigoSemDestinoGravadoUsaONomeDoNo() {
        Preferences sessions =
                appRoot.node(LocalIdentitySessionStore.NODE).node("localhost_9100_plain");
        sessions.put("sessionToken", "tok");
        sessions.put("accountId", "acc");
        sessions.putLong("expiresAt", NOW - 5);
        sessions.put("state", "PROVISIONAL");

        Entry entry = find(Kind.MSS, "padrao");

        assertEquals("localhost:9100 (sem TLS)", entry.destination());
        assertEquals("expirada", entry.state());
    }

    @Test
    void removerContaMssApagaSoASessaoLocalEOpcionalmenteSaiNoServidor() {
        var mss = new LocalIdentitySessionStore(current, PROD);
        mss.save(session("tok", "acc-1", NOW + 3600));
        List<String> remote = new ArrayList<>();
        Entry entry = find(Kind.MSS, "padrao");
        assertTrue(service.removalDescription(entry).contains("continua existindo"));

        assertTrue(
                service.remove(
                                entry,
                                (target, store, deviceId) -> {
                                    remote.add(target.authority() + "|" + deviceId);
                                    assertTrue(store.load().isPresent());
                                })
                        .isEmpty());

        assertEquals(
                List.of("identity.minashonsoftware.com.br:443|" + current.identityDeviceId()),
                remote);
        assertTrue(mss.load().isEmpty());
        assertTrue(service.list().stream().noneMatch(e -> e.kind() == Kind.MSS));
    }

    @Test
    void falhaAoSairNoServidorNaoImpedeARemocaoLocal() {
        var mss = new LocalIdentitySessionStore(current, PROD);
        mss.save(session("tok", "acc-1", NOW + 3600));

        var warning =
                service.remove(
                        find(Kind.MSS, "padrao"),
                        (t, s, d) -> {
                            throw new IllegalStateException("fora do ar");
                        });

        assertTrue(warning.orElseThrow().contains("fora do ar"));
        assertTrue(mss.load().isEmpty());
    }

    @Test
    void semSairNoServidorNaoChamaARedeEContaDeOutroPerfilLivreERemovida() {
        DataProfile free = open("livre");
        new LocalIdentitySessionStore(free, LOCAL).save(session("tok", "acc", NOW + 60));
        free.close();

        assertTrue(service.remove(find(Kind.MSS, "livre"), null).isEmpty());

        DataProfile reopened = open("livre");
        assertTrue(new LocalIdentitySessionStore(reopened, LOCAL).load().isEmpty());
    }

    @Test
    void perfilEmUsoPorOutraJanelaNaoPodeSerAlterado() {
        DataProfile other = open(null);
        new LocalIdentitySessionStore(other, LOCAL).save(session("tok", "acc", NOW + 60));
        Entry entry = find(Kind.MSS, "perfil-2");

        assertTrue(service.removalBlocker(entry).orElseThrow().contains("outra janela"));
        assertThrows(DataProfileException.class, () -> service.remove(entry, null));
        assertTrue(new LocalIdentitySessionStore(other, LOCAL).load().isPresent());
    }

    @Test
    void perfilPadraoEOPerfilDestaJanelaNaoSaoRemovidosInteiros() {
        Entry padrao = find(Kind.DATA_PROFILE, "padrao");
        assertTrue(service.removalBlocker(padrao).isPresent());
        assertThrows(DataProfileException.class, () -> service.remove(padrao, null));

        DataProfile mine = open("minha");
        var mineService = new LocalAccountsService(dataDir, mine, appRoot, Clock.systemUTC());
        Entry own =
                mineService.list().stream()
                        .filter(
                                e ->
                                        e.kind() == Kind.DATA_PROFILE
                                                && e.dataProfile().equals("minha"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(mineService.removalBlocker(own).orElseThrow().contains("desta janela"));
    }

    @Test
    void removerPerfilDeJogadorLevaCarteiraESessaoAntigaJunto() {
        LocalProfileStore players = new LocalProfileStore(current);
        PlayerProfile ana = players.create("Ana");
        new LocalWalletStore(current).walletFor(ana.id()).trySpend();
        new LocalAccountSessionStore(current)
                .save(ana.id(), new StoredAccountSession("tok", "acc", "guest", NOW + 60));
        Entry entry = find(Kind.PLAYER_PROFILE, "padrao");
        assertTrue(service.removalDescription(entry).contains("carteira"));

        service.remove(entry, null);

        assertTrue(players.list().isEmpty());
        assertTrue(players.active().isEmpty());
        assertEquals(
                UndoWallet.INITIAL_BALANCE,
                new LocalWalletStore(current).walletFor(ana.id()).balance());
        assertTrue(new LocalAccountSessionStore(current).sessionFor(ana.id()).isEmpty());
    }

    @Test
    void removerContaOficialAntigaMantemOPerfilDeJogador() {
        PlayerProfile ana = new LocalProfileStore(current).create("Ana");
        new LocalAccountSessionStore(current)
                .save(ana.id(), new StoredAccountSession("tok", "acc", "guest", NOW + 60));

        service.remove(find(Kind.LEGACY_OFFICIAL, "padrao"), null);

        assertTrue(new LocalAccountSessionStore(current).sessionFor(ana.id()).isEmpty());
        assertEquals("Ana", new LocalProfileStore(current).active().orElseThrow().displayName());
    }

    @Test
    void removerPerfilLocalLivreApagaTudoNeleEATrava() throws Exception {
        DataProfile free = open("velho");
        new LocalIdentitySessionStore(free, LOCAL).save(session("tok", "acc", NOW + 60));
        new LocalProfileStore(free).create("Caio");
        free.close();
        Entry entry = find(Kind.DATA_PROFILE, "velho");
        assertTrue(service.removalDescription(entry).contains("inteiro"));

        service.remove(entry, null);

        assertFalse(appRoot.nodeExists("perfis/velho"));
        assertFalse(Files.exists(dataDir.resolve("perfis").resolve("velho.lock")));
        assertTrue(service.list().stream().noneMatch(e -> e.dataProfile().equals("velho")));
    }
}
