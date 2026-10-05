package br.com.mss.tchow.net.grpc;

import br.com.mss.tchow.auth.proto.AccountSession;
import br.com.mss.tchow.auth.proto.AuthServiceGrpc;
import br.com.mss.tchow.auth.proto.ConfirmContactRequest;
import br.com.mss.tchow.auth.proto.Contact;
import br.com.mss.tchow.auth.proto.CreateAccountRequest;
import br.com.mss.tchow.auth.proto.ResendConfirmationCodeRequest;
import br.com.mss.tchow.net.TransportException;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Chamadas unárias de {@code AuthService} pro fluxo de conta oficial ({@code [E6-13]}) — mesmo
 * padrão de {@link GrpcDiscovery} (canal de vida curta por chamada, sem stream). O {@link Contact}
 * é montado aqui mesmo a partir de {@code (isEmail, valor)}: o cliente desktop não depende do
 * pacote {@code server.auth} (o {@code Contact}/{@code EmailAddress}/{@code PhoneNumber} de lá,
 * embora tecnicamente alcançáveis via {@code tchow-server} no classpath) — validação de formato é
 * responsabilidade do servidor ({@code INVALID_ARGUMENT} com mensagem clara), não duplicada aqui.
 */
public final class GrpcAccountClient {
    public enum Purpose {
        VERIFY_CONTACT,
        RECOVER_ACCOUNT,
        DELETE_ACCOUNT
    }

    public record ChallengeDto(String id, Purpose purpose, long expiresAt) {}

    public record Registration(Optional<AccountSessionDto> session, ChallengeDto challenge) {}

    public enum DeliveryChannel {
        DEFAULT,
        SMS,
        WHATSAPP
    }

    public record Capabilities(boolean email, boolean phone, boolean sms, boolean whatsapp) {
        public Capabilities(boolean email, boolean phone) {
            this(email, phone, false, phone);
        }
    }

    private final String deviceId;

    GrpcAccountClient(String deviceId) {
        this.deviceId = deviceId;
    }

    public GrpcAccountClient() {
        var prefs = java.util.prefs.Preferences.userNodeForPackage(GrpcAccountClient.class);
        String stored = prefs.get("authDeviceId", null);
        if (stored == null) {
            stored = java.util.UUID.randomUUID().toString();
            prefs.put("authDeviceId", stored);
        }
        deviceId = stored;
    }

    public Capabilities capabilities(String host, int port, boolean tls) throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            var c =
                    stub(channel)
                            .getAuthCapabilities(
                                    br.com.mss.tchow.auth.proto.GetAuthCapabilitiesRequest
                                            .getDefaultInstance());
            return new Capabilities(
                    c.getEmailEnabled(),
                    c.getPhoneEnabled(),
                    c.getSmsEnabled(),
                    c.getWhatsappEnabled() || (c.getPhoneEnabled() && !c.getSmsEnabled()));
        } catch (StatusRuntimeException e) {
            throw new TransportException(describe(e), e);
        } finally {
            channel.shutdownNow();
        }
    }

    public ChallengeDto requestChallenge(
            String host, int port, boolean tls, boolean email, String contactValue, Purpose purpose)
            throws TransportException {
        return requestChallenge(
                host, port, tls, email, contactValue, purpose, DeliveryChannel.DEFAULT);
    }

    public ChallengeDto requestChallenge(
            String host,
            int port,
            boolean tls,
            boolean email,
            String contactValue,
            Purpose purpose,
            DeliveryChannel deliveryChannel)
            throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            var response =
                    stub(channel)
                            .requestChallenge(
                                    br.com.mss.tchow.auth.proto.RequestChallengeRequest.newBuilder()
                                            .setContact(
                                                    contact(email, contactValue, deliveryChannel))
                                            .setDeviceId(deviceId)
                                            .setPurpose(toProto(purpose))
                                            .build());
            return challenge(response.getChallenge());
        } catch (StatusRuntimeException e) {
            throw new TransportException(describe(e), e);
        } finally {
            channel.shutdownNow();
        }
    }

    public Optional<AccountSessionDto> completeChallenge(
            String host,
            int port,
            boolean tls,
            boolean email,
            String contactValue,
            ChallengeDto challenge,
            String code)
            throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            var stub = stub(channel);
            return switch (challenge.purpose()) {
                case VERIFY_CONTACT ->
                        Optional.of(
                                toDto(
                                        stub.confirmContact(
                                                        ConfirmContactRequest.newBuilder()
                                                                .setContact(
                                                                        contact(
                                                                                email,
                                                                                contactValue))
                                                                .setCode(code)
                                                                .setChallengeId(challenge.id())
                                                                .build())
                                                .getSession()));
                case RECOVER_ACCOUNT ->
                        Optional.of(
                                toDto(
                                        stub.recoverAccount(
                                                        br.com.mss.tchow.auth.proto
                                                                .RecoverAccountRequest.newBuilder()
                                                                .setContact(
                                                                        contact(
                                                                                email,
                                                                                contactValue))
                                                                .setCode(code)
                                                                .setChallengeId(challenge.id())
                                                                .build())
                                                .getSession()));
                case DELETE_ACCOUNT -> {
                    stub.deleteAccount(
                            br.com.mss.tchow.auth.proto.DeleteAccountRequest.newBuilder()
                                    .setContact(contact(email, contactValue))
                                    .setCode(code)
                                    .setChallengeId(challenge.id())
                                    .build());
                    yield Optional.empty();
                }
            };
        } catch (StatusRuntimeException e) {
            throw new TransportException(describe(e), e);
        } finally {
            channel.shutdownNow();
        }
    }

    private static ChallengeDto challenge(br.com.mss.tchow.auth.proto.ChallengeInfo info) {
        return new ChallengeDto(
                info.getChallengeId(),
                fromProto(info.getPurpose()),
                info.getExpiresAtEpochSeconds());
    }

    private static br.com.mss.tchow.auth.proto.AuthChallengePurpose toProto(Purpose purpose) {
        return switch (purpose) {
            case VERIFY_CONTACT ->
                    br.com.mss.tchow.auth.proto.AuthChallengePurpose
                            .AUTH_CHALLENGE_PURPOSE_VERIFY_CONTACT;
            case RECOVER_ACCOUNT ->
                    br.com.mss.tchow.auth.proto.AuthChallengePurpose
                            .AUTH_CHALLENGE_PURPOSE_RECOVER_ACCOUNT;
            case DELETE_ACCOUNT ->
                    br.com.mss.tchow.auth.proto.AuthChallengePurpose
                            .AUTH_CHALLENGE_PURPOSE_DELETE_ACCOUNT;
        };
    }

    private static Purpose fromProto(br.com.mss.tchow.auth.proto.AuthChallengePurpose purpose) {
        return switch (purpose) {
            case AUTH_CHALLENGE_PURPOSE_VERIFY_CONTACT -> Purpose.VERIFY_CONTACT;
            case AUTH_CHALLENGE_PURPOSE_RECOVER_ACCOUNT -> Purpose.RECOVER_ACCOUNT;
            case AUTH_CHALLENGE_PURPOSE_DELETE_ACCOUNT -> Purpose.DELETE_ACCOUNT;
            default ->
                    throw new IllegalStateException(
                            "finalidade de desafio desconhecida: " + purpose);
        };
    }

    /**
     * Sessão de dispositivo devolvida por {@code CreateAccount}/{@code PromoteAccount}/{@code
     * ConfirmContact}.
     */
    public record AccountSessionDto(
            String token, String accountId, String guestId, long expiresAtEpochSeconds) {}

    /** New accounts get a fresh online identity; local history remains on the local profile. */
    public Registration register(
            String host,
            int port,
            boolean tls,
            String nick,
            String fullName,
            boolean isEmail,
            String contactValue)
            throws TransportException {
        return register(
                host, port, tls, nick, fullName, isEmail, contactValue, DeliveryChannel.DEFAULT);
    }

    public Registration register(
            String host,
            int port,
            boolean tls,
            String nick,
            String fullName,
            boolean isEmail,
            String contactValue,
            DeliveryChannel deliveryChannel)
            throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            var response =
                    stub(channel)
                            .createAccount(
                                    CreateAccountRequest.newBuilder()
                                            .setName(nick)
                                            .setDeviceId(deviceId)
                                            .setFullName(blankToEmpty(fullName))
                                            .setContact(
                                                    contact(isEmail, contactValue, deliveryChannel))
                                            .build());
            return new Registration(
                    response.hasSession()
                            ? Optional.of(toDto(response.getSession()))
                            : Optional.empty(),
                    response.hasChallenge() ? challenge(response.getChallenge()) : null);
        } catch (StatusRuntimeException e) {
            throw new TransportException(describe(e), e);
        } finally {
            channel.shutdownNow();
        }
    }

    /** Confirma o código — funciona mesmo sem sessão nenhuma guardada (docs/issues.md §E6-10). */
    public AccountSessionDto confirmContact(
            String host, int port, boolean tls, boolean isEmail, String contactValue, String code)
            throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            AccountSession session =
                    stub(channel)
                            .confirmContact(
                                    ConfirmContactRequest.newBuilder()
                                            .setCode(code)
                                            .setContact(contact(isEmail, contactValue))
                                            .build())
                            .getSession();
            return toDto(session);
        } catch (StatusRuntimeException e) {
            throw new TransportException(describe(e), e);
        } finally {
            channel.shutdownNow();
        }
    }

    /** Resposta sempre "ok" do servidor, contato existindo ou não (anti-enumeração, [E6-10]). */
    public void resendConfirmationCode(
            String host, int port, boolean tls, boolean isEmail, String contactValue)
            throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            stub(channel)
                    .resendConfirmationCode(
                            ResendConfirmationCodeRequest.newBuilder()
                                    .setContact(contact(isEmail, contactValue))
                                    .build());
        } catch (StatusRuntimeException e) {
            throw new TransportException(describe(e), e);
        } finally {
            channel.shutdownNow();
        }
    }

    private static AuthServiceGrpc.AuthServiceBlockingStub stub(ManagedChannel channel) {
        return AuthServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);
    }

    private static Contact contact(boolean isEmail, String value) {
        return contact(isEmail, value, DeliveryChannel.DEFAULT);
    }

    private static Contact contact(boolean isEmail, String value, DeliveryChannel channel) {
        return isEmail
                ? Contact.newBuilder().setEmail(value).build()
                : Contact.newBuilder()
                        .setPhoneE164(value)
                        .setDeliveryChannel(
                                switch (channel) {
                                    case DEFAULT ->
                                            br.com.mss.tchow.auth.proto.ContactDeliveryChannel
                                                    .CONTACT_DELIVERY_CHANNEL_UNSPECIFIED;
                                    case SMS ->
                                            br.com.mss.tchow.auth.proto.ContactDeliveryChannel
                                                    .CONTACT_DELIVERY_CHANNEL_SMS;
                                    case WHATSAPP ->
                                            br.com.mss.tchow.auth.proto.ContactDeliveryChannel
                                                    .CONTACT_DELIVERY_CHANNEL_WHATSAPP;
                                })
                        .build();
    }

    private static AccountSessionDto toDto(AccountSession session) {
        return new AccountSessionDto(
                session.getToken(),
                session.getAccountId(),
                session.getGuestId(),
                session.getExpiresAtEpochSeconds());
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String describe(StatusRuntimeException e) {
        String description = e.getStatus().getDescription();
        return description != null ? description : "falha ao falar com o servidor";
    }

    private static ManagedChannel channel(String host, int port, boolean tls) {
        ChannelCredentials credentials =
                tls ? TlsChannelCredentials.create() : InsecureChannelCredentials.create();
        return Grpc.newChannelBuilderForAddress(host, port, credentials).build();
    }
}
