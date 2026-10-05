package br.com.mss.tchow.net;

/**
 * Falha ao obter a credencial de conta antes de uma chamada de jogo ({@link AccountCredentials}). A
 * mensagem é para o jogador, em português; nunca contém tokens.
 */
public final class CredentialException extends RuntimeException {

    /** Espelha os códigos gRPC que o servidor de jogo usaria para a mesma situação. */
    public enum Reason {
        /** Sem sessão ou sessão expirada/revogada: entrar novamente. */
        UNAUTHENTICATED,
        /** Conta restrita (contato não confirmado após a carência). */
        PERMISSION_DENIED,
        /** Serviço de identidade fora do ar ou inalcançável. */
        UNAVAILABLE
    }

    private final Reason reason;

    public CredentialException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CredentialException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
