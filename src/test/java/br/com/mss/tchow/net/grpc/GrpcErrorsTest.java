package br.com.mss.tchow.net.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.net.AccountCredentials;
import br.com.mss.tchow.net.AccountCredentials.Source;
import br.com.mss.tchow.net.CredentialException;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;

/** Mensagens de recusa de conta (BUG-002): cada caso com a ação certa para o jogador. */
class GrpcErrorsTest {

    private static final String FALLBACK = "não foi possível conectar ao servidor";

    private static String describe(Throwable cause, Source source) {
        return GrpcErrors.describe(true, "jogo.example", 443, cause, FALLBACK, source);
    }

    private static StatusRuntimeException server(Status status, String description) {
        return status.withDescription(description).asRuntimeException();
    }

    @Test
    void semSessaoMssPreservaAMensagemLocal() {
        // Falha local ao pedir o acesso de jogo, como o interceptor a entrega ao stub.
        var local =
                new CredentialException(
                        CredentialException.Reason.UNAUTHENTICATED,
                        "Você não entrou na conta MSS neste servidor. Entre na conta MSS (Jogador →"
                                + " Conta MSS…) para jogar.");
        String message =
                describe(
                        GameCallCredentials.toStatus(local).asRuntimeException(),
                        Source.MSS_IDENTITY);

        assertEquals(local.getMessage(), message);
        assertTrue(message.contains("Jogador → Conta MSS…"));
        assertFalse(message.contains("Criar ou acessar conta"));
    }

    @Test
    void falhaLocalAtravessaOInterceptorComACausa() {
        var credentials =
                new AccountCredentials() {
                    @Override
                    public String token() {
                        throw new CredentialException(
                                CredentialException.Reason.UNAUTHENTICATED, "mensagem local");
                    }

                    @Override
                    public boolean isEmpty() {
                        return false;
                    }
                };
        AtomicReference<Status> closed = new AtomicReference<>();
        new GameCallCredentials(credentials, () -> null)
                .interceptCall(
                        br.com.mss.tchow.net.grpc.proto.GameServiceGrpc.getSendChatMethod(),
                        CallOptions.DEFAULT,
                        unusedChannel())
                .start(
                        new ClientCall.Listener<>() {
                            @Override
                            public void onClose(Status status, Metadata trailers) {
                                closed.set(status);
                            }
                        },
                        new Metadata());

        assertEquals(
                "mensagem local", describe(closed.get().asRuntimeException(), Source.MSS_IDENTITY));
    }

    @Test
    void contaMssRecusadaPeloServidorNaoFalaEmConfirmar() {
        String message =
                describe(
                        server(Status.UNAUTHENTICATED, "entre novamente na conta"),
                        Source.MSS_IDENTITY);

        assertTrue(message.startsWith(GrpcErrors.MSS_REJECTED), message);
        assertTrue(message.contains("servidor: entre novamente na conta"), message);
        assertFalse(message.toLowerCase().contains("confirm"), message);
        assertFalse(message.contains("Criar ou acessar conta"), message);
    }

    @Test
    void contaMssRecusadaSemDescricao() {
        assertEquals(
                GrpcErrors.MSS_REJECTED,
                describe(Status.UNAUTHENTICATED.asRuntimeException(), Source.MSS_IDENTITY));
    }

    @Test
    void textoLegadoSoParaServidorSemIdentidade() {
        assertEquals(
                GrpcErrors.LEGACY_SIGN_IN,
                describe(server(Status.UNAUTHENTICATED, "sessão expirada"), Source.LEGACY_SESSION));
        assertEquals(
                GrpcErrors.LEGACY_SIGN_IN,
                describe(Status.UNAUTHENTICATED.asRuntimeException(), Source.NONE));
    }

    @Test
    void contatoNaoConfirmadoViraContaRestritaComAAcaoCerta() {
        var error = server(Status.PERMISSION_DENIED, "confirme seu contato para continuar jogando");

        assertEquals(GrpcErrors.CONTACT_RESTRICTED, describe(error, Source.MSS_IDENTITY));
        assertTrue(GrpcErrors.CONTACT_RESTRICTED.contains("Jogador → Confirmar contato…"));
        assertTrue(GrpcErrors.isContactRestriction(error));
    }

    @Test
    void outraRecusaDePermissaoPreservaADescricao() {
        var error = server(Status.PERMISSION_DENIED, "sessão não pertence ao participante");

        assertEquals("sessão não pertence ao participante", describe(error, Source.MSS_IDENTITY));
        assertFalse(GrpcErrors.isContactRestriction(error));
    }

    @Test
    void restricaoLocalNaoContaComoRecusaDoServidor() {
        var local =
                new CredentialException(
                        CredentialException.Reason.PERMISSION_DENIED, "confirme seu contato");

        assertFalse(
                GrpcErrors.isContactRestriction(
                        GameCallCredentials.toStatus(local).asRuntimeException()));
    }

    @Test
    void contaAntigaNaoAceitaIndicaContaMss() {
        var error =
                server(
                        Status.FAILED_PRECONDITION,
                        "conta antiga não é aceita neste servidor; entre com a conta MSS");

        String legacy = describe(error, Source.LEGACY_SESSION);
        assertTrue(legacy.startsWith(GrpcErrors.LEGACY_NOT_ACCEPTED), legacy);
        assertTrue(legacy.contains("Trocar servidor…"), legacy);
        assertEquals(GrpcErrors.LEGACY_NOT_ACCEPTED, describe(error, Source.MSS_IDENTITY));
    }

    @Test
    void outraPreCondicaoPreservaADescricao() {
        assertEquals(
                "partida já começou",
                describe(
                        server(Status.FAILED_PRECONDITION, "partida já começou"),
                        Source.MSS_IDENTITY));
    }

    @Test
    void identidadeIndisponivelNoServidor() {
        assertEquals(
                GrpcErrors.IDENTITY_UNAVAILABLE,
                describe(
                        server(Status.UNAVAILABLE, "identidade indisponível"),
                        Source.MSS_IDENTITY));
    }

    @Test
    void servidorForaDoArNaoViraErroDeIdentidade() {
        assertEquals(
                "io exception",
                describe(server(Status.UNAVAILABLE, "io exception"), Source.MSS_IDENTITY));
        assertEquals(FALLBACK, describe(Status.UNAVAILABLE.asRuntimeException(), Source.NONE));
    }

    @Test
    void handshakeTlsQuebradoContinuaExplicado() {
        var error =
                Status.UNAVAILABLE
                        .withCause(new SSLException("not an SSL/TLS record"))
                        .asRuntimeException();

        assertTrue(describe(error, Source.MSS_IDENTITY).contains("conexão segura (TLS)"));
    }

    @Test
    void semStatusUsaOFallback() {
        assertEquals(FALLBACK, describe(new IllegalStateException("x"), Source.NONE));
    }

    private static Channel unusedChannel() {
        return new Channel() {
            @Override
            public String authority() {
                return "test";
            }

            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions options) {
                return new ClientCall<>() {
                    @Override
                    public void start(Listener<RespT> listener, Metadata headers) {
                        throw new AssertionError("a chamada não deveria chegar ao servidor");
                    }

                    @Override
                    public void request(int count) {}

                    @Override
                    public void cancel(String message, Throwable cause) {}

                    @Override
                    public void halfClose() {}

                    @Override
                    public void sendMessage(ReqT message) {}
                };
            }
        };
    }
}
