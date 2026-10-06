package br.com.mss.tchow.app;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.match.MatchId;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * {@link SessionTokenStore} por dispositivo, sobre {@link Preferences} — mesmo padrão de {@link
 * LocalProfileStore}.
 *
 * <p>Layout: um nó por {@code matchId + "|" + color}, chave {@code token}. Sem expiração/limpeza
 * nesta fatia (ADR-0013, {@code [E4c-01]}) — partidas são efêmeras; entradas órfãs não fazem mal.
 */
public final class LocalSessionTokenStore implements SessionTokenStore {

    private static final String KEY_TOKEN = "token";

    private final Preferences root;

    public LocalSessionTokenStore() {
        this(Preferences.userNodeForPackage(LocalSessionTokenStore.class).node("session-tokens"));
    }

    /**
     * Tokens de assento do perfil local de dados {@code profile}: duas janelas na mesma partida não
     * enxergam o assento uma da outra como "seu" (Retornar).
     */
    public LocalSessionTokenStore(DataProfile profile) {
        this(profile.node("session-tokens"));
    }

    LocalSessionTokenStore(Preferences root) {
        this.root = root;
    }

    @Override
    public synchronized Optional<String> find(MatchId matchId, PlayerColor color) {
        try {
            String nodeName = nodeName(matchId, color);
            if (!root.nodeExists(nodeName)) {
                return Optional.empty();
            }
            String token = root.node(nodeName).get(KEY_TOKEN, null);
            return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
        } catch (BackingStoreException e) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized void save(MatchId matchId, PlayerColor color, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        root.node(nodeName(matchId, color)).put(KEY_TOKEN, token);
        try {
            root.flush();
        } catch (BackingStoreException e) {
            // best-effort: o SO grava sozinho ao sair; não vale abortar a UI por isso
        }
    }

    private static String nodeName(MatchId matchId, PlayerColor color) {
        return matchId.value() + "|" + color.name();
    }
}
