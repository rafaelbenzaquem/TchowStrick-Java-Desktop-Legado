package br.com.mss.tchow.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.net.config.ServerPreset;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalServerChoiceStoreTest {

    private Preferences node;
    private LocalServerChoiceStore store;

    @BeforeEach
    void freshNode() {
        node =
                Preferences.userRoot()
                        .node("br/com/mss/tchow/test/serverChoice/" + System.nanoTime());
        store = new LocalServerChoiceStore(node);
    }

    @AfterEach
    void wipeNode() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void semEscolhaNenhumaDevolveVazio() {
        assertTrue(store.lastChoice().isEmpty());
    }

    @Test
    void lembraAUltimaEscolha() {
        store.remember(new ServerPreset("Casa", "192.168.0.5", 6000, false, false, false));

        ServerPreset remembered = store.lastChoice().orElseThrow();

        assertEquals("Casa", remembered.name());
        assertEquals("192.168.0.5", remembered.host());
        assertEquals(6000, remembered.port());
        assertEquals(false, remembered.tls());
    }

    @Test
    void lembraTambemSeEraTls() {
        store.remember(
                new ServerPreset(
                        "Oficial", "tchowstrick.minashonsoftware.com.br", 443, true, false, true));

        assertTrue(store.lastChoice().orElseThrow().tls());
    }

    @Test
    void lembraTambemSeEraOficial() {
        store.remember(
                new ServerPreset(
                        "Oficial", "tchowstrick.minashonsoftware.com.br", 443, true, false, true));

        assertTrue(store.lastChoice().orElseThrow().official());
    }

    @Test
    void presetPersonalizadoNuncaEhOficial() {
        store.remember(new ServerPreset("personalizado", "192.168.0.5", 6000, false, false, false));

        assertFalse(store.lastChoice().orElseThrow().official());
    }

    @Test
    void escolherDeNovoSubstituiAAnterior() {
        store.remember(new ServerPreset("Casa", "192.168.0.5", 6000, false, false, false));
        store.remember(new ServerPreset("Oficial", "147.15.109.255", 5050, false, true, true));

        assertEquals("Oficial", store.lastChoice().orElseThrow().name());
    }

    @Test
    void sobreviveAReabrirOStore() {
        store.remember(new ServerPreset("Casa", "192.168.0.5", 6000, false, false, false));

        LocalServerChoiceStore reopened = new LocalServerChoiceStore(node);

        assertEquals("Casa", reopened.lastChoice().orElseThrow().name());
    }
}
