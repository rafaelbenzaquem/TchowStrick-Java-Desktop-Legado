package br.com.mss.tchow.app;

/**
 * Um perfil local de jogador: a {@link PlayerId} (identidade, chaveia a carteira) mais um {@code
 * displayName} editável (o que aparece pros outros). Vários perfis podem coexistir no mesmo
 * dispositivo — cada um com sua carteira de tokens.
 */
public record PlayerProfile(PlayerId id, String displayName) {

    public PlayerProfile {
        if (id == null) {
            throw new IllegalArgumentException("perfil sem id");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("perfil sem nome");
        }
        displayName = displayName.strip();
    }
}
