package br.com.mss.tchow.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Economia de tokens de desfazer (ADR-0008), sobre a implementação local em {@link Preferences}.
 */
class UndoWalletTest {

    private Preferences node;
    private LocalUndoWallet wallet;

    @BeforeEach
    void freshNode() {
        node = Preferences.userRoot().node("br/com/mss/tchow/test/undowallet/" + System.nanoTime());
        wallet = new LocalUndoWallet(node);
    }

    @AfterEach
    void wipeNode() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void saldoInicialEhCinco() {
        assertEquals(UndoWallet.INITIAL_BALANCE, wallet.balance());
        assertEquals(5, wallet.balance());
    }

    @Test
    void gastarDebitaUmToken() {
        assertTrue(wallet.trySpend());
        assertEquals(4, wallet.balance());
    }

    @Test
    void naoGastaComSaldoZero() {
        for (int i = 0; i < 5; i++) {
            assertTrue(wallet.trySpend());
        }
        assertEquals(0, wallet.balance());
        assertFalse(wallet.trySpend());
        assertEquals(0, wallet.balance());
    }

    @Test
    void premioRespeitaOTetoDeVinte() {
        wallet.award(50);
        assertEquals(UndoWallet.MAX_BALANCE, wallet.balance());
        assertEquals(20, wallet.balance());
    }

    @Test
    void premioZeroOuNegativoNaoMudaNada() {
        wallet.award(0);
        wallet.award(-3);
        assertEquals(5, wallet.balance());
    }

    @Test
    void saldoPersisteEntreInstancias() {
        wallet.trySpend();
        wallet.trySpend();
        LocalUndoWallet reopened = new LocalUndoWallet(node);
        assertEquals(3, reopened.balance());
    }

    @Test
    void ganharDepoisDeGastarVoltaAoSaldo() {
        wallet.trySpend();
        wallet.trySpend();
        wallet.award(1);
        assertEquals(4, wallet.balance());
    }
}
