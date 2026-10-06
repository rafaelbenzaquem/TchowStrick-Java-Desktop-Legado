package br.com.mss.tchow.app;

import br.com.mss.tchow.net.config.IdentityTarget;
import java.util.Locale;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * {@link IdentitySessionStore} por dispositivo, sobre {@link Preferences} — mesmo padrão de {@link
 * LocalAccountSessionStore} e dos demais stores locais do cliente. Um nó-filho por destino de
 * identidade ({@code host_porta_tls|plain}), para que a sessão de um serviço de identidade nunca
 * seja enviada a outro.
 */
public final class LocalIdentitySessionStore implements IdentitySessionStore {

    private static final String KEY_TOKEN = "sessionToken";
    private static final String KEY_ACCOUNT_ID = "accountId";
    private static final String KEY_EXPIRES_AT = "expiresAt";
    private static final String KEY_STATE = "state";
    private static final String KEY_NICK = "nick";
    private static final String KEY_NICK_ACCOUNT = "nickAccountId";

    private final Preferences root;
    private final String nodeName;

    public LocalIdentitySessionStore(IdentityTarget target) {
        this(
                Preferences.userNodeForPackage(LocalIdentitySessionStore.class)
                        .node("mssIdentitySession"),
                target);
    }

    /**
     * Sessão MSS do perfil local de dados {@code profile} para {@code target}: cada janela aberta
     * tem a sua, e nenhuma é rotacionada por duas janelas ao mesmo tempo (o perfil é travado).
     */
    public LocalIdentitySessionStore(DataProfile profile, IdentityTarget target) {
        this(profile.node("mssIdentitySession"), target);
    }

    LocalIdentitySessionStore(Preferences root, IdentityTarget target) {
        this.root = root;
        this.nodeName = nodeName(target);
    }

    /** Nome de nó estável e válido para {@link Preferences} (sem '/', até 80 caracteres). */
    static String nodeName(IdentityTarget target) {
        String raw =
                (target.host() + "_" + target.port() + "_" + (target.tls() ? "tls" : "plain"))
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9._-]", "_");
        return raw.length() <= Preferences.MAX_NAME_LENGTH
                ? raw
                : raw.substring(0, 60) + "_" + Integer.toHexString(raw.hashCode());
    }

    @Override
    public synchronized Optional<StoredIdentitySession> load() {
        try {
            if (!root.nodeExists(nodeName)) {
                return Optional.empty();
            }
        } catch (BackingStoreException e) {
            return Optional.empty();
        }
        Preferences node = root.node(nodeName);
        String token = node.get(KEY_TOKEN, null);
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(
                new StoredIdentitySession(
                        token,
                        node.get(KEY_ACCOUNT_ID, ""),
                        node.getLong(KEY_EXPIRES_AT, 0),
                        node.get(KEY_STATE, "PROVISIONAL")));
    }

    @Override
    public synchronized void save(StoredIdentitySession session) {
        Preferences node = root.node(nodeName);
        node.put(KEY_TOKEN, session.sessionToken());
        node.put(KEY_ACCOUNT_ID, session.accountId());
        node.putLong(KEY_EXPIRES_AT, session.expiresAtEpochSeconds());
        node.put(KEY_STATE, session.state());
        try {
            node.flush();
        } catch (BackingStoreException e) {
            // best-effort: o SO grava sozinho ao sair; não vale abortar a UI por isso
        }
    }

    @Override
    public synchronized Optional<String> nickFor(String accountId) {
        try {
            if (accountId == null || !root.nodeExists(nodeName)) {
                return Optional.empty();
            }
        } catch (BackingStoreException e) {
            return Optional.empty();
        }
        Preferences node = root.node(nodeName);
        String nick = node.get(KEY_NICK, null);
        if (nick == null || nick.isBlank() || !accountId.equals(node.get(KEY_NICK_ACCOUNT, ""))) {
            return Optional.empty();
        }
        return Optional.of(nick);
    }

    @Override
    public synchronized void rememberNick(String accountId, String nick) {
        if (accountId == null || nick == null || nick.isBlank()) {
            return;
        }
        Preferences node = root.node(nodeName);
        node.put(KEY_NICK, nick.strip());
        node.put(KEY_NICK_ACCOUNT, accountId);
        try {
            node.flush();
        } catch (BackingStoreException e) {
            // best-effort
        }
    }

    @Override
    public synchronized void clear() {
        try {
            if (root.nodeExists(nodeName)) {
                root.node(nodeName).removeNode();
                root.flush();
            }
        } catch (BackingStoreException e) {
            throw new IllegalStateException("não foi possível remover a sessão MSS local", e);
        }
    }
}
