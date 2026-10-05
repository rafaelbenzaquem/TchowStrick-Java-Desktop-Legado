package br.com.mss.tchow.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Identidade local: perfis por dispositivo em {@link Preferences} ([E3-12]). */
class ProfileStoreTest {

    private Preferences node;
    private LocalProfileStore store;

    @BeforeEach
    void freshNode() {
        node = Preferences.userRoot().node("br/com/mss/tchow/test/profiles/" + System.nanoTime());
        store = new LocalProfileStore(node);
    }

    @AfterEach
    void wipeNode() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void comecaVazio() {
        assertTrue(store.list().isEmpty());
        assertTrue(store.active().isEmpty());
    }

    @Test
    void criarAdicionaEAtiva() {
        PlayerProfile ana = store.create("Ana");

        assertEquals(List.of(ana), store.list());
        assertEquals(ana, store.active().orElseThrow());
        assertTrue(ana.id().value().startsWith("guest-"));
    }

    @Test
    void cadaPerfilTemIdProprio() {
        PlayerProfile ana = store.create("Ana");
        PlayerProfile bia = store.create("Bia");

        assertNotEquals(ana.id(), bia.id());
        assertEquals(List.of(ana, bia), store.list()); // ordem de criação
        assertEquals(bia, store.active().orElseThrow()); // o último criado fica ativo
    }

    @Test
    void trocarOAtivo() {
        PlayerProfile ana = store.create("Ana");
        store.create("Bia");

        store.setActive(ana.id());

        assertEquals(ana, store.active().orElseThrow());
    }

    @Test
    void setActiveIgnoraIdDesconhecido() {
        PlayerProfile ana = store.create("Ana");

        store.setActive(PlayerId.newGuest());

        assertEquals(ana, store.active().orElseThrow());
    }

    @Test
    void persisteEntreInstancias() {
        PlayerProfile ana = store.create("Ana");
        store.create("Bia");
        store.setActive(ana.id());

        LocalProfileStore reopened = new LocalProfileStore(node);

        assertEquals(2, reopened.list().size());
        assertEquals(ana.id(), reopened.active().orElseThrow().id());
        assertEquals("Ana", reopened.active().orElseThrow().displayName());
    }
}
