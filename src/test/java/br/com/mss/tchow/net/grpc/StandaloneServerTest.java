package br.com.mss.tchow.net.grpc;

import static br.com.mss.tchow.domain.EdgeOrientation.HORIZONTAL;
import static br.com.mss.tchow.domain.PlayerColor.BLUE;
import static br.com.mss.tchow.domain.PlayerColor.GREEN;
import static br.com.mss.tchow.domain.PlayerColor.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import br.com.mss.tchow.net.Dtos.PlayerStatsDto;
import br.com.mss.tchow.net.GameEvent;
import br.com.mss.tchow.net.GameEventListener;
import br.com.mss.tchow.net.TransportException;
import br.com.mss.tchow.net.grpc.proto.GameServiceGrpc;
import br.com.mss.tchow.net.grpc.proto.GetMyStatsRequest;
import br.com.mss.tchow.net.grpc.proto.GetMyStatsResponse;
import br.com.mss.tchow.net.match.MatchId;
import br.com.mss.tchow.net.match.MatchPersistence;
import br.com.mss.tchow.net.match.MatchRegistry;
import br.com.mss.tchow.net.match.OpenMatchSummary;
import br.com.mss.tchow.net.match.PlayerStats;
import br.com.mss.tchow.net.match.PlayerStatsQuery;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Emula "{@code ServerMain} + 2 desktops" ([E4a-02]): sobe o servidor exatamente como o {@code
 * ServerMain} standalone faria ({@link GrpcServerFactory}, sem nenhum jogador embutido) e conecta
 * <b>dois</b> clientes puros ({@link GrpcClientTransport}) — diferente do {@link
 * GrpcTransportTest}, nenhum lado usa o atalho em processo do {@link GrpcHostTransport}.
 */
class StandaloneServerTest {

    private Server server;
    private final List<GrpcClientTransport> open = new ArrayList<>();
    private final List<ManagedChannel> rawChannels = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (GrpcClientTransport t : open) {
            try {
                t.disconnect();
            } catch (RuntimeException ignored) {
                // encerrando
            }
        }
        open.clear();
        for (ManagedChannel ch : rawChannels) {
            ch.shutdownNow();
        }
        rawChannels.clear();
        if (server != null) {
            server.shutdownNow();
        }
    }

    private GrpcClientTransport client(int port, String nick, PlayerColor color, MatchId id)
            throws TransportException {
        return client(port, nick, color, id, "");
    }

    private GrpcClientTransport client(
            int port, String nick, PlayerColor color, MatchId id, String password)
            throws TransportException {
        GrpcClientTransport t =
                new GrpcClientTransport("127.0.0.1", port, nick, nick, color, id, password);
        open.add(t);
        return t;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> {});
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new TimeoutException("condição não satisfeita em 5s");
    }

    private static final class Collector implements GameEventListener {
        private final List<GameEvent> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onEvent(GameEvent event) {
            events.add(event);
        }

        <T extends GameEvent> List<T> of(Class<T> type) {
            synchronized (events) {
                return events.stream().filter(type::isInstance).map(type::cast).toList();
            }
        }
    }

    @Test
    void doisClientesPurosJogamContraUmServidorStandalone() throws Exception {
        int port = freePort();
        MatchRegistry registry = new MatchRegistry();
        server = GrpcServerFactory.start(port, registry);

        MatchId matchId =
                new GrpcDiscovery().createMatch("127.0.0.1", port, 2, 1, List.of(RED, BLUE), false);

        GrpcClientTransport hostClient = client(port, "A", RED, matchId);
        Collector hostEvents = new Collector();
        hostClient.addListener(hostEvents);
        GameSnapshotDto hostSnapshot = hostClient.connect();
        assertEquals(2, hostSnapshot.width());

        GrpcClientTransport guestClient = client(port, "B", BLUE, matchId);
        Collector guestEvents = new Collector();
        guestClient.addListener(guestEvents);
        GameSnapshotDto guestSnapshot = guestClient.connect();
        assertTrue(guestSnapshot.started(), "2 de 2 → partida começa");

        hostClient.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0));
        awaitUntil(() -> !guestEvents.of(GameEvent.MoveApplied.class).isEmpty());
        assertEquals(BLUE, guestEvents.of(GameEvent.MoveApplied.class).get(0).nextPlayer());
        assertEquals(BLUE, registry.get(matchId).service().view().currentPlayer());
    }

    /**
     * DoD do `[E4a-05a]` (ADR-0012): com 3 partidas simultâneas no servidor, {@code
     * ListOpenMatches} devolve só as que têm vaga livre — a cheia não aparece.
     */
    @Test
    void listOpenMatchesMostraSoAsPartidasComVagaLivre() throws Exception {
        int port = freePort();
        MatchRegistry registry = new MatchRegistry();
        server = GrpcServerFactory.start(port, registry);
        GrpcDiscovery discovery = new GrpcDiscovery();

        MatchId semNinguem =
                discovery.createMatch("127.0.0.1", port, 2, 1, List.of(RED, BLUE), false);

        MatchId cheia = discovery.createMatch("127.0.0.1", port, 2, 1, List.of(RED, BLUE), false);
        client(port, "A", RED, cheia).connect();
        client(port, "B", BLUE, cheia).connect();

        MatchId comUmaVaga =
                discovery.createMatch("127.0.0.1", port, 3, 3, List.of(RED, BLUE, GREEN), false);
        client(port, "C", RED, comUmaVaga).connect();

        List<OpenMatchSummary> open = discovery.listOpenMatches("127.0.0.1", port, false);

        assertEquals(2, open.size());
        assertTrue(open.stream().anyMatch(s -> s.id().equals(semNinguem)));
        assertTrue(open.stream().anyMatch(s -> s.id().equals(comUmaVaga)));
        assertTrue(open.stream().noneMatch(s -> s.id().equals(cheia)), "a cheia não aparece");

        OpenMatchSummary comUmaVagaResumo =
                open.stream().filter(s -> s.id().equals(comUmaVaga)).findFirst().orElseThrow();
        assertEquals(List.of(BLUE, GREEN), comUmaVagaResumo.availableColors());
        assertEquals(1, comUmaVagaResumo.joined().size());
    }

    /**
     * DoD do `[E4a-05b]` (ADR-0012): criar com senha e tentar entrar sem ela (ou com a errada) ⇒
     * recusa clara; senha certa ⇒ entra normal. `ListOpenMatches` mostra a partida como trancada,
     * sem revelar a senha.
     */
    @Test
    void partidaTrancadaSoDeixaEntrarComASenhaCerta() throws Exception {
        int port = freePort();
        MatchRegistry registry = new MatchRegistry();
        server = GrpcServerFactory.start(port, registry);
        GrpcDiscovery discovery = new GrpcDiscovery();

        MatchId matchId =
                discovery.createMatch(
                        "127.0.0.1", port, 2, 1, List.of(RED, BLUE), "segredo", false);

        TransportException semSenha =
                assertThrows(
                        TransportException.class,
                        () -> client(port, "A", RED, matchId, "").connect());
        assertTrue(semSenha.getMessage().toLowerCase().contains("senha"));

        TransportException senhaErrada =
                assertThrows(
                        TransportException.class,
                        () -> client(port, "A", RED, matchId, "chuta").connect());
        assertTrue(senhaErrada.getMessage().toLowerCase().contains("senha"));

        GameSnapshotDto snapshot = client(port, "A", RED, matchId, "segredo").connect();
        assertEquals(2, snapshot.width());

        OpenMatchSummary resumo = discovery.listOpenMatches("127.0.0.1", port, false).get(0);
        assertTrue(resumo.locked());
    }

    /**
     * {@code [E4d-01]}: a RPC {@code GetMyStats} devolve o que a {@link PlayerStatsQuery} do
     * servidor responde; sem query configurada (servidor sem banco) devolve tudo zero — nunca um
     * erro.
     */
    @Test
    void getMyStatsDevolveAsEstatisticasDoJogadorEZeroSemBanco() throws Exception {
        int port = freePort();
        PlayerStatsQuery fake =
                guestId ->
                        "guest-a".equals(guestId)
                                ? new PlayerStats(10, 6, 3, 1, 42)
                                : PlayerStats.EMPTY;
        MatchRegistry registry =
                new MatchRegistry(
                        MatchRegistry.DEFAULT_IDLE_TTL,
                        Clock.systemUTC(),
                        MatchPersistence.NONE,
                        fake);
        server = GrpcServerFactory.start(port, registry);

        ManagedChannel channel =
                Grpc.newChannelBuilderForAddress(
                                "127.0.0.1", port, InsecureChannelCredentials.create())
                        .build();
        rawChannels.add(channel);
        GameServiceGrpc.GameServiceBlockingStub stub = GameServiceGrpc.newBlockingStub(channel);

        GetMyStatsResponse mine =
                stub.getMyStats(GetMyStatsRequest.newBuilder().setGuestId("guest-a").build());
        assertEquals(10, mine.getPlayed());
        assertEquals(6, mine.getWon());
        assertEquals(3, mine.getLost());
        assertEquals(1, mine.getDrawn());
        assertEquals(42, mine.getBoxesTotal());

        GetMyStatsResponse anon =
                stub.getMyStats(GetMyStatsRequest.newBuilder().setGuestId("").build());
        assertEquals(0, anon.getPlayed());
        assertEquals(0, anon.getBoxesTotal());
    }

    /**
     * O lado cliente de {@code [E4d-01]} ([E4d-04]): {@link GrpcDiscovery#getMyStats} decodifica a
     * resposta do servidor no mesmo {@link PlayerStatsDto} que a UI (aba "Jogador → Estatísticas…")
     * usa.
     */
    @Test
    void getMyStatsDoClienteDecodificaAsEstatisticasDoServidor() throws Exception {
        int port = freePort();
        PlayerStatsQuery fake =
                guestId ->
                        "guest-a".equals(guestId)
                                ? new PlayerStats(10, 6, 3, 1, 42)
                                : PlayerStats.EMPTY;
        MatchRegistry registry =
                new MatchRegistry(
                        MatchRegistry.DEFAULT_IDLE_TTL,
                        Clock.systemUTC(),
                        MatchPersistence.NONE,
                        fake);
        server = GrpcServerFactory.start(port, registry);

        PlayerStatsDto stats = new GrpcDiscovery().getMyStats("127.0.0.1", port, "guest-a", false);

        assertEquals(10, stats.played());
        assertEquals(6, stats.won());
        assertEquals(3, stats.lost());
        assertEquals(1, stats.drawn());
        assertEquals(42, stats.boxesTotal());
    }
}
