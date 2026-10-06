package br.com.mss.tchow.app;

import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * {@link AccountSessionStore} por dispositivo: um nó-filho de {@link Preferences} por {@link
 * PlayerId} — mesmo padrão de {@link LocalWalletStore}.
 */
public final class LocalAccountSessionStore implements AccountSessionStore {

    private static final String KEY_TOKEN = "token";
    private static final String KEY_ACCOUNT_ID = "accountId";
    private static final String KEY_GUEST_ID = "guestId";
    private static final String KEY_EXPIRES_AT = "expiresAt";

    private final Preferences root;

    public LocalAccountSessionStore() {
        this(Preferences.userNodeForPackage(LocalAccountSessionStore.class).node("accountSession"));
    }

    /** Sessões da conta oficial antiga do perfil local de dados {@code profile}. */
    public LocalAccountSessionStore(DataProfile profile) {
        this(profile.node("accountSession"));
    }

    LocalAccountSessionStore(Preferences root) {
        this.root = root;
    }

    @Override
    public Optional<StoredAccountSession> sessionFor(PlayerId id) {
        Preferences node = root.node(id.value());
        String token = node.get(KEY_TOKEN, null);
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(
                new StoredAccountSession(
                        token,
                        node.get(KEY_ACCOUNT_ID, ""),
                        node.get(KEY_GUEST_ID, ""),
                        node.getLong(KEY_EXPIRES_AT, 0)));
    }

    @Override
    public void clear(PlayerId id) {
        try {
            root.node(id.value()).removeNode();
            root.flush();
        } catch (BackingStoreException e) {
            throw new IllegalStateException("não foi possível remover a sessão local", e);
        }
    }

    @Override
    public void save(PlayerId id, StoredAccountSession session) {
        Preferences node = root.node(id.value());
        node.put(KEY_TOKEN, session.token());
        node.put(KEY_ACCOUNT_ID, session.accountId());
        node.put(KEY_GUEST_ID, session.guestId());
        node.putLong(KEY_EXPIRES_AT, session.expiresAtEpochSeconds());
        try {
            node.flush();
        } catch (BackingStoreException e) {
            // best-effort: o SO grava sozinho ao sair; não vale abortar a UI por isso
        }
    }
}
