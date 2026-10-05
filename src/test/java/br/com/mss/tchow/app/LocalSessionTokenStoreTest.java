package br.com.mss.tchow.app;

import static br.com.mss.tchow.domain.PlayerColor.BLUE;
import static br.com.mss.tchow.domain.PlayerColor.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.match.MatchId;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Token de sessão guardado por (partida, cor) em {@link Preferences} (ADR-0013, {@code [E4c-01]}).
 */
class LocalSessionTokenStoreTest {

    private Preferences node;
    private LocalSessionTokenStore store;

    @BeforeEach
    void freshNode() {
        node =
                Preferences.userRoot()
                        .node("br/com/mss/tchow/test/session-tokens/" + System.nanoTime());
        store = new LocalSessionTokenStore(node);
    }

    @AfterEach
    void wipeNode() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void ausenteDevolveVazio() {
        assertTrue(store.find(MatchId.random(), RED).isEmpty());
    }

    @Test
    void salvarEAchar() {
        MatchId matchId = MatchId.random();
        store.save(matchId, RED, "token-123");

        assertEquals("token-123", store.find(matchId, RED).orElseThrow());
    }

    @Test
    void chavesIndependentesPorPartidaECor() {
        MatchId matchA = MatchId.random();
        MatchId matchB = MatchId.random();
        store.save(matchA, RED, "token-a-red");
        store.save(matchA, BLUE, "token-a-blue");
        store.save(matchB, RED, "token-b-red");

        assertEquals("token-a-red", store.find(matchA, RED).orElseThrow());
        assertEquals("token-a-blue", store.find(matchA, BLUE).orElseThrow());
        assertEquals("token-b-red", store.find(matchB, RED).orElseThrow());
        assertTrue(store.find(matchB, BLUE).isEmpty());
    }

    @Test
    void salvarDeNovoSubstitui() {
        MatchId matchId = MatchId.random();
        store.save(matchId, RED, "token-velho");
        store.save(matchId, RED, "token-novo");

        assertEquals("token-novo", store.find(matchId, RED).orElseThrow());
    }

    @Test
    void tokenVazioOuNuloNaoEhGuardado() {
        MatchId matchId = MatchId.random();
        store.save(matchId, RED, "");
        store.save(matchId, BLUE, null);

        assertTrue(store.find(matchId, RED).isEmpty());
        assertTrue(store.find(matchId, BLUE).isEmpty());
    }

    @Test
    void persisteEntreInstancias() {
        MatchId matchId = MatchId.random();
        store.save(matchId, RED, "token-123");

        LocalSessionTokenStore reopened = new LocalSessionTokenStore(node);

        assertEquals("token-123", reopened.find(matchId, RED).orElseThrow());
    }

    /**
     * {@code knownColorFor} é quem o {@code JoinDialog} usa pra oferecer "Retornar" (cor
     * transparente) em vez de "Entrar" (escolher cor) — sem token nenhum, não há como retornar.
     */
    @Test
    void semTokenNenhumNaoTemCorConhecida() {
        assertTrue(store.knownColorFor(MatchId.random()).isEmpty());
    }

    @Test
    void achaACorComTokenSalvo() {
        MatchId matchId = MatchId.random();
        store.save(matchId, BLUE, "token-blue");

        assertEquals(PlayerColor.BLUE, store.knownColorFor(matchId).orElseThrow());
    }

    @Test
    void corConhecidaNaoVazaEntrePartidas() {
        MatchId matchA = MatchId.random();
        MatchId matchB = MatchId.random();
        store.save(matchA, RED, "token-a-red");

        assertEquals(PlayerColor.RED, store.knownColorFor(matchA).orElseThrow());
        assertTrue(store.knownColorFor(matchB).isEmpty());
    }
}
