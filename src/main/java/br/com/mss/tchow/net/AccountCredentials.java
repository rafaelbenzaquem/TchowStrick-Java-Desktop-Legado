package br.com.mss.tchow.net;

/**
 * Fonte da credencial de conta anexada como {@code authorization: Bearer <token>} nas chamadas de
 * jogo. Consultada <b>a cada chamada</b>, para que um acesso de curta duração (identidade MSS, 10
 * min) seja renovado antes de cada RPC, inclusive no meio de uma partida longa.
 *
 * <p>Implementações: {@link #none()} (LAN, sem conta), {@link #fixed(String)} (sessão oficial
 * antiga, {@code tchowstrick.auth.v1}) e a da identidade MSS na camada {@code app}.
 */
public interface AccountCredentials {

    /** Origem da credencial, para escolher a mensagem certa quando o servidor a recusa. */
    enum Source {
        /** Sem conta (LAN ou servidor que não exige conta). */
        NONE,
        /** Sessão oficial antiga ({@code tchowstrick.auth.v1}), servidor sem identidade MSS. */
        LEGACY_SESSION,
        /** Acesso de jogo da identidade MSS. */
        MSS_IDENTITY
    }

    /**
     * Token atual; {@code ""} = chamada sem credencial de conta.
     *
     * @throws CredentialException não foi possível obter a credencial (sessão expirada, conta
     *     restrita, identidade fora do ar).
     */
    String token();

    /**
     * O servidor de jogo recusou o último token com {@code UNAUTHENTICATED}. Devolve {@code true}
     * se um novo {@link #token()} pode ser diferente e vale uma única nova tentativa.
     */
    default boolean renewAfterRejection() {
        return false;
    }

    /** {@code true} se esta fonte nunca fornece credencial (LAN/servidor sem conta). */
    boolean isEmpty();

    /** Origem da credencial; a padrão distingue só "sem conta" de sessão oficial antiga. */
    default Source source() {
        return isEmpty() ? Source.NONE : Source.LEGACY_SESSION;
    }

    /**
     * O servidor de jogo recusou a conta por contato não confirmado ({@code PERMISSION_DENIED}): a
     * fonte pode atualizar o estado guardado da conta (BUG-003). Padrão: nada a fazer.
     */
    default void accountRestricted() {}

    static AccountCredentials none() {
        return fixed("");
    }

    static AccountCredentials fixed(String token) {
        String value = token == null ? "" : token;
        return new AccountCredentials() {
            @Override
            public String token() {
                return value;
            }

            @Override
            public boolean isEmpty() {
                return value.isBlank();
            }
        };
    }
}
