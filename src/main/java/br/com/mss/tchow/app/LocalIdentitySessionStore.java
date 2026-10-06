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
    private static final String KEY_CONTACT = "maskedContact";
    private static final String KEY_TARGET_HOST = "identityHost";
    private static final String KEY_TARGET_PORT = "identityPort";
    private static final String KEY_TARGET_TLS = "identityTls";

    /** Nome do nó-filho, dentro de cada perfil local, que guarda as sessões MSS. */
    static final String NODE = "mssIdentitySession";

    private static final java.util.regex.Pattern NODE_NAME =
            java.util.regex.Pattern.compile("(.+)_(\\d{1,5})_(tls|plain)");

    private final Preferences root;
    private final String nodeName;
    private final IdentityTarget target;

    public LocalIdentitySessionStore(IdentityTarget target) {
        this(Preferences.userNodeForPackage(LocalIdentitySessionStore.class).node(NODE), target);
    }

    /**
     * Sessão MSS do perfil local de dados {@code profile} para {@code target}: cada janela aberta
     * tem a sua, e nenhuma é rotacionada por duas janelas ao mesmo tempo (o perfil é travado).
     */
    public LocalIdentitySessionStore(DataProfile profile, IdentityTarget target) {
        this(profile.node(NODE), target);
    }

    LocalIdentitySessionStore(Preferences root, IdentityTarget target) {
        this.root = root;
        this.nodeName = nodeName(target);
        this.target = target;
    }

    private LocalIdentitySessionStore(Preferences root, String nodeName) {
        this.root = root;
        this.nodeName = nodeName;
        this.target = null;
    }

    /** Store de um nó já existente (listagem/remoção em "Gerenciar contas"). */
    static LocalIdentitySessionStore forNode(Preferences root, String nodeName) {
        return new LocalIdentitySessionStore(root, nodeName);
    }

    /**
     * Destino de identidade da sessão guardada: o gravado junto da sessão ou, em registros
     * anteriores, o deduzido do nome do nó ({@code host_porta_tls|plain}); vazio se nenhum.
     */
    Optional<IdentityTarget> storedTarget() {
        if (target != null) {
            return Optional.of(target);
        }
        try {
            if (root.nodeExists(nodeName)) {
                Preferences node = root.node(nodeName);
                String host = node.get(KEY_TARGET_HOST, null);
                int port = node.getInt(KEY_TARGET_PORT, 0);
                if (host != null && !host.isBlank() && port > 0 && port <= 65535) {
                    return Optional.of(
                            new IdentityTarget(host, port, node.getBoolean(KEY_TARGET_TLS, true)));
                }
            }
        } catch (BackingStoreException | IllegalArgumentException e) {
            // cai para o nome do nó
        }
        var m = NODE_NAME.matcher(nodeName);
        if (m.matches()) {
            try {
                return Optional.of(
                        new IdentityTarget(
                                m.group(1),
                                Integer.parseInt(m.group(2)),
                                m.group(3).equals("tls")));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    String nodeName() {
        return nodeName;
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
        if (target != null) {
            node.put(KEY_TARGET_HOST, target.host());
            node.putInt(KEY_TARGET_PORT, target.port());
            node.putBoolean(KEY_TARGET_TLS, target.tls());
        }
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
    public synchronized Optional<String> maskedContactFor(String accountId) {
        Optional<String> nick = nickFor(accountId);
        if (nick.isEmpty()) {
            return Optional.empty();
        }
        String contact = root.node(nodeName).get(KEY_CONTACT, null);
        return contact == null || contact.isBlank() ? Optional.empty() : Optional.of(contact);
    }

    @Override
    public synchronized void rememberProfile(String accountId, String nick, String maskedContact) {
        if (accountId == null || nick == null || nick.isBlank()) {
            return;
        }
        Preferences node = root.node(nodeName);
        node.put(KEY_NICK, nick.strip());
        node.put(KEY_NICK_ACCOUNT, accountId);
        if (maskedContact != null) {
            if (maskedContact.isBlank()) {
                node.remove(KEY_CONTACT);
            } else {
                node.put(KEY_CONTACT, maskedContact.strip());
            }
        }
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
