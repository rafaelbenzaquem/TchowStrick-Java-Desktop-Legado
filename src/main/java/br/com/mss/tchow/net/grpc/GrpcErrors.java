package br.com.mss.tchow.net.grpc;

import io.grpc.StatusRuntimeException;
import javax.net.ssl.SSLException;

/**
 * Mensagem de erro para uma falha de conexão gRPC, compartilhada por {@link GrpcDiscovery} e {@link
 * GrpcClientTransport} (achado em 2026-09-15: um servidor sem TLS recebendo um {@code ClientHello}
 * porque "conexão segura" estava marcada dava só "não achei uma partida em host:porta" — sem pista
 * nenhuma do motivo real).
 */
final class GrpcErrors {

    private GrpcErrors() {}

    /**
     * {@code fallback} é usada quando a causa não é um handshake TLS quebrado nem carrega uma
     * descrição de {@link Status} — texto de hoje, preservado por compatibilidade.
     */
    static String describe(boolean tls, String host, int port, Throwable cause, String fallback) {
        if (cause instanceof StatusRuntimeException error
                && error.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED) {
            return "Sessão inválida ou expirada. Use Jogador → Criar ou acessar conta… para entrar novamente.";
        }
        if (tls && isTlsHandshakeFailure(cause)) {
            return "Não foi possível estabelecer conexão segura (TLS) com %s:%d — confirme que "
                            .formatted(host, port)
                    + "esse servidor realmente fala TLS, ou desmarque \"Usar conexão segura\" se "
                    + "for um servidor local ou de teste.";
        }
        if (cause instanceof StatusRuntimeException sre
                && sre.getStatus().getDescription() != null) {
            return sre.getStatus().getDescription();
        }
        return fallback;
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
