package br.com.mss.tchow.net.grpc;

import br.com.mss.tchow.net.AccountCredentials;
import br.com.mss.tchow.net.CredentialException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.util.Locale;
import javax.net.ssl.SSLException;

/**
 * Mensagem de erro para uma falha de conexão gRPC, compartilhada por {@link GrpcDiscovery} e {@link
 * GrpcClientTransport} (achado em 2026-09-15: um servidor sem TLS recebendo um {@code ClientHello}
 * porque "conexão segura" estava marcada dava só "não achei uma partida em host:porta" — sem pista
 * nenhuma do motivo real).
 *
 * <p>Recusas de conta (BUG-002, 06/10/2026) são explicadas conforme a origem da credencial ({@link
 * AccountCredentials.Source}): a descrição local (falha ao obter o acesso na identidade) é
 * preservada; a do servidor é traduzida por caso e, quando não há caso conhecido, devolvida como
 * veio. O texto da conta oficial antiga só aparece para servidores sem identidade MSS.
 */
public final class GrpcErrors {

    /** Servidor sem identidade MSS (fluxo {@code tchowstrick.auth.v1}) recusou a sessão. */
    static final String LEGACY_SIGN_IN =
            "Sessão inválida ou expirada. Use Jogador → Criar ou acessar conta… para entrar"
                    + " novamente.";

    /** Servidor com identidade recusou o acesso da conta MSS mesmo depois da renovação. */
    static final String MSS_REJECTED =
            "O servidor de jogo recusou o acesso da sua conta MSS, mesmo depois de renová-lo. Entre"
                    + " de novo em Jogador → Sair/Trocar de conta MSS…; se continuar, o problema"
                    + " é do servidor — tente mais tarde.";

    static final String CONTACT_RESTRICTED =
            "Conta restrita: o prazo para confirmar o contato venceu. Use Jogador → Confirmar"
                    + " contato… para voltar a jogar.";

    static final String LEGACY_NOT_ACCEPTED =
            "Este servidor não aceita mais a conta oficial antiga: entre com a conta MSS em"
                    + " Jogador → Conta MSS….";

    static final String LEGACY_NOT_ACCEPTED_SWITCH_SERVER =
            " Se o menu disser que o servidor não usa conta MSS, use Trocar servidor… e escolha"
                    + " o Oficial (conta MSS).";

    static final String IDENTITY_UNAVAILABLE =
            "O serviço de identidade MSS está indisponível para o servidor de jogo agora. Tente"
                    + " novamente mais tarde.";

    private GrpcErrors() {}

    /**
     * Como {@link #describe(boolean, String, int, Throwable, String, AccountCredentials.Source)},
     * para chamadas sem credencial de conta.
     */
    static String describe(boolean tls, String host, int port, Throwable cause, String fallback) {
        return describe(tls, host, port, cause, fallback, AccountCredentials.Source.NONE);
    }

    /**
     * {@code fallback} é usada quando a causa não é um handshake TLS quebrado, não é uma recusa de
     * conta conhecida nem carrega uma descrição de {@link Status}.
     */
    static String describe(
            boolean tls,
            String host,
            int port,
            Throwable cause,
            String fallback,
            AccountCredentials.Source source) {
        if (tls && isTlsHandshakeFailure(cause)) {
            return "Não foi possível estabelecer conexão segura (TLS) com %s:%d — confirme que "
                            .formatted(host, port)
                    + "esse servidor realmente fala TLS, ou desmarque \"Usar conexão segura\" se "
                    + "for um servidor local ou de teste.";
        }
        CredentialException local = find(cause, CredentialException.class);
        if (local != null && local.getMessage() != null && !local.getMessage().isBlank()) {
            // Falha ao obter a credencial antes de sair do cliente: a mensagem já é para o jogador.
            return local.getMessage();
        }
        Status status = statusOf(cause);
        if (status == null) {
            return fallback;
        }
        String description = status.getDescription();
        String account = accountMessage(status, source);
        if (account != null) {
            return account;
        }
        return description != null ? description : fallback;
    }

    /**
     * Mensagem para uma recusa de conta vinda do servidor; {@code null} se o status não for uma.
     */
    private static String accountMessage(Status status, AccountCredentials.Source source) {
        String description = status.getDescription();
        return switch (status.getCode()) {
            case UNAUTHENTICATED ->
                    source == AccountCredentials.Source.MSS_IDENTITY
                            ? withDetail(MSS_REJECTED, description)
                            : LEGACY_SIGN_IN;
            case PERMISSION_DENIED -> isContactRestriction(status) ? CONTACT_RESTRICTED : null;
            case FAILED_PRECONDITION ->
                    mentions(description, "conta antiga")
                            ? LEGACY_NOT_ACCEPTED
                                    + (source == AccountCredentials.Source.MSS_IDENTITY
                                            ? ""
                                            : LEGACY_NOT_ACCEPTED_SWITCH_SERVER)
                            : null;
            case UNAVAILABLE -> mentions(description, "identidade") ? IDENTITY_UNAVAILABLE : null;
            default -> null;
        };
    }

    /**
     * {@code true} se a falha é a recusa do servidor de jogo por contato não confirmado ({@code
     * PERMISSION_DENIED} "confirme seu contato…"), e não outra recusa de permissão (assento,
     * identidade divergente).
     */
    public static boolean isContactRestriction(Throwable cause) {
        Status status = statusOf(cause);
        return status != null && isContactRestriction(status);
    }

    static boolean isContactRestriction(Status status) {
        return status.getCode() == Status.Code.PERMISSION_DENIED
                && find(status.getCause(), CredentialException.class) == null
                && mentions(status.getDescription(), "contato");
    }

    private static String withDetail(String message, String description) {
        if (description == null || description.isBlank()) {
            return message;
        }
        return message + " (servidor: " + description.strip() + ")";
    }

    private static boolean mentions(String description, String term) {
        return description != null
                && description.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private static Status statusOf(Throwable cause) {
        for (Throwable c = cause; c != null; c = c.getCause()) {
            if (c instanceof StatusRuntimeException sre) {
                return sre.getStatus();
            }
            if (c instanceof StatusException se) {
                return se.getStatus();
            }
        }
        return null;
    }

    private static <T extends Throwable> T find(Throwable cause, Class<T> type) {
        for (Throwable c = cause; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        return null;
    }

    /**
     * Um {@code ClientHello} TLS contra um servidor em texto claro nunca completa o handshake — o
     * cliente vê a conexão cair no meio, sempre em algum {@link SSLException} na cadeia de causas
     * (inclusive as subclasses do Netty, como {@code NotSslRecordException}).
     */
    private static boolean isTlsHandshakeFailure(Throwable cause) {
        for (Throwable c = cause; c != null; c = c.getCause()) {
            if (c instanceof SSLException) {
                return true;
            }
        }
        return false;
    }
}
