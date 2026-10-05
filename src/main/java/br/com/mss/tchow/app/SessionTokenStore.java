package br.com.mss.tchow.app;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.match.MatchId;
import java.util.Optional;

/**
 * Guarda, por partida e cor, o token de sessão emitido pelo servidor (ADR-0013, {@code [E4c-01]}) —
 * apresentado numa reconexão pra retomar o assento sem depender só do {@code guest_id}. A
 * implementação atual ({@link LocalSessionTokenStore}) é local ao dispositivo; se o armazenamento
 * se perder (reinstalar, trocar de máquina), a reconexão àquela cor específica não é mais possível
 * — mesma recusa clara de "cor pertence a outro jogador".
 */
public interface SessionTokenStore {

    /** O token guardado para {@code (matchId, color)}, se algum join anterior já o recebeu. */
    Optional<String> find(MatchId matchId, PlayerColor color);

    /** Guarda/substitui o token de {@code (matchId, color)}. */
    void save(MatchId matchId, PlayerColor color, String token);

    /**
     * A cor com token salvo para {@code matchId} neste dispositivo, se alguma — "este perfil já
     * jogou essa partida" pro {@code JoinDialog} oferecer <b>Retornar</b> (cor transparente, não
     * escolher de novo) em vez de <b>Entrar</b> (escolher uma cor livre). Só uma cor por {@code
     * (matchId, guest_id)} de verdade acontece na prática — este cliente tem um único {@code
     * guest_id} de rede por perfil ativo (ver {@code Main#networkGuestId()}).
     */
    default Optional<PlayerColor> knownColorFor(MatchId matchId) {
        for (PlayerColor color : PlayerColor.values()) {
            if (find(matchId, color).isPresent()) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }
}
