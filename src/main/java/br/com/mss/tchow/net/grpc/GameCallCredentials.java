package br.com.mss.tchow.net.grpc;

import br.com.mss.tchow.net.AccountCredentials;
import br.com.mss.tchow.net.CredentialException;
import io.grpc.*;
import java.util.function.Supplier;

/**
 * Reads the latest account credential and match token per call, keeping account authentication
 * separate (ADR-0013). The account credential is fetched on every call so short-lived MSS game
 * access is renewed before it expires (M1); if it cannot be obtained, the call fails locally with
 * the matching gRPC status and a Portuguese description, without reaching the server.
 */
final class GameCallCredentials implements ClientInterceptor {
    private final AccountCredentials accountCredentials;
    private final Supplier<String> matchToken;

    GameCallCredentials(String accountToken, Supplier<String> matchToken) {
        this(AccountCredentials.fixed(accountToken), matchToken);
    }

    GameCallCredentials(AccountCredentials accountCredentials, Supplier<String> matchToken) {
        this.accountCredentials = accountCredentials;
        this.matchToken = matchToken;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
        return new ClientInterceptors.CheckedForwardingClientCall<>(next.newCall(method, options)) {
            @Override
            protected void checkedStart(Listener<RespT> listener, Metadata headers)
                    throws Exception {
                String account;
                try {
                    account = accountCredentials.token();
                } catch (CredentialException e) {
                    throw toStatus(e).asException();
                }
                CallIdentity.attach(headers, account);
                String token = matchToken.get();
                if (token != null && !token.isBlank())
                    headers.put(CallIdentity.MATCH_TOKEN_HEADER, token);
                delegate().start(listener, headers);
            }
        };
    }

    static Status toStatus(CredentialException e) {
        Status status =
                switch (e.reason()) {
                    case UNAUTHENTICATED -> Status.UNAUTHENTICATED;
                    case PERMISSION_DENIED -> Status.PERMISSION_DENIED;
                    case UNAVAILABLE -> Status.UNAVAILABLE;
                };
        return status.withDescription(e.getMessage());
    }
}
