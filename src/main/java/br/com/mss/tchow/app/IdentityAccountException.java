package br.com.mss.tchow.app;

/**
 * Falha de uma operação da conta MSS ({@link IdentityAccountGateway}). A mensagem é para o jogador,
 * em português, e nunca contém tokens ou códigos.
 */
public final class IdentityAccountException extends RuntimeException {

    /** Espelha os tipos de erro do {@code identity-client-java}. */
    public enum Kind {
        /** Nenhuma sessão guardada neste dispositivo para o destino. */
        NOT_SIGNED_IN,
        UNAUTHENTICATED,
        PERMISSION_DENIED,
        INVALID_ARGUMENT,
        FAILED_PRECONDITION,
        RESOURCE_EXHAUSTED,
        UNAVAILABLE,
        INTERNAL
    }

    private final Kind kind;

    public IdentityAccountException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public IdentityAccountException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** {@code true} se o jogador precisa entrar de novo (sem sessão ou sessão inválida). */
    public boolean requiresSignIn() {
        return kind == Kind.NOT_SIGNED_IN || kind == Kind.UNAUTHENTICATED;
    }

    /** Mensagem padrão em português para cada tipo, quando o servidor não trouxe uma melhor. */
    public static String defaultMessage(Kind kind) {
        return switch (kind) {
            case NOT_SIGNED_IN -> "Você não entrou na conta MSS neste servidor.";
            case UNAUTHENTICATED ->
                    "Sua sessão da conta MSS expirou ou foi encerrada. Entre novamente.";
            case PERMISSION_DENIED ->
                    "Conta MSS restrita: confirme seu e-mail para continuar jogando.";
            case INVALID_ARGUMENT -> "Dados inválidos. Confira o nick, o e-mail ou o código.";
            case FAILED_PRECONDITION ->
                    "Operação não permitida agora (código vencido ou já usado?). Peça um novo"
                            + " código.";
            case RESOURCE_EXHAUSTED ->
                    "Muitas tentativas ou envios. Aguarde alguns minutos e tente de novo.";
            case UNAVAILABLE ->
                    "Serviço de identidade MSS indisponível. Verifique a conexão e tente de novo.";
            case INTERNAL -> "Erro inesperado no serviço de identidade MSS.";
        };
    }
}
