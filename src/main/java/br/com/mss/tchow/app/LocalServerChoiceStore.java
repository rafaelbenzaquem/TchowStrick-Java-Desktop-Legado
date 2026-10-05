package br.com.mss.tchow.app;

import br.com.mss.tchow.net.config.IdentityTarget;
import br.com.mss.tchow.net.config.ServerPreset;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * {@link ServerChoiceStore} por dispositivo, sobre {@link Preferences} — mesmo padrão de {@link
 * LocalProfileStore}. Um nó só, cinco chaves {@code name}/{@code host}/{@code port}/{@code
 * tls}/{@code official} ({@code [E6-13]}); sem porta gravada (nunca trocou), {@link #lastChoice()}
 * devolve vazio. Destino de identidade MSS opcional (M1) em {@code identityHost}/{@code
 * identityPort}/{@code identityTls}.
 */
public final class LocalServerChoiceStore implements ServerChoiceStore {

    private static final String KEY_NAME = "name";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_TLS = "tls";
    private static final String KEY_OFFICIAL = "official";
    private static final String KEY_IDENTITY_HOST = "identityHost";
    private static final String KEY_IDENTITY_PORT = "identityPort";
    private static final String KEY_IDENTITY_TLS = "identityTls";

    private final Preferences node;

    public LocalServerChoiceStore() {
        this(Preferences.userNodeForPackage(LocalServerChoiceStore.class).node("serverChoice"));
    }

    LocalServerChoiceStore(Preferences node) {
        this.node = node;
    }

    @Override
    public synchronized Optional<ServerPreset> lastChoice() {
        String host = node.get(KEY_HOST, null);
        int port = node.getInt(KEY_PORT, 0);
        if (host == null || host.isBlank() || port <= 0) {
            return Optional.empty();
        }
        String name = node.get(KEY_NAME, host);
        boolean tls = node.getBoolean(KEY_TLS, false);
        boolean official = node.getBoolean(KEY_OFFICIAL, false);
        return Optional.of(new ServerPreset(name, host, port, tls, true, official, identity()));
    }

    private IdentityTarget identity() {
        String host = node.get(KEY_IDENTITY_HOST, null);
        int port = node.getInt(KEY_IDENTITY_PORT, 0);
        if (host == null || host.isBlank() || port <= 0 || port > 65535) {
            return null;
        }
        return new IdentityTarget(host, port, node.getBoolean(KEY_IDENTITY_TLS, true));
    }

    @Override
    public synchronized void remember(ServerPreset preset) {
        node.put(KEY_NAME, preset.name());
        node.put(KEY_HOST, preset.host());
        node.putInt(KEY_PORT, preset.port());
        node.putBoolean(KEY_TLS, preset.tls());
        node.putBoolean(KEY_OFFICIAL, preset.official());
        if (preset.identity() == null) {
            node.remove(KEY_IDENTITY_HOST);
            node.remove(KEY_IDENTITY_PORT);
            node.remove(KEY_IDENTITY_TLS);
        } else {
            node.put(KEY_IDENTITY_HOST, preset.identity().host());
            node.putInt(KEY_IDENTITY_PORT, preset.identity().port());
            node.putBoolean(KEY_IDENTITY_TLS, preset.identity().tls());
        }
        try {
            node.flush();
        } catch (BackingStoreException e) {
            // best-effort: o SO grava sozinho ao sair; não vale abortar a UI por isso
        }
    }
}
