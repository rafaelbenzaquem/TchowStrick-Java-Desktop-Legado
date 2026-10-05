package br.com.mss.tchow.app;

/**
 * Devolve a {@link UndoWallet} de um jogador. Cada {@link PlayerId} tem sua própria carteira —
 * nunca compartilhada. A impl. atual ({@link LocalWalletStore}) é por dispositivo; a
 * server-authoritative por {@code userId} entra na E4/E6 (ADR-0002, ADR-0008).
 */
public interface WalletStore {

    UndoWallet walletFor(PlayerId id);
}
