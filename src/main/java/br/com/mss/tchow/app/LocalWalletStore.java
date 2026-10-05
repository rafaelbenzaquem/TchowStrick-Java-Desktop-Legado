package br.com.mss.tchow.app;

import java.util.prefs.Preferences;

/**
 * {@link WalletStore} por dispositivo: um nó-filho de {@link Preferences} por {@link PlayerId},
 * cada um servido por um {@link LocalUndoWallet}. Saldos de ids diferentes são independentes.
 */
public final class LocalWalletStore implements WalletStore {

    private final Preferences root;

    public LocalWalletStore() {
        this(Preferences.userNodeForPackage(LocalWalletStore.class).node("wallets"));
    }

    LocalWalletStore(Preferences root) {
        this.root = root;
    }

    @Override
    public UndoWallet walletFor(PlayerId id) {
        return new LocalUndoWallet(root.node(id.value()));
    }
}
