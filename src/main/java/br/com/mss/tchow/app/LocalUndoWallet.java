package br.com.mss.tchow.app;

import java.util.prefs.Preferences;

/**
 * {@link UndoWallet} persistida em um nó de {@link Preferences}. O nó é escolhido por quem constrói
 * ({@link LocalWalletStore} usa um por {@link PlayerId}) — nunca há um saldo "global do
 * dispositivo" compartilhado entre jogadores. Sem anti-fraude: o cliente controla o próprio saldo;
 * a versão confiável (servidor) é da E4/E6.
 */
public final class LocalUndoWallet implements UndoWallet {

    private static final String KEY = "undo.tokens";

    private final Preferences prefs;

    /** O nó onde este saldo vive. Normalmente vem do {@link LocalWalletStore}. */
    LocalUndoWallet(Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public synchronized int balance() {
        return clamp(prefs.getInt(KEY, INITIAL_BALANCE));
    }

    @Override
    public synchronized boolean trySpend() {
        int current = balance();
        if (current <= 0) {
            return false;
        }
        prefs.putInt(KEY, current - 1);
        return true;
    }

    @Override
    public synchronized void award(int amount) {
        if (amount <= 0) {
            return;
        }
        prefs.putInt(KEY, clamp(balance() + amount));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_BALANCE, value));
    }
}
