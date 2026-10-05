package br.com.mss.tchow.net.grpc;

import static org.junit.jupiter.api.Assertions.*;

import io.grpc.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GameCallCredentialsTest {
    @Test
    void sendsLoginAndLatestMatchTokenSeparately() {
        AtomicReference<String> matchToken = new AtomicReference<>("");
        var credentials = new GameCallCredentials("account-secret", matchToken::get);
        Metadata first = capture(credentials);
        assertEquals("Bearer account-secret", first.get(CallIdentity.AUTHORIZATION));
        assertNull(first.get(CallIdentity.MATCH_TOKEN_HEADER));
        matchToken.set("new-seat-token");
        Metadata next = capture(credentials);
        assertEquals("Bearer account-secret", next.get(CallIdentity.AUTHORIZATION));
        assertEquals("new-seat-token", next.get(CallIdentity.MATCH_TOKEN_HEADER));
    }

    @Test
    void lanDoesNotSendLoginCredential() {
        Metadata headers = capture(new GameCallCredentials("", () -> "lan-seat-token"));
        assertNull(headers.get(CallIdentity.AUTHORIZATION));
        assertEquals("lan-seat-token", headers.get(CallIdentity.MATCH_TOKEN_HEADER));
    }

    private Metadata capture(GameCallCredentials credentials) {
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
        credentials
                .interceptCall(
                        br.com.mss.tchow.net.grpc.proto.GameServiceGrpc.getSendChatMethod(),
                        CallOptions.DEFAULT,
                        channel)
                .start(new ClientCall.Listener<>() {}, new Metadata());
        return received.get();
    }
}
