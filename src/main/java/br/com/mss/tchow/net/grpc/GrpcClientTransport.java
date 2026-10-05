package br.com.mss.tchow.net.grpc;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.AccountCredentials;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import br.com.mss.tchow.net.GameEvent;
import br.com.mss.tchow.net.GameEventListener;
import br.com.mss.tchow.net.GameTransport;
import br.com.mss.tchow.net.TransportException;
import br.com.mss.tchow.net.grpc.proto.ChatMessageProto;
import br.com.mss.tchow.net.grpc.proto.GameEventProto;
import br.com.mss.tchow.net.grpc.proto.GameServiceGrpc;
import br.com.mss.tchow.net.grpc.proto.GetMyStatsRequest;
import br.com.mss.tchow.net.grpc.proto.JoinRequest;
import br.com.mss.tchow.net.grpc.proto.JoinResponse;
import br.com.mss.tchow.net.grpc.proto.RematchRequest;
import br.com.mss.tchow.net.grpc.proto.SendChatRequest;
import br.com.mss.tchow.net.grpc.proto.SubmitMoveRequest;
import br.com.mss.tchow.net.match.MatchId;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.MetadataUtils;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cliente puro: abre um {@link ManagedChannel} para {@code host:port}, chama {@code Join}
 * (server-streaming) e traduz cada mensagem em {@link GameEvent}, entregue na EDT.
 *
 * <p>Logging (ADR-0011): conectar/desconectar em INFO; falha de transporte em WARN.
 */
public final class GrpcClientTransport implements GameTransport {

    private static final Logger logger = LoggerFactory.getLogger(GrpcClientTransport.class);

    private final String host;
    private final int port;
    private final PlayerColor color;
    private final String nick;
    private final String guestId;
    private final String password;
    private final String sessionToken;
    private final MatchId matchId;
    private final boolean tls;
    private final AccountCredentials accountCredentials;
    private final ManagedChannel channel;
    private final GameServiceGrpc.GameServiceStub asyncStub;
    private final GameServiceGrpc.GameServiceBlockingStub blockingStub;
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Renovação periódica do acesso da identidade MSS enquanto a partida está aberta: o servidor
     * revalida o stream {@code Join} a cada evento e, quando o acesso de 10 min vence, usa a
     * credencial mais recente da mesma conta recebida em qualquer RPC unário (TchowStrick M8). Um
     * {@code GetMyStats} periódico entrega essa credencial mesmo com o jogador ocioso.
     *
     * <p>A biblioteca reusa o acesso em cache até 1 min antes de vencer; com período de 1 min há
     * sempre uma chamada nessa janela final, que leva ao servidor um acesso novo antes do
     * vencimento do anterior (um período maior reenviaria o mesmo token e o stream cairia).
     */
    static final Duration ACCESS_KEEPALIVE = Duration.ofMinutes(1);

    private volatile ClientCallStreamObserver<JoinRequest> joinCall;
    private volatile boolean disconnecting;
    private volatile ScheduledExecutorService keepalive;
    private volatile String issuedSessionToken = "";

    public GrpcClientTransport(
            String host, int port, String nick, String guestId, PlayerColor color)
            throws TransportException {
        this(host, port, nick, guestId, color, null);
    }

    /**
     * @param guestId id de convidado do jogador — trava o assento a este id (ADR-0009); vazio = sem
     *     trava.
     * @param matchId partida a endereçar (via metadata {@code tchow-match-id}); {@code null} deixa
     *     o servidor assumir a única partida aberta.
     */
    public GrpcClientTransport(
            String host, int port, String nick, String guestId, PlayerColor color, MatchId matchId)
            throws TransportException {
        this(host, port, nick, guestId, color, matchId, "");
    }

    /**
     * @param password senha da partida (ADR-0012); vazia se ela não estiver trancada.
     */
    public GrpcClientTransport(
            String host,
            int port,
            String nick,
            String guestId,
            PlayerColor color,
            MatchId matchId,
            String password)
            throws TransportException {
        this(host, port, nick, guestId, color, matchId, password, "");
    }

    /**
     * @param sessionToken token de sessão (ADR-0013, {@code [E4c-01]}) a apresentar numa reconexão
     *     a uma cor já com dono; vazio no 1º join dessa cor. O token emitido pelo servidor fica
     *     disponível em {@link #issuedSessionToken()} depois de {@link #connect()}.
     */
    public GrpcClientTransport(
            String host,
            int port,
            String nick,
            String guestId,
            PlayerColor color,
            MatchId matchId,
            String password,
            String sessionToken)
            throws TransportException {
        this(host, port, nick, guestId, color, matchId, password, sessionToken, false);
    }

    /**
     * @param tls usa {@code TlsChannelCredentials} em vez de texto claro (E5, ADR-0005) — produção
     *     (atrás do Caddy) manda {@code true}; LAN/dev/embutido continuam em claro.
     */
    public GrpcClientTransport(
            String host,
            int port,
            String nick,
            String guestId,
            PlayerColor color,
            MatchId matchId,
            String password,
            String sessionToken,
            boolean tls)
            throws TransportException {
        this(host, port, nick, guestId, color, matchId, password, sessionToken, tls, "");
    }

    /** accountToken is supplied only for a trusted official TLS endpoint. */
    public GrpcClientTransport(
            String host,
            int port,
            String nick,
            String guestId,
            PlayerColor color,
            MatchId matchId,
            String password,
            String sessionToken,
            boolean tls,
            String accountToken)
            throws TransportException {
        this(
                host,
                port,
                nick,
                guestId,
                color,
                matchId,
                password,
                sessionToken,
                tls,
                AccountCredentials.fixed(accountToken));
    }

    /**
     * @param accountCredentials consultada a cada chamada (M1): com a identidade MSS, o acesso de
     *     jogo é renovado antes de expirar, inclusive durante partidas longas; um {@code
     *     UNAUTHENTICATED} gera no máximo uma nova tentativa com credencial renovada. Texto claro
     *     só é aceito com credencial para {@code localhost}.
     */
    public GrpcClientTransport(
            String host,
            int port,
            String nick,
            String guestId,
            PlayerColor color,
            MatchId matchId,
            String password,
            String sessionToken,
            boolean tls,
            AccountCredentials accountCredentials)
            throws TransportException {
        String violation = CredentialRetry.plaintextViolation(accountCredentials, host, tls);
        if (violation != null) {
            throw new TransportException(violation);
        }
        this.accountCredentials = accountCredentials;
        this.host = host;
        this.port = port;
        this.color = color;
        this.nick = nick;
        this.guestId = guestId == null ? "" : guestId;
        this.password = password == null ? "" : password;
        this.sessionToken = sessionToken == null ? "" : sessionToken;
        this.matchId = matchId;
        this.tls = tls;
        try {
            ChannelCredentials credentials =
                    tls ? TlsChannelCredentials.create() : InsecureChannelCredentials.create();
            this.channel = Grpc.newChannelBuilderForAddress(host, port, credentials).build();
        } catch (RuntimeException e) {
            throw new TransportException("endereço inválido: " + host + ":" + port, e);
        }
        // Account login and match reconnection use different credentials.
        Metadata headers = MatchRouting.headerFor(matchId);
        var routing = MetadataUtils.newAttachHeadersInterceptor(headers);
        var credentials =
                new GameCallCredentials(
                        accountCredentials,
                        () ->
                                issuedSessionToken.isBlank()
                                        ? this.sessionToken
                                        : issuedSessionToken);
        this.asyncStub = GameServiceGrpc.newStub(channel).withInterceptors(routing, credentials);
        this.blockingStub =
                GameServiceGrpc.newBlockingStub(channel).withInterceptors(routing, credentials);
    }

    @Override
    public GameSnapshotDto connect() throws TransportException {
        try {
            try {
                return joinOnce();
            } catch (ExecutionException e) {
                if (!CredentialRetry.shouldRetry(accountCredentials, e.getCause())) {
                    throw e;
                }
                logger.info(
                        "credencial recusada ao entrar em {}:{}; renovando uma vez", host, port);
                return joinOnce();
            }
        } catch (ExecutionException e) {
            String message = describe(e.getCause());
            logger.warn("falha ao conectar em {}:{}: {}", host, port, message);
            throw new TransportException(message, e.getCause());
        } catch (TimeoutException e) {
            logger.warn("tempo esgotado ao conectar em {}:{}", host, port);
            throw new TransportException("tempo esgotado ao conectar", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("conexão interrompida", e);
        }
    }

    private GameSnapshotDto joinOnce()
            throws ExecutionException, TimeoutException, InterruptedException {
        CompletableFuture<GameSnapshotDto> firstSnapshot = new CompletableFuture<>();

        JoinRequest request =
                JoinRequest.newBuilder()
                        .setNick(nick)
                        .setGuestId(guestId)
                        .setColor(ProtoMapper.toProto(color))
                        .setPassword(password)
                        .setSessionToken(sessionToken)
                        .build();

        asyncStub.join(
                request,
                new ClientResponseObserver<JoinRequest, JoinResponse>() {
                    @Override
                    public void beforeStart(ClientCallStreamObserver<JoinRequest> requestStream) {
                        joinCall = requestStream;
                    }

                    @Override
                    public void onNext(JoinResponse response) {
                        GameEventProto proto = response.getEvent();
                        if (proto.getKindCase() == GameEventProto.KindCase.INITIAL_SNAPSHOT) {
                            issuedSessionToken = response.getSessionToken();
                            firstSnapshot.complete(
                                    ProtoMapper.snapshot(proto.getInitialSnapshot()));
                        } else {
                            GameEvent event = ProtoMapper.fromProto(proto);
                            SwingUtilities.invokeLater(
                                    () -> listeners.forEach(l -> l.onEvent(event)));
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (disconnecting) {
                            logger.debug(
                                    "stream da partida em {}:{} encerrado pelo cliente",
                                    host,
                                    port);
                        } else if (firstSnapshot.isDone()) {
                            logger.warn(
                                    "stream da partida em {}:{} encerrado com erro: {}",
                                    host,
                                    port,
                                    describe(t));
                        }
                        firstSnapshot.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        // servidor encerrou o stream — nada a fazer
                    }
                });

        GameSnapshotDto snapshot = firstSnapshot.get(10, TimeUnit.SECONDS);
        logger.info("conectado a {}:{} como {} ({})", host, port, nick, color);
        startAccessKeepalive();
        return snapshot;
    }

    /** Só com credencial renovável (identidade MSS); sessão antiga e LAN não precisam. */
    private synchronized void startAccessKeepalive() {
        if (keepalive != null
                || accountCredentials.isEmpty()
                || !accountCredentials.renewAfterRejection()) {
            return;
        }
        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "tchow-acesso-identidade");
                            t.setDaemon(true);
                            return t;
                        });
        long period = ACCESS_KEEPALIVE.toSeconds();
        executor.scheduleWithFixedDelay(
                this::refreshStreamAccess, period, period, TimeUnit.SECONDS);
        keepalive = executor;
    }

    void refreshStreamAccess() {
        try {
            var request = GetMyStatsRequest.newBuilder().setGuestId(guestId).build();
            CredentialRetry.call(
                    accountCredentials,
                    () -> blockingStub.withDeadlineAfter(5, TimeUnit.SECONDS).getMyStats(request));
        } catch (RuntimeException e) {
            // Sem derrubar a partida: se o acesso não puder ser renovado, o stream cai sozinho.
            logger.warn(
                    "não consegui renovar o acesso da partida em {}:{}: {}",
                    host,
                    port,
                    describe(e));
        }
    }

    private synchronized void stopAccessKeepalive() {
        if (keepalive != null) {
            keepalive.shutdownNow();
            keepalive = null;
        }
    }

    @Override
    public void submitMove(MoveDto move) throws TransportException {
        try {
            var request = SubmitMoveRequest.newBuilder().setMove(ProtoMapper.toProto(move)).build();
            CredentialRetry.run(accountCredentials, () -> blockingStub.submitMove(request));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION) {
                String reason =
                        e.getStatus().getDescription() != null
                                ? e.getStatus().getDescription()
                                : "jogada recusada";
                SwingUtilities.invokeLater(
                        () ->
                                listeners.forEach(
                                        l -> l.onEvent(new GameEvent.MoveRejected(reason))));
            } else {
                throw new TransportException("conexão com o servidor perdida", e);
            }
        }
    }

    @Override
    public void sendChat(String text) throws TransportException {
        try {
            var request =
                    SendChatRequest.newBuilder()
                            .setMessage(
                                    ChatMessageProto.newBuilder()
                                            .setColor(ProtoMapper.toProto(color))
                                            .setNick(nick)
                                            .setText(text))
                            .build();
            CredentialRetry.run(accountCredentials, () -> blockingStub.sendChat(request));
        } catch (StatusRuntimeException e) {
            throw new TransportException("falha ao enviar a mensagem", e);
        }
    }

    @Override
    public void rematch() throws TransportException {
        // Pedido de revanche: o desfecho (RematchStarted / RematchRejected) vem pelo stream.
        try {
            var request =
                    RematchRequest.newBuilder().setRequester(ProtoMapper.toProto(color)).build();
            CredentialRetry.run(accountCredentials, () -> blockingStub.rematch(request));
        } catch (StatusRuntimeException e) {
            throw new TransportException("conexão com o servidor perdida", e);
        }
    }

    @Override
    public void respondRematch(boolean accept) throws TransportException {
        try {
            var request =
                    br.com.mss.tchow.net.grpc.proto.RespondRematchRequest.newBuilder()
                            .setResponder(ProtoMapper.toProto(color))
                            .setAccept(accept)
                            .build();
            CredentialRetry.run(accountCredentials, () -> blockingStub.respondRematch(request));
        } catch (StatusRuntimeException e) {
            throw new TransportException("conexão com o servidor perdida", e);
        }
    }

    @Override
    public boolean supportsUndo() {
        return true;
    }

    @Override
    public boolean undoUsesTokens() {
        return true;
    }

    @Override
    public boolean canUndo() {
        // O servidor é a autoridade (é minha última jogada? cota?); o botão fica
        // ativo durante a partida e a recusa vem como UndoRejected.
        return true;
    }

    @Override
    public void undo() throws TransportException {
        // Sem consentimento: o desfecho chega pelo stream (HistoryChanged / UndoRejected).
        try {
            var request =
                    br.com.mss.tchow.net.grpc.proto.RequestUndoRequest.newBuilder()
                            .setRequester(ProtoMapper.toProto(color))
                            .build();
            CredentialRetry.run(accountCredentials, () -> blockingStub.requestUndo(request));
        } catch (StatusRuntimeException e) {
            throw new TransportException("conexão com o servidor perdida", e);
        }
    }

    @Override
    public void disconnect() {
        logger.info("desconectando de {}:{}", host, port);
        disconnecting = true;
        stopAccessKeepalive();
        ClientCallStreamObserver<JoinRequest> call = joinCall;
        if (call != null) {
            call.cancel("cliente saiu", null);
        }
        channel.shutdown();
        try {
            channel.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        channel.shutdownNow();
    }

    @Override
    public void addListener(GameEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public PlayerColor localColor() {
        return color;
    }

    /**
     * Token de sessão emitido pelo servidor no {@link #connect()} (ADR-0013, {@code [E4c-01]}) —
     * apresente-o numa reconexão futura a esta mesma partida/cor. Vazio antes de conectar, ou se o
     * servidor não emitiu nenhum (modo/servidor antigo).
     */
    public String issuedSessionToken() {
        return issuedSessionToken;
    }

    /** A partida endereçada por este transporte; {@code null} se conectado sem {@code match_id}. */
    public MatchId matchId() {
        return matchId;
    }

    private String describe(Throwable cause) {
        return GrpcErrors.describe(tls, host, port, cause, "não foi possível conectar ao servidor");
    }
}
