package br.com.mss.tchow.net.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.net.AccountCredentials;
import br.com.mss.tchow.net.CredentialException;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Credencial de conta renovável (identidade MSS, M1) nas chamadas de jogo. */
class IdentityCallCredentialsTest {

    /** Devolve um token novo a cada renovação; pode falhar com um motivo. */
    private static final class RenewingCredentials implements AccountCredentials {
        final Deque<String> tokens = new ArrayDeque<>();
        CredentialException failure;
        int renewals;

        @Override
        public String token() {
            if (failure != null) throw failure;
            return tokens.peek();
        }

        @Override
        public boolean renewAfterRejection() {
            renewals++;
            tokens.poll();
            return true;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    @Test
    void cadaChamadaLeOTokenAtual() {
        var credentials = new RenewingCredentials();
        credentials.tokens.add("acesso-1");
        credentials.tokens.add("acesso-2");
        var interceptor = new GameCallCredentials(credentials, () -> "assento");

        assertEquals("Bearer acesso-1", start(interceptor).headers.get(CallIdentity.AUTHORIZATION));
        credentials.renewAfterRejection();
        Captured second = start(interceptor);

        assertEquals("Bearer acesso-2", second.headers.get(CallIdentity.AUTHORIZATION));
        assertEquals("assento", second.headers.get(CallIdentity.MATCH_TOKEN_HEADER));
    }

    @Test
    void falhaAoObterCredencialEncerraAChamadaSemIrAoServidor() {
        var credentials = new RenewingCredentials();
        credentials.failure =
                new CredentialException(
                        CredentialException.Reason.PERMISSION_DENIED, "Conta MSS restrita.");

        Captured captured = start(new GameCallCredentials(credentials, () -> null));

        assertNull(captured.headers, "a chamada não deveria ter sido iniciada");
        assertNotNull(captured.closedWith);
        assertEquals(Status.Code.PERMISSION_DENIED, captured.closedWith.getCode());
        assertEquals("Conta MSS restrita.", captured.closedWith.getDescription());
    }

    @Test
    void motivosViramStatusGrpc() {
        assertEquals(
                Status.Code.UNAUTHENTICATED,
                GameCallCredentials.toStatus(
                                new CredentialException(
                                        CredentialException.Reason.UNAUTHENTICATED, "x"))
                        .getCode());
        assertEquals(
                Status.Code.UNAVAILABLE,
                GameCallCredentials.toStatus(
                                new CredentialException(
                                        CredentialException.Reason.UNAVAILABLE, "x"))
                        .getCode());
    }

    @Test
    void unauthenticatedGeraUmaUnicaNovaTentativa() {
        var credentials = new RenewingCredentials();
        AtomicInteger attempts = new AtomicInteger();

        String result =
                CredentialRetry.call(
                        credentials,
                        () -> {
                            if (attempts.incrementAndGet() == 1)
                                throw Status.UNAUTHENTICATED.asRuntimeException();
                            return "ok";
                        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
        assertEquals(1, credentials.renewals);
    }

    @Test
    void segundaRecusaPropagaOErro() {
        var credentials = new RenewingCredentials();
        AtomicInteger attempts = new AtomicInteger();

        StatusRuntimeException e =
                assertThrows(
                        StatusRuntimeException.class,
                        () ->
                                CredentialRetry.run(
                                        credentials,
                                        () -> {
                                            attempts.incrementAndGet();
                                            throw Status.UNAUTHENTICATED.asRuntimeException();
                                        }));

        assertEquals(Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
        assertEquals(2, attempts.get());
    }

    @Test
    void outrosErrosESemCredencialNaoRepetem() {
        var credentials = new RenewingCredentials();
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
                StatusRuntimeException.class,
                () ->
                        CredentialRetry.run(
                                credentials,
                                () -> {
                                    attempts.incrementAndGet();
                                    throw Status.PERMISSION_DENIED.asRuntimeException();
                                }));
        assertEquals(1, attempts.get());
        assertFalse(
                CredentialRetry.shouldRetry(
                        AccountCredentials.none(), Status.UNAUTHENTICATED.asRuntimeException()));
    }

    @Test
    void credencialEmTextoClaroSoParaLocalhost() {
        AccountCredentials withToken = AccountCredentials.fixed("t");

        assertNull(CredentialRetry.plaintextViolation(withToken, "localhost", false));
        assertNull(CredentialRetry.plaintextViolation(withToken, "remote.example", true));
        assertNull(
                CredentialRetry.plaintextViolation(AccountCredentials.none(), "10.0.0.1", false));
        assertTrue(
                CredentialRetry.plaintextViolation(withToken, "10.0.0.1", false).contains("TLS"));
    }

    private static final class Captured {
        Metadata headers;
        Status closedWith;
    }

    private static Captured start(GameCallCredentials interceptor) {
        Captured captured = new Captured();
        AtomicReference<Metadata> received = new AtomicReference<>();
        Channel channel =
                new Channel() {
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
                                received.set(headers);
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
        var call =
                interceptor.interceptCall(
                        br.com.mss.tchow.net.grpc.proto.GameServiceGrpc.getSendChatMethod(),
                        CallOptions.DEFAULT,
                        channel);
        call.start(
                new ClientCall.Listener<>() {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        captured.closedWith = status;
                    }
                },
                new Metadata());
        call.request(1); // após falha local, deve ser no-op
        captured.headers = received.get();
        return captured;
    }
}
