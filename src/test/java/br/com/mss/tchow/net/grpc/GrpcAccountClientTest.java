package br.com.mss.tchow.net.grpc;

import static org.junit.jupiter.api.Assertions.*;

import br.com.mss.tchow.auth.proto.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GrpcAccountClientTest {
    @Test
    void explicitDeliveryPreferenceTravelsInRegistrationAndEveryChallengePurpose()
            throws Exception {
        var seen = new java.util.concurrent.CopyOnWriteArrayList<ContactDeliveryChannel>();
        var legacy = new java.util.concurrent.atomic.AtomicBoolean(false);
        var server =
                Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                        .addService(
                                new AuthServiceGrpc.AuthServiceImplBase() {
                                    public void getAuthCapabilities(
                                            GetAuthCapabilitiesRequest req,
                                            StreamObserver<GetAuthCapabilitiesResponse> out) {
                                        var caps =
                                                GetAuthCapabilitiesResponse.newBuilder()
                                                        .setPhoneEnabled(true);
                                        if (!legacy.get())
                                            caps.setSmsEnabled(true).setWhatsappEnabled(true);
                                        reply(out, caps.build());
                                    }

                                    public void createAccount(
                                            CreateAccountRequest req,
                                            StreamObserver<CreateAccountResponse> out) {
                                        seen.add(req.getContact().getDeliveryChannel());
                                        reply(out, CreateAccountResponse.getDefaultInstance());
                                    }

                                    public void requestChallenge(
                                            RequestChallengeRequest req,
                                            StreamObserver<RequestChallengeResponse> out) {
                                        seen.add(req.getContact().getDeliveryChannel());
                                        reply(
                                                out,
                                                RequestChallengeResponse.newBuilder()
                                                        .setChallenge(
                                                                ChallengeInfo.newBuilder()
                                                                        .setPurpose(
                                                                                req.getPurpose())
                                                                        .setChallengeId("test"))
                                                        .build());
                                    }
                                })
                        .build()
                        .start();
        try {
            var client = new GrpcAccountClient("test-device");
            assertEquals(
                    new GrpcAccountClient.Capabilities(false, true, true, true),
                    client.capabilities("127.0.0.1", server.getPort(), false));
            legacy.set(true);
            assertEquals(
                    new GrpcAccountClient.Capabilities(false, true, false, true),
                    client.capabilities("127.0.0.1", server.getPort(), false));
            for (var channel :
                    java.util.List.of(
                            GrpcAccountClient.DeliveryChannel.SMS,
                            GrpcAccountClient.DeliveryChannel.WHATSAPP)) {
                client.register(
                        "127.0.0.1",
                        server.getPort(),
                        false,
                        "Owner",
                        "",
                        false,
                        "+5511999999999",
                        channel);
                for (var purpose : GrpcAccountClient.Purpose.values())
                    client.requestChallenge(
                            "127.0.0.1",
                            server.getPort(),
                            false,
                            false,
                            "+5511999999999",
                            purpose,
                            channel);
            }
            assertEquals(
                    java.util.Collections.nCopies(
                            4, ContactDeliveryChannel.CONTACT_DELIVERY_CHANNEL_SMS),
                    seen.subList(0, 4));
            assertEquals(
                    java.util.Collections.nCopies(
                            4, ContactDeliveryChannel.CONTACT_DELIVERY_CHANNEL_WHATSAPP),
                    seen.subList(4, 8));
        } finally {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void clientCarriesDeviceAndChallengeAndUsesTheDedicatedRpcForEachPurpose() throws Exception {
        var seen = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var session =
                AccountSession.newBuilder()
                        .setToken("session")
                        .setGuestId("guest")
                        .setAccountId("account")
                        .setExpiresAtEpochSeconds(999)
                        .build();
        var server =
                Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                        .addService(
                                new AuthServiceGrpc.AuthServiceImplBase() {
                                    public void getAuthCapabilities(
                                            GetAuthCapabilitiesRequest req,
                                            StreamObserver<GetAuthCapabilitiesResponse> out) {
                                        reply(
                                                out,
                                                GetAuthCapabilitiesResponse.newBuilder()
                                                        .setEmailEnabled(true)
                                                        .build());
                                    }

                                    public void requestChallenge(
                                            RequestChallengeRequest req,
                                            StreamObserver<RequestChallengeResponse> out) {
                                        seen.add(req.getDeviceId());
                                        reply(
                                                out,
                                                RequestChallengeResponse.newBuilder()
                                                        .setChallenge(
                                                                ChallengeInfo.newBuilder()
                                                                        .setChallengeId(
                                                                                "id-"
                                                                                        + req
                                                                                                .getPurpose())
                                                                        .setPurpose(
                                                                                req.getPurpose())
                                                                        .setExpiresAtEpochSeconds(
                                                                                999))
                                                        .build());
                                    }

                                    public void confirmContact(
                                            ConfirmContactRequest req,
                                            StreamObserver<ConfirmContactResponse> out) {
                                        seen.add(
                                                "verify:"
                                                        + req.getChallengeId()
                                                        + ":"
                                                        + req.getCode());
                                        reply(
                                                out,
                                                ConfirmContactResponse.newBuilder()
                                                        .setSession(session)
                                                        .build());
                                    }

                                    public void recoverAccount(
                                            RecoverAccountRequest req,
                                            StreamObserver<RecoverAccountResponse> out) {
                                        seen.add(
                                                "recover:"
                                                        + req.getChallengeId()
                                                        + ":"
                                                        + req.getCode());
                                        reply(
                                                out,
                                                RecoverAccountResponse.newBuilder()
                                                        .setSession(session)
                                                        .build());
                                    }

                                    public void deleteAccount(
                                            DeleteAccountRequest req,
                                            StreamObserver<DeleteAccountResponse> out) {
                                        seen.add(
                                                "delete:"
                                                        + req.getChallengeId()
                                                        + ":"
                                                        + req.getCode());
                                        reply(out, DeleteAccountResponse.getDefaultInstance());
                                    }
                                })
                        .build()
                        .start();
        try {
            var client = new GrpcAccountClient("stable-device");
            int port = server.getPort();
            assertEquals(
                    new GrpcAccountClient.Capabilities(true, false),
                    client.capabilities("127.0.0.1", port, false));
            for (var purpose : GrpcAccountClient.Purpose.values()) {
                var challenge =
                        client.requestChallenge(
                                "127.0.0.1", port, false, true, "a@example.com", purpose);
                assertEquals(purpose, challenge.purpose());
                var result =
                        client.completeChallenge(
                                "127.0.0.1",
                                port,
                                false,
                                true,
                                "a@example.com",
                                challenge,
                                "123456");
                assertEquals(
                        purpose != GrpcAccountClient.Purpose.DELETE_ACCOUNT, result.isPresent());
            }
            assertEquals(
                    java.util.List.of(
                            "stable-device",
                            "verify:id-AUTH_CHALLENGE_PURPOSE_VERIFY_CONTACT:123456",
                            "stable-device",
                            "recover:id-AUTH_CHALLENGE_PURPOSE_RECOVER_ACCOUNT:123456",
                            "stable-device",
                            "delete:id-AUTH_CHALLENGE_PURPOSE_DELETE_ACCOUNT:123456"),
                    seen);
        } finally {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    static <T> void reply(StreamObserver<T> out, T value) {
        out.onNext(value);
        out.onCompleted();
    }
}
