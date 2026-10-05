package br.com.mss.tchow.net.grpc;

import io.grpc.*;
import java.util.function.Supplier;

/** Reads the latest match token per call, keeping account authentication separate. */
final class GameCallCredentials implements ClientInterceptor {
    private final String accountToken;
    private final Supplier<String> matchToken;

    GameCallCredentials(String accountToken, Supplier<String> matchToken) {
        this.accountToken = accountToken;
        this.matchToken = matchToken;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, options)) {
            @Override
            public void start(Listener<RespT> listener, Metadata headers) {
                CallIdentity.attach(headers, accountToken);
                String token = matchToken.get();
                if (token != null && !token.isBlank())
                    headers.put(CallIdentity.MATCH_TOKEN_HEADER, token);
                super.start(listener, headers);
            }
        };
    }
}
