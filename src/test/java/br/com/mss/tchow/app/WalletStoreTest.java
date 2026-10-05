package br.com.mss.tchow.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cada {@link PlayerId} tem carteira própria — nunca compartilhada ([E3-12]). */
class WalletStoreTest {

    private Preferences node;
    private LocalWalletStore store;

    @BeforeEach
    void freshNode() {
        node = Preferences.userRoot().node("br/com/mss/tchow/test/wallets/" + System.nanoTime());
        store = new LocalWalletStore(node);
    }

    @AfterEach
    void wipeNode() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void carteiraNovaComecaNoSaldoInicial() {
        assertEquals(UndoWallet.INITIAL_BALANCE, store.walletFor(PlayerId.newGuest()).balance());
    }

    @Test
    void saldosDeJogadoresDiferentesSaoIndependentes() {
        PlayerId ana = PlayerId.newGuest();
        PlayerId bia = PlayerId.newGuest();

        store.walletFor(ana).trySpend();
        store.walletFor(ana).trySpend();

        assertEquals(3, store.walletFor(ana).balance());
        assertEquals(5, store.walletFor(bia).balance());
    }

    @Test
    void oMesmoIdVoltaAoMesmoSaldo() {
        PlayerId ana = PlayerId.newGuest();
        store.walletFor(ana).trySpend();

        LocalWalletStore reopened = new LocalWalletStore(node);

        assertEquals(4, reopened.walletFor(ana).balance());
    }
}
