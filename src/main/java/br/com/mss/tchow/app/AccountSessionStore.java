package br.com.mss.tchow.app;

import java.util.Optional;

/**
 * Guarda a sessão de conta oficial de cada {@link PlayerId} ({@code [E6-13]}) — mesmo padrão de
 * {@link WalletStore}: cada perfil tem a sua, nunca compartilhada. Vazio = perfil ainda sem conta
 * oficial (conta puramente local).
 */
public interface AccountSessionStore {

    Optional<StoredAccountSession> sessionFor(PlayerId id);

    void save(PlayerId id, StoredAccountSession session);

    void clear(PlayerId id);
}
