package br.com.mss.tchow.net.grpc;

import br.com.mss.tchow.net.AccountCredentials;
import br.com.mss.tchow.net.config.IdentityTarget;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.function.Supplier;

/**
 * Regras comuns às chamadas autenticadas (M1): no máximo uma nova tentativa quando o servidor de
 * jogo recusa a credencial com {@code UNAUTHENTICATED} e a fonte consegue renová-la; e credencial
 * de conta só sobre TLS, salvo para a própria máquina (desenvolvimento local).
 */
final class CredentialRetry {

    private CredentialRetry() {}

    static <T> T call(AccountCredentials credentials, Supplier<T> rpc) {
        try {
            return rpc.get();
        } catch (StatusRuntimeException e) {
            if (shouldRetry(credentials, e)) {
                return rpc.get();
            }
            throw e;
        }
    }

    static void run(AccountCredentials credentials, Runnable rpc) {
        call(
                credentials,
                () -> {
                    rpc.run();
                    return null;
                });
    }

    /** {@code true} só para {@code UNAUTHENTICATED} com credencial renovável. */
    static boolean shouldRetry(AccountCredentials credentials, Throwable error) {
        return error instanceof StatusRuntimeException sre
                && sre.getStatus().getCode() == Status.Code.UNAUTHENTICATED
                && !credentials.isEmpty()
                && credentials.renewAfterRejection();
    }

    /** {@code null} se pode enviar; senão a mensagem do motivo. */
    static String plaintextViolation(AccountCredentials credentials, String host, boolean tls) {
        if (credentials.isEmpty() || tls || IdentityTarget.isLoopbackHost(host)) {
            return null;
        }
        return "conta exige conexão segura (TLS); texto claro só em localhost";
    }
}
