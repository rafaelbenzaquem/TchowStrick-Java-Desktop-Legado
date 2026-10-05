package br.com.mss.tchow.app;

import java.util.UUID;

/**
 * Identidade estável de um jogador. Hoje é um id de <b>convidado</b> gerado no dispositivo; quando
 * entrar login (E6), o mesmo valor é ligado a uma conta e a carteira migra uma vez (ADR-0008).
 *
 * <p>Não use o nick como identidade: nick é texto livre, não é único nem estável.
 */
public record PlayerId(String value) {

    public PlayerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PlayerId vazio");
        }
        value = value.strip();
    }

    /** Um id de convidado novo, único por chamada. */
    public static PlayerId newGuest() {
        return new PlayerId("guest-" + UUID.randomUUID());
    }
}
