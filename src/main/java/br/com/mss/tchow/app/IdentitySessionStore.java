package br.com.mss.tchow.app;

import java.util.Optional;

/**
 * Sessão da conta MSS guardada neste dispositivo para <b>um</b> destino de identidade (M1). O
 * adaptador {@link IdentityClientGateway} a expõe como o {@code SessionStore} do {@code
 * identity-client-java}. Implementações nunca registram o token.
 */
public interface IdentitySessionStore {

    Optional<StoredIdentitySession> load();

    void save(StoredIdentitySession session);

    void clear();

    /**
     * Sessão guardada. {@code state} é o nome do {@code AccountState} ({@code PROVISIONAL}, {@code
     * ACTIVE}, {@code RESTRICTED}).
     */
    record StoredIdentitySession(
            String sessionToken, String accountId, long expiresAtEpochSeconds, String state) {

        @Override
        public String toString() {
            // Nunca expor o token em logs/depuração.
            return "StoredIdentitySession[accountId="
                    + accountId
                    + ", expiresAt="
                    + expiresAtEpochSeconds
                    + ", state="
                    + state
                    + "]";
        }
    }
}
