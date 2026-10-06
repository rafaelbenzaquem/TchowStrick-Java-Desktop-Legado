package br.com.mss.tchow.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.app.IdentitySessionStore.StoredIdentitySession;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.config.IdentityTarget;
import br.com.mss.tchow.net.match.MatchId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Perfis locais de dados (M1): várias janelas no mesmo usuário do SO, cada uma com a sua sessão MSS
 * e os seus tokens de assento, sem nunca compartilhar um perfil ao mesmo tempo.
 */
class DataProfileTest {

    private static final IdentityTarget LOCAL = new IdentityTarget("localhost", 9100, false);

    @TempDir Path dataDir;

    private Preferences appRoot;
    private final List<DataProfile> opened = new ArrayList<>();

    @BeforeEach
    void freshNode() {
        appRoot =
                Preferences.userRoot()
                        .node("br/com/mss/tchow/test/dataProfile/" + System.nanoTime());
    }

    @AfterEach
    void cleanUp() throws BackingStoreException {
        opened.forEach(DataProfile::close);
        appRoot.removeNode();
    }

    private DataProfile open(String requested) {
        DataProfile profile = DataProfile.acquire(dataDir, requested, appRoot);
        opened.add(profile);
        return profile;
    }

    @Test
    void primeiraJanelaUsaOPadraoESeguintesOProximoLivre() {
        DataProfile first = open(null);
        DataProfile second = open(null);
        DataProfile third = open(null);

        assertEquals("padrao", first.name());
        assertTrue(first.isDefault());
        assertEquals("padrão", first.displayName());
        assertEquals("perfil-2", second.name());
        assertEquals("perfil-3", third.name());
        assertFalse(second.isDefault());
        assertTrue(first.locked() && second.locked() && third.locked());
        assertTrue(Files.isRegularFile(dataDir.resolve("perfis").resolve("padrao.lock")));
    }

    @Test
    void perfilLiberadoVoltaASerUsado() {
        DataProfile first = open(null);
        open(null);
        first.close();

        assertEquals("padrao", open(null).name());
    }

    @Test
    void perfilExplicitoEmUsoERecusadoComMensagemClara() {
        open(null);

        DataProfileException e = assertThrows(DataProfileException.class, () -> open("padrão"));
        assertTrue(e.getMessage().contains("já está aberto em outra janela"), e.getMessage());
        assertEquals("teste", open("Teste").name());
        assertThrows(DataProfileException.class, () -> open("teste"));
    }

    @Test
    void nomesSaoNormalizadosEValidados() {
        assertEquals("padrao", DataProfile.normalize(" Padrão "));
        assertEquals("perfil-2", DataProfile.normalize("PERFIL-2"));
        assertEquals("ana_1", DataProfile.normalize("ana_1"));
        for (String invalid :
                new String[] {"", "  ", "../x", "a/b", "-x", "perfis", "a".repeat(33)}) {
            assertThrows(DataProfileException.class, () -> DataProfile.normalize(invalid), invalid);
        }
    }

    @Test
    void sessaoMssDeUmPerfilNaoApareceNoOutro() {
        DataProfile first = open(null);
        DataProfile second = open(null);
        new LocalIdentitySessionStore(first, LOCAL)
                .save(new StoredIdentitySession("tok-a", "conta-a", 1_900_000_000L, "ACTIVE"));

        assertTrue(new LocalIdentitySessionStore(second, LOCAL).load().isEmpty());

        new LocalIdentitySessionStore(second, LOCAL)
                .save(new StoredIdentitySession("tok-b", "conta-b", 1_900_000_000L, "ACTIVE"));

        assertEquals(
                "conta-a",
                new LocalIdentitySessionStore(first, LOCAL).load().orElseThrow().accountId());
        assertEquals(
                "conta-b",
                new LocalIdentitySessionStore(second, LOCAL).load().orElseThrow().accountId());
    }

    @Test
    void tokenDeAssentoDeUmPerfilNaoTravaACorNoOutro() {
        DataProfile first = open(null);
        DataProfile second = open(null);
        MatchId match = new MatchId(UUID.randomUUID().toString());
        new LocalSessionTokenStore(first).save(match, PlayerColor.RED, "assento-red");

        assertEquals(
                PlayerColor.RED,
                new LocalSessionTokenStore(first).knownColorFor(match).orElseThrow());
        assertTrue(new LocalSessionTokenStore(second).knownColorFor(match).isEmpty());
    }

    @Test
    void perfisDeJogadorCarteiraESessaoAntigaTambemSaoIsolados() {
        DataProfile first = open(null);
        DataProfile second = open(null);
        PlayerProfile ana = new LocalProfileStore(first).create("Ana");
        new LocalWalletStore(first).walletFor(ana.id()).trySpend();
        new LocalAccountSessionStore(first)
                .save(ana.id(), new StoredAccountSession("tok", "acc", "guest", 1L));

        assertTrue(new LocalProfileStore(second).list().isEmpty());
        assertTrue(new LocalProfileStore(second).active().isEmpty());
        assertEquals(
                UndoWallet.INITIAL_BALANCE,
                new LocalWalletStore(second).walletFor(ana.id()).balance());
        assertTrue(new LocalAccountSessionStore(second).sessionFor(ana.id()).isEmpty());
        assertEquals("Ana", new LocalProfileStore(first).active().orElseThrow().displayName());
    }

    @Test
    void perfilPadraoLeOsDadosGravadosAntesDosPerfis() {
        // Layout anterior: nós-filhos direto no nó do pacote app.
        new LocalIdentitySessionStore(appRoot.node("mssIdentitySession"), LOCAL)
                .save(new StoredIdentitySession("tok-antigo", "conta-antiga", 1L, "ACTIVE"));
        MatchId match = new MatchId(UUID.randomUUID().toString());
        new LocalSessionTokenStore(appRoot.node("session-tokens"))
                .save(match, PlayerColor.BLUE, "assento-antigo");
        PlayerProfile old = new LocalProfileStore(appRoot.node("profiles")).create("Antigo");
        appRoot.put("mssIdentityDeviceId", "device-antigo");

        DataProfile padrao = open(null);

        assertEquals(
                "conta-antiga",
                new LocalIdentitySessionStore(padrao, LOCAL).load().orElseThrow().accountId());
        assertEquals(
                "assento-antigo",
                new LocalSessionTokenStore(padrao).find(match, PlayerColor.BLUE).orElseThrow());
        assertEquals(old, new LocalProfileStore(padrao).active().orElseThrow());
        assertEquals("device-antigo", padrao.identityDeviceId());

        DataProfile segundo = open(null);
        assertTrue(new LocalIdentitySessionStore(segundo, LOCAL).load().isEmpty());
    }

    @Test
    void cadaPerfilEUmDispositivoProprioParaAIdentidade() {
        DataProfile first = open(null);
        DataProfile second = open(null);

        String deviceA = first.identityDeviceId();
        String deviceB = second.identityDeviceId();

        assertNotEquals(deviceA, deviceB);
        assertEquals(deviceA, first.identityDeviceId());
        assertEquals(deviceB, second.identityDeviceId());
    }

    @Test
    void semDiretorioDeDadosAbreOPadraoSemTrava() throws Exception {
        Path notADir = dataDir.resolve("arquivo");
        Files.writeString(notADir, "x");

        DataProfile profile = DataProfile.acquire(notADir, null, appRoot);
        opened.add(profile);

        assertEquals("padrao", profile.name());
        assertFalse(profile.locked());
    }

    @Test
    void diretorioDeDadosPadraoRespeitaAPropriedade() {
        System.setProperty(DataProfile.DATA_DIR_PROPERTY, dataDir.toString());
        try {
            assertEquals(dataDir, DataProfile.defaultDataDir());
        } finally {
            System.clearProperty(DataProfile.DATA_DIR_PROPERTY);
        }
        assertTrue(DataProfile.defaultDataDir().endsWith(".tchowstrick"));
    }

    @Test
    void janelasDemaisSaoRecusadas() {
        for (int i = 0; i < DataProfile.MAX_AUTO_PROFILES; i++) {
            open(null);
        }
        DataProfileException e = assertThrows(DataProfileException.class, () -> open(null));
        assertTrue(e.getMessage().contains("janelas"), e.getMessage());
    }
}
