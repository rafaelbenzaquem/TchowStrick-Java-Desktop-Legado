package br.com.mss.tchow.net.grpc;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.Dtos.MatchInfoDto;
import br.com.mss.tchow.net.Dtos.PlayerStatsDto;
import br.com.mss.tchow.net.MatchDiscovery;
import br.com.mss.tchow.net.TransportException;
import br.com.mss.tchow.net.grpc.proto.CreateMatchRequest;
import br.com.mss.tchow.net.grpc.proto.GameServiceGrpc;
import br.com.mss.tchow.net.grpc.proto.GetMatchInfoRequest;
import br.com.mss.tchow.net.grpc.proto.GetMyStatsRequest;
import br.com.mss.tchow.net.grpc.proto.ListOpenMatchesRequest;
import br.com.mss.tchow.net.match.MatchId;
import br.com.mss.tchow.net.match.OpenMatchSummary;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chamadas unárias de pré-partida (sem entrar): consultar o resumo e criar uma partida.
 *
 * <p>Logging (ADR-0011, achado em 2026-09-15 — {@code [OBS-01]}): falha de transporte em WARN, com
 * a causa original, não só a mensagem genérica que a UI mostra.
 */
public final class GrpcDiscovery implements MatchDiscovery {

    private final String accountToken;

    public GrpcDiscovery() {
        this("");
    }

    public GrpcDiscovery(String accountToken) {
        this.accountToken = accountToken;
    }

    private GameServiceGrpc.GameServiceBlockingStub authenticatedStub(
            ManagedChannel channel, boolean tls) {
        if (!accountToken.isBlank() && !tls)
            throw new IllegalArgumentException("conta oficial exige TLS");
        io.grpc.Metadata headers = new io.grpc.Metadata();
        CallIdentity.attach(headers, accountToken);
        return GameServiceGrpc.newBlockingStub(channel)
                .withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static final Logger logger = LoggerFactory.getLogger(GrpcDiscovery.class);

    @Override
    public MatchInfoDto peek(String host, int port, boolean tls) throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            return ProtoMapper.matchInfo(
                    GameServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(5, TimeUnit.SECONDS)
                            .getMatchInfo(GetMatchInfoRequest.getDefaultInstance())
                            .getMatchInfo());
        } catch (StatusRuntimeException e) {
            String message =
                    GrpcErrors.describe(
                            tls, host, port, e, "não achei uma partida em " + host + ":" + port);
            logger.warn("falha ao consultar {}:{} (tls={}): {}", host, port, tls, message, e);
            throw new TransportException(message, e);
        } finally {
            channel.shutdownNow();
        }
    }

    /** Cria uma partida pública no servidor e devolve o seu {@link MatchId}. */
    public MatchId createMatch(
            String host, int port, int width, int height, List<PlayerColor> roster, boolean tls)
            throws TransportException {
        return createMatch(host, port, width, height, roster, "", tls);
    }

    /** {@code password} não-vazio tranca a partida (ADR-0012). */
    public MatchId createMatch(
            String host,
            int port,
            int width,
            int height,
            List<PlayerColor> roster,
            String password,
            boolean tls)
            throws TransportException {
        return createMatch(
                host,
                port,
                CreateMatchRequest.newBuilder()
                        .setWidth(width)
                        .setHeight(height)
                        .addAllRoster(roster.stream().map(ProtoMapper::toProto).toList())
                        .setPassword(password == null ? "" : password)
                        .build(),
                tls);
    }

    /**
     * Elenco aberto ([E4.5-05], ADR-0015): {@code maxPlayers} é só a quantidade — cada jogador
     * (inclusive quem cria) escolhe a cor livremente ao entrar (ver {@code MatchService.join}).
     */
    public MatchId createMatch(
            String host,
            int port,
            int width,
            int height,
            int maxPlayers,
            String password,
            boolean tls)
            throws TransportException {
        return createMatch(
                host,
                port,
                CreateMatchRequest.newBuilder()
                        .setWidth(width)
                        .setHeight(height)
                        .setMaxPlayers(maxPlayers)
                        .setPassword(password == null ? "" : password)
                        .build(),
                tls);
    }

    private MatchId createMatch(String host, int port, CreateMatchRequest request, boolean tls)
            throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            String id =
                    authenticatedStub(channel, tls)
                            .withDeadlineAfter(5, TimeUnit.SECONDS)
                            .createMatch(request)
                            .getMatchId();
            return new MatchId(id);
        } catch (StatusRuntimeException e) {
            String message =
                    GrpcErrors.describe(
                            tls,
                            host,
                            port,
                            e,
                            "não consegui criar a partida em " + host + ":" + port);
            logger.warn(
                    "falha ao criar partida em {}:{} (tls={}): {}", host, port, tls, message, e);
            throw new TransportException(message, e);
        } finally {
            channel.shutdownNow();
        }
    }

    /** Lobby (ADR-0012): as partidas abertas naquele servidor, para escolher qual entrar. */
    @Override
    public List<OpenMatchSummary> listOpenMatches(String host, int port, boolean tls)
            throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            return GameServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .listOpenMatches(ListOpenMatchesRequest.getDefaultInstance())
                    .getMatchesList()
                    .stream()
                    .map(ProtoMapper::fromProto)
                    .toList();
        } catch (StatusRuntimeException e) {
            String message =
                    GrpcErrors.describe(
                            tls,
                            host,
                            port,
                            e,
                            "não consegui listar as partidas em " + host + ":" + port);
            logger.warn(
                    "falha ao listar partidas em {}:{} (tls={}): {}", host, port, tls, message, e);
            throw new TransportException(message, e);
        } finally {
            channel.shutdownNow();
        }
    }

    /**
     * Estatísticas acumuladas do jogador ([E4d-01], UI em [E4d-04]). Guardada pelo {@code
     * OfficialAccountGuard} igual às demais RPCs de partida — exige conta oficial só no servidor
     * oficial (o {@link #authenticatedStub} cuida disso).
     */
    public PlayerStatsDto getMyStats(String host, int port, String guestId, boolean tls)
            throws TransportException {
        ManagedChannel channel = channel(host, port, tls);
        try {
            return ProtoMapper.playerStats(
                    authenticatedStub(channel, tls)
                            .withDeadlineAfter(5, TimeUnit.SECONDS)
                            .getMyStats(
                                    GetMyStatsRequest.newBuilder().setGuestId(guestId).build()));
        } catch (StatusRuntimeException e) {
            String message =
                    GrpcErrors.describe(
                            tls,
                            host,
                            port,
                            e,
                            "não consegui buscar suas estatísticas em " + host + ":" + port);
            logger.warn(
                    "falha ao buscar estatísticas em {}:{} (tls={}): {}",
                    host,
                    port,
                    tls,
                    message,
                    e);
            throw new TransportException(message, e);
        } finally {
            channel.shutdownNow();
        }
    }

    private static ManagedChannel channel(String host, int port, boolean tls) {
        ChannelCredentials credentials =
                tls ? TlsChannelCredentials.create() : InsecureChannelCredentials.create();
        return Grpc.newChannelBuilderForAddress(host, port, credentials).build();
    }
}
