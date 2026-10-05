package br.com.mss.tchow.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.identity.client.IdentityClient;
import br.com.mss.identity.client.internal.v1.AccountSession;
import br.com.mss.identity.client.internal.v1.AccountState;
import br.com.mss.identity.client.internal.v1.ChallengeInfo;
import br.com.mss.identity.client.internal.v1.GetCapabilitiesRequest;
import br.com.mss.identity.client.internal.v1.GetCapabilitiesResponse;
import br.com.mss.identity.client.internal.v1.IdentityServiceGrpc;
import br.com.mss.identity.client.internal.v1.IssueGameAccessRequest;
import br.com.mss.identity.client.internal.v1.IssueGameAccessResponse;
import br.com.mss.identity.client.internal.v1.RecoverAccountRequest;
import br.com.mss.identity.client.internal.v1.RecoverAccountResponse;
import br.com.mss.identity.client.internal.v1.RequestChallengeRequest;
import br.com.mss.identity.client.internal.v1.RequestChallengeResponse;
import br.com.mss.tchow.app.IdentityAccountGateway.Purpose;
import br.com.mss.tchow.app.IdentitySessionStore.StoredIdentitySession;
import br.com.mss.tchow.net.config.IdentityTarget;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Adaptador sobre o {@code identity-client-java}, contra um serviço falso em processo. */
class IdentityClientGatewayTest {

    /** Store em memória da porta do desktop. */
    private static final class MemoryStore implements IdentitySessionStore {
        StoredIdentitySession saved;

        @Override
        public Optional<StoredIdentitySession> load() {
            return Optional.ofNullable(saved);
        }

        @Override
        public void save(StoredIdentitySession session) {
            saved = session;
        }

        @Override
        public void clear() {
            saved = null;
        }
    }

    private final AtomicInteger accessSerial = new AtomicInteger();
    private volatile Status accessFailure;
    private Server server;
    private ManagedChannel channel;
    private final MemoryStore store = new MemoryStore();
    private IdentityClientGateway gateway;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server =
                InProcessServerBuilder.forName(name)
                        .directExecutor()
                        .addService(new FakeService())
                        .build()
                        .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        gateway =
                new IdentityClientGateway(
                        () ->
                                IdentityClient.builder()
                                        .channel(channel)
                                        .sessionStore(new IdentityClientGateway.StoreAdapter(store))
                                        .build());
    }

    @AfterEach
    void stop() {
        gateway.close();
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void capacidadesSoEmail() {
        var caps = gateway.capabilities();

        assertTrue(caps.email());
        assertFalse(caps.phone());
    }

    @Test
    void recuperarGuardaASessaoNoStoreDoDesktop() {
        var challenge = gateway.requestCode("ana@example.com", Purpose.RECOVER_ACCOUNT);
        var status = gateway.recover("ana@example.com", challenge, "123456");

        assertEquals("acc-1", status.accountId());
        assertEquals(IdentityAccountGateway.AccountState.ACTIVE, status.state());
        assertEquals("sess-1", store.saved.sessionToken());
        assertEquals("ACTIVE", store.saved.state());
        assertEquals(status, gateway.currentAccount().orElseThrow());
    }

    @Test
    void acessoDeJogoFicaEmCacheEInvalidarForcaUmNovo() {
        signedIn();

        String first = gateway.gameAccessToken();
        assertEquals(first, gateway.gameAccessToken());

        gateway.invalidateGameAccess();
        String second = gateway.gameAccessToken();

        assertNotEquals(first, second);
        assertEquals("sess-1", store.saved.sessionToken(), "sessão preservada");
    }

    @Test
    void semSessaoPedeParaEntrar() {
        var e = assertThrows(IdentityAccountException.class, () -> gateway.gameAccessToken());

        assertEquals(IdentityAccountException.Kind.NOT_SIGNED_IN, e.kind());
        assertTrue(e.requiresSignIn());
    }

    @Test
    void contaRestritaViraPermissionDeniedEmPortugues() {
        signedIn();
        accessFailure = Status.PERMISSION_DENIED.withDescription("restricted");

        var e = assertThrows(IdentityAccountException.class, () -> gateway.gameAccessToken());

        assertEquals(IdentityAccountException.Kind.PERMISSION_DENIED, e.kind());
        assertTrue(e.getMessage().contains("restrita"));
    }

    @Test
    void identidadeForaDoArViraUnavailable() {
        signedIn();
        accessFailure = Status.UNAVAILABLE;

        var e = assertThrows(IdentityAccountException.class, () -> gateway.gameAccessToken());

        assertEquals(IdentityAccountException.Kind.UNAVAILABLE, e.kind());
    }

    @Test
    void destinoRemotoEmTextoClaroERecusado() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new IdentityClientGateway(
                                new IdentityTarget("10.0.0.9", 9100, false), new MemoryStore()));
    }

    @Test
    void sessaoCorrompidaNoStoreEIgnorada() {
        store.saved = new StoredIdentitySession("t", "acc", 1L, "DESCONHECIDO");

        assertTrue(gateway.currentAccount().isEmpty());
    }

    private void signedIn() {
        store.saved =
                new StoredIdentitySession(
                        "sess-1",
                        "acc-1",
                        Instant.now().plusSeconds(30L * 24 * 3600).getEpochSecond(),
                        "ACTIVE");
    }

    private final class FakeService extends IdentityServiceGrpc.IdentityServiceImplBase {
        @Override
        public void getCapabilities(
                GetCapabilitiesRequest request, StreamObserver<GetCapabilitiesResponse> response) {
            response.onNext(GetCapabilitiesResponse.newBuilder().setEmailEnabled(true).build());
            response.onCompleted();
        }

        @Override
        public void requestChallenge(
                RequestChallengeRequest request,
                StreamObserver<RequestChallengeResponse> response) {
            response.onNext(
                    RequestChallengeResponse.newBuilder()
                            .setChallenge(
                                    ChallengeInfo.newBuilder()
                                            .setChallengeId("ch-1")
                                            .setPurpose(request.getPurpose())
                                            .setExpiresAtEpochSeconds(
                                                    Instant.now()
                                                            .plusSeconds(600)
                                                            .getEpochSecond()))
                            .build());
            response.onCompleted();
        }

        @Override
        public void recoverAccount(
                RecoverAccountRequest request, StreamObserver<RecoverAccountResponse> response) {
            if (!"123456".equals(request.getCode()) || !"ch-1".equals(request.getChallengeId())) {
                response.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                return;
            }
            response.onNext(
                    RecoverAccountResponse.newBuilder()
                            .setSession(
                                    AccountSession.newBuilder()
                                            .setSessionToken("sess-1")
                                            .setAccountId("acc-1")
                                            .setExpiresAtEpochSeconds(
                                                    Instant.now()
                                                            .plusSeconds(30L * 24 * 3600)
                                                            .getEpochSecond())
                                            .setState(AccountState.ACCOUNT_STATE_ACTIVE))
                            .build());
            response.onCompleted();
        }

        @Override
        public void issueGameAccess(
                IssueGameAccessRequest request, StreamObserver<IssueGameAccessResponse> response) {
            if (accessFailure != null) {
                response.onError(accessFailure.asRuntimeException());
                return;
            }
            response.onNext(
                    IssueGameAccessResponse.newBuilder()
                            .setAccessToken("access-" + accessSerial.incrementAndGet())
                            .setAudience(request.getAudience())
                            .setExpiresAtEpochSeconds(
                                    Instant.now().plusSeconds(600).getEpochSecond())
                            .build());
            response.onCompleted();
        }
    }
}
