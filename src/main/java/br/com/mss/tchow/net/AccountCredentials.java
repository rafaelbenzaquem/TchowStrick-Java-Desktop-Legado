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
