package br.com.mss.tchow.net.grpc;

import static br.com.mss.tchow.domain.EdgeOrientation.HORIZONTAL;
import static br.com.mss.tchow.domain.EdgeOrientation.VERTICAL;
import static br.com.mss.tchow.domain.PlayerColor.BLUE;
import static br.com.mss.tchow.domain.PlayerColor.GREEN;
import static br.com.mss.tchow.domain.PlayerColor.PINK;
import static br.com.mss.tchow.domain.PlayerColor.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MatchInfoDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import br.com.mss.tchow.net.GameEvent;
import br.com.mss.tchow.net.GameEventListener;
import br.com.mss.tchow.net.GameTransport;
import br.com.mss.tchow.net.TransportException;
import br.com.mss.tchow.net.grpc.proto.GameEventProto;
import br.com.mss.tchow.net.grpc.proto.GameServiceGrpc;
import br.com.mss.tchow.net.grpc.proto.JoinRequest;
import br.com.mss.tchow.net.grpc.proto.JoinResponse;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Integração ponta a ponta do transporte gRPC (host + cliente no mesmo JVM). */
class GrpcTransportTest {

    private final List<GameTransport> open = new ArrayList<>();
    private final List<ManagedChannel> rawChannels = new ArrayList<>();

    @AfterEach
    void closeAll() {
        for (GameTransport t : open) {
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
    }

    private GrpcHostTransport host(
            int port, int w, int h, List<PlayerColor> roster, PlayerColor color)
            throws TransportException {
        GrpcHostTransport t =
                new GrpcHostTransport(port, w, h, roster, color, "host", "host-guest");
        open.add(t);
        return t;
    }

    /** Elenco aberto ([E4.5-05], ADR-0015): {@code maxPlayers} é só a quantidade. */
    private GrpcHostTransport hostOpen(int port, int w, int h, int maxPlayers, PlayerColor color)
            throws TransportException {
        GrpcHostTransport t =
                new GrpcHostTransport(
                        port, w, h, maxPlayers, color, "host", "host-guest", "", false);
        open.add(t);
        return t;
    }

    private GrpcClientTransport client(int port, String nick, PlayerColor color)
            throws TransportException {
        return client(port, nick, nick, color, null);
    }

    private GrpcClientTransport client(
            int port, String nick, PlayerColor color, br.com.mss.tchow.net.match.MatchId matchId)
            throws TransportException {
        return client(port, nick, nick, color, matchId);
    }

    private GrpcClientTransport client(
            int port,
            String nick,
            String guestId,
            PlayerColor color,
            br.com.mss.tchow.net.match.MatchId matchId)
            throws TransportException {
        GrpcClientTransport t =
                new GrpcClientTransport("127.0.0.1", port, nick, guestId, color, matchId);
        open.add(t);
        return t;
    }

    /** Apresenta um token de sessão (ADR-0013, {@code [E4c-01]}) numa tentativa de reconexão. */
    private GrpcClientTransport client(
            int port,
            String nick,
            String guestId,
            PlayerColor color,
            br.com.mss.tchow.net.match.MatchId matchId,
            String sessionToken)
            throws TransportException {
        GrpcClientTransport t =
                new GrpcClientTransport(
                        "127.0.0.1", port, nick, guestId, color, matchId, "", sessionToken);
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
        throw new AssertionError("condição não satisfeita em 5s");
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

        boolean has(Class<? extends GameEvent> type) {
            return !of(type).isEmpty();
        }
    }

    @Test
    void doisJogadoresTrocamSnapshotJogadaRecusaEChat() throws Exception {
        int port = freePort();

        GrpcHostTransport host = host(port, 2, 1, List.of(RED, BLUE), RED);
        Collector hostEvents = new Collector();
        host.addListener(hostEvents);
        GameSnapshotDto hostSnapshot = host.connect();
        assertEquals(2, hostSnapshot.width());
        assertFalse(hostSnapshot.started());

        GrpcClientTransport client = client(port, "cli", BLUE);
        Collector clientEvents = new Collector();
        client.addListener(clientEvents);
        GameSnapshotDto clientSnapshot = client.connect();

        assertTrue(clientSnapshot.started(), "2 de 2 → partida começa");
        awaitUntil(
                () ->
                        hostEvents.has(GameEvent.PlayerJoined.class)
                                && hostEvents.has(GameEvent.MatchStarted.class));

        // RED marca a aresta de cima do quadro (0,0): não fecha nada → passa a vez
        host.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0));
        awaitUntil(() -> !clientEvents.of(GameEvent.MoveApplied.class).isEmpty());
        List<GameEvent.MoveApplied> applied = clientEvents.of(GameEvent.MoveApplied.class);
        assertEquals(1, applied.size());
        assertEquals(BLUE, applied.get(0).nextPlayer());
        assertFalse(applied.get(0).gameOver());

        // RED tenta jogar de novo, fora da vez → MoveRejected sintetizado no host
        host.submitMove(new MoveDto(RED, HORIZONTAL, 1, 0));
        awaitUntil(() -> !hostEvents.of(GameEvent.MoveRejected.class).isEmpty());
        assertTrue(
                hostEvents
                        .of(GameEvent.MoveRejected.class)
                        .get(0)
                        .reason()
                        .toLowerCase()
                        .contains("vez"));

        // chat do cliente chega no host
        client.sendChat("ola");
        awaitUntil(
                () ->
                        hostEvents.of(GameEvent.ChatPosted.class).stream()
                                .anyMatch(
                                        e ->
                                                e.message().text().equals("ola")
                                                        && e.message().color() == BLUE));
    }

    /**
     * [E4.5-05] (ADR-0015), ponta a ponta pelo gRPC de verdade: {@code CreateMatch} com {@code
     * max_players} (sem roster) + dois {@code Join} com cores fora de qualquer prefixo — prova que
     * {@code max_players}/{@code roster} vazio chegam certos pelo wire, não só na chamada direta ao
     * {@code MatchService} ({@code MatchServiceTest} já cobre a lógica em si).
     */
    @Test
    void elencoAbertoPeloWireAceitaQualquerCor() throws Exception {
        int port = freePort();

        GrpcHostTransport host = hostOpen(port, 2, 1, 2, GREEN);
        Collector hostEvents = new Collector();
        host.addListener(hostEvents);
        GameSnapshotDto hostSnapshot = host.connect();
        assertFalse(hostSnapshot.started());

        GrpcClientTransport client = client(port, "cli", PINK);
        GameSnapshotDto clientSnapshot = client.connect();

        assertTrue(clientSnapshot.started(), "2 de 2 → partida começa");
        awaitUntil(() -> hostEvents.has(GameEvent.MatchStarted.class));
    }

    @Test
    void revancheRecusadaEnquantoAPartidaNaoTerminou() throws Exception {
        int port = freePort();
        GrpcHostTransport host = host(port, 3, 3, List.of(RED, BLUE), RED);
        Collector hostEvents = new Collector();
        host.addListener(hostEvents);
        host.connect();
        GrpcClientTransport client = client(port, "cli", BLUE);
        client.connect();

        host.rematch(); // pede revanche com a partida em andamento

        awaitUntil(() -> hostEvents.has(GameEvent.RematchRejected.class));
        assertTrue(
                hostEvents
                        .of(GameEvent.RematchRejected.class)
                        .get(0)
                        .reason()
                        .toLowerCase()
                        .contains("termin"));
    }

    @Test
    void revancheReiniciaOTabuleiroERotacionaOInicio() throws Exception {
        int port = freePort();
        GrpcHostTransport host = host(port, 2, 1, List.of(RED, BLUE), RED); // host = RED
        host.connect();
        GrpcClientTransport client = client(port, "cli", BLUE);
        Collector clientEvents = new Collector();
        client.addListener(clientEvents);
        client.connect();
        awaitUntil(() -> clientEvents.has(GameEvent.MatchStarted.class));

        // sequência fixa que fecha os 2 quadros do 2x1 (BLUE vence 2 a 0)
        host.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0));
        client.submitMove(new MoveDto(BLUE, HORIZONTAL, 1, 0));
        host.submitMove(new MoveDto(RED, VERTICAL, 0, 0));
        client.submitMove(new MoveDto(BLUE, HORIZONTAL, 0, 1));
        host.submitMove(new MoveDto(RED, HORIZONTAL, 1, 1));
        client.submitMove(new MoveDto(BLUE, VERTICAL, 0, 1)); // fecha (0,0), BLUE de novo
        client.submitMove(new MoveDto(BLUE, VERTICAL, 0, 2)); // fecha (0,1), fim

        awaitUntil(
                () ->
                        clientEvents.of(GameEvent.MoveApplied.class).stream()
                                .anyMatch(GameEvent.MoveApplied::gameOver));

        host.rematch(); // pede revanche
        awaitUntil(() -> clientEvents.has(GameEvent.RematchRequested.class));
        client.respondRematch(true); // o oponente aceita

        awaitUntil(() -> clientEvents.has(GameEvent.RematchStarted.class));
        GameEvent.RematchStarted rematch = clientEvents.of(GameEvent.RematchStarted.class).get(0);
        assertTrue(rematch.snapshot().started());
        assertFalse(rematch.snapshot().finished());
        assertTrue(rematch.snapshot().markedEdges().isEmpty(), "tabuleiro zerado");
        assertEquals(BLUE, rematch.snapshot().currentPlayer(), "quem começa rotaciona");
    }

    @Test
    void desfazerEmRedeSemConsentimento() throws Exception {
        int port = freePort();
        GrpcHostTransport host = host(port, 3, 3, List.of(RED, BLUE), RED);
        Collector hostEvents = new Collector();
        host.addListener(hostEvents);
        host.connect();
        GrpcClientTransport client = client(port, "cli", BLUE);
        Collector clientEvents = new Collector();
        client.addListener(clientEvents);
        client.connect();
        awaitUntil(() -> clientEvents.has(GameEvent.MatchStarted.class));

        host.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0));
        awaitUntil(() -> clientEvents.has(GameEvent.MoveApplied.class));

        // o host desfaz — sem pedir nada; os dois recebem o estado rebobinado
        host.undo();
        awaitUntil(() -> hostEvents.has(GameEvent.HistoryChanged.class));
        awaitUntil(() -> clientEvents.has(GameEvent.HistoryChanged.class));

        GameEvent.HistoryChanged applied = hostEvents.of(GameEvent.HistoryChanged.class).get(0);
        assertTrue(applied.snapshot().markedEdges().isEmpty(), "a jogada foi desfeita");

        // RED re-joga; o BLUE joga em seguida → a janela do RED fechou
        host.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0));
        awaitUntil(() -> clientEvents.of(GameEvent.MoveApplied.class).size() == 2);
        client.submitMove(new MoveDto(BLUE, HORIZONTAL, 3, 0));
        awaitUntil(() -> clientEvents.of(GameEvent.MoveApplied.class).size() == 3);
        host.undo(); // RED tenta desfazer, mas o BLUE já jogou depois
        awaitUntil(() -> hostEvents.has(GameEvent.UndoRejected.class));
    }

    @Test
    void createMatchAbrePartidasIsoladasNoMesmoServidor() throws Exception {
        int port = freePort();

        // Partida A: o host abre e entra — fica no lobby (só RED)
        GrpcHostTransport host = host(port, 2, 1, List.of(RED, BLUE), RED);
        Collector aEvents = new Collector();
        host.addListener(aEvents);
        host.connect();

        // Partida B: criada por RPC, jogada por dois clientes até o fim
        var discovery = new GrpcDiscovery();
        br.com.mss.tchow.net.match.MatchId b =
                discovery.createMatch("127.0.0.1", port, 2, 1, List.of(RED, BLUE), false);

        GrpcClientTransport b1 = client(port, "b1", RED, b);
        GrpcClientTransport b2 = client(port, "b2", BLUE, b);
        Collector bEvents = new Collector();
        b1.addListener(bEvents);
        b1.connect();
        b2.connect();
        awaitUntil(() -> bEvents.has(GameEvent.MatchStarted.class));

        b1.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0));
        b2.submitMove(new MoveDto(BLUE, HORIZONTAL, 1, 0));
        b1.submitMove(new MoveDto(RED, VERTICAL, 0, 0));
        b2.submitMove(new MoveDto(BLUE, HORIZONTAL, 0, 1));
        b1.submitMove(new MoveDto(RED, HORIZONTAL, 1, 1));
        b2.submitMove(new MoveDto(BLUE, VERTICAL, 0, 1));
        b2.submitMove(new MoveDto(BLUE, VERTICAL, 0, 2));
        awaitUntil(
                () ->
                        bEvents.of(GameEvent.MoveApplied.class).stream()
                                .anyMatch(GameEvent.MoveApplied::gameOver));

        // A não recebeu nenhum evento de jogo de B
        assertTrue(
                aEvents.of(GameEvent.MoveApplied.class).isEmpty(),
                "eventos da partida B não vazam para a A");

        // A continua no lobby: uma jogada nela é recusada com 'aguardando'
        host.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0));
        awaitUntil(() -> aEvents.has(GameEvent.MoveRejected.class));
        assertTrue(
                aEvents.of(GameEvent.MoveRejected.class)
                        .get(0)
                        .reason()
                        .toLowerCase()
                        .contains("aguardando"));
    }

    @Test
    void joinComMatchIdInexistenteFalha() throws Exception {
        int port = freePort();
        host(port, 3, 3, List.of(RED, BLUE), RED).connect();

        GrpcClientTransport bad =
                client(port, "x", BLUE, new br.com.mss.tchow.net.match.MatchId("nao-existe"));
        assertThrows(TransportException.class, bad::connect);
    }

    @Test
    void createMatchComElencoInvalidoFalha() throws Exception {
        int port = freePort();
        host(port, 3, 3, List.of(RED, BLUE), RED).connect();

        assertThrows(
                TransportException.class,
                () ->
                        new GrpcDiscovery()
                                .createMatch("127.0.0.1", port, 3, 3, List.of(RED), false));
    }

    @Test
    void discoveryMostraElencoECoresLivres() throws Exception {
        int port = freePort();
        host(port, 4, 3, List.of(RED, BLUE, GREEN), RED).connect();

        MatchInfoDto info = new GrpcDiscovery().peek("127.0.0.1", port, false);

        assertEquals(4, info.width());
        assertEquals(3, info.height());
        assertEquals(List.of(RED, BLUE, GREEN), info.turnOrder());
        assertEquals(List.of(BLUE, GREEN), info.availableColors());
        assertFalse(info.started());
    }

    @Test
    void corEmUsoEhRecusada() throws Exception {
        int port = freePort();
        host(port, 3, 3, List.of(RED, BLUE), RED).connect();

        GrpcClientTransport clash = client(port, "x", RED);
        TransportException ex = assertThrows(TransportException.class, clash::connect);
        assertTrue(ex.getMessage().contains("em uso"));
    }

    @Test
    void assentoTravaNoPerfilQueEntrou() throws Exception {
        int port = freePort();
        GrpcHostTransport host = host(port, 3, 3, List.of(RED, BLUE), RED);
        Collector hostEvents = new Collector();
        host.addListener(hostEvents);
        host.connect();

        GrpcClientTransport ana = client(port, "Ana", "guest-ana", BLUE, null);
        Collector anaEvents = new Collector();
        ana.addListener(anaEvents);
        ana.connect();
        awaitUntil(() -> anaEvents.has(GameEvent.MatchStarted.class));

        host.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0)); // RED joga
        awaitUntil(() -> anaEvents.has(GameEvent.MoveApplied.class));

        String anaToken = ana.issuedSessionToken(); // ADR-0013 [E4c-01]: guarda pra reconectar
        ana.disconnect(); // Ana sai; o assento BLUE fica reservado a ela
        awaitUntil(() -> hostEvents.has(GameEvent.PlayerLeft.class)); // servidor processou a saída

        // outro perfil tentando BLUE na mesma partida → recusa clara
        GrpcClientTransport bia = client(port, "Bia", "guest-bia", BLUE, null);
        TransportException ex = assertThrows(TransportException.class, bia::connect);
        assertTrue(ex.getMessage().toLowerCase().contains("pertence a outro jogador"));

        // Ana volta com o mesmo guest_id e o token certo → retoma a partida de onde parou
        GrpcClientTransport anaBack = client(port, "Ana", "guest-ana", BLUE, null, anaToken);
        GameSnapshotDto resumed = anaBack.connect();
        assertEquals(1, resumed.markedEdges().size(), "a jogada do RED continua no tabuleiro");
    }

    @Test
    void reconectarComTokenErradoEhRecusado() throws Exception {
        int port = freePort();
        GrpcHostTransport host = host(port, 3, 3, List.of(RED, BLUE), RED);
        Collector hostEvents = new Collector();
        host.addListener(hostEvents);
        host.connect();

        GrpcClientTransport ana = client(port, "Ana", "guest-ana", BLUE, null);
        Collector anaEvents = new Collector();
        ana.addListener(anaEvents);
        ana.connect();
        awaitUntil(() -> anaEvents.has(GameEvent.MatchStarted.class));

        ana.disconnect();
        awaitUntil(() -> hostEvents.has(GameEvent.PlayerLeft.class));

        // mesmo guest_id, mas token errado (ADR-0013, [E4c-01]) → recusa clara
        GrpcClientTransport anaBack = client(port, "Ana", "guest-ana", BLUE, null, "token-chutado");
        TransportException ex = assertThrows(TransportException.class, anaBack::connect);
        assertTrue(ex.getMessage().toLowerCase().contains("token"));
    }

    /**
     * {@code [E4c-02]}: o {@code session_token} também é aceito pela metadata ({@code
     * authorization: Bearer <token>}), não só pelo corpo do {@code JoinRequest}. Prova com um stub
     * cru: {@code JoinRequest} sem {@code session_token}, credencial só no header.
     */
    @Test
    void sessionTokenAceitoViaMetadataAuthorization() throws Exception {
        int port = freePort();
        GrpcHostTransport host = host(port, 3, 3, List.of(RED, BLUE), RED);
        Collector hostEvents = new Collector();
        host.addListener(hostEvents);
        host.connect();

        GrpcClientTransport ana = client(port, "Ana", "guest-ana", BLUE, null);
        Collector anaEvents = new Collector();
        ana.addListener(anaEvents);
        ana.connect();
        awaitUntil(() -> anaEvents.has(GameEvent.MatchStarted.class));
        String anaToken = ana.issuedSessionToken();
        assertFalse(anaToken.isEmpty());
        ana.disconnect();
        awaitUntil(() -> hostEvents.has(GameEvent.PlayerLeft.class));

        // token errado só no header → recusa clara (o assento BLUE continua vago e reservado)
        StatusRuntimeException erro =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> joinComCredencialNaMetadata(port, BLUE, "token-chutado"));
        assertTrue(erro.getMessage().toLowerCase().contains("token"));

        // corpo sem token, credencial certa só no header → reconecta
        JoinResponse ok = joinComCredencialNaMetadata(port, BLUE, anaToken);
        assertEquals(GameEventProto.KindCase.INITIAL_SNAPSHOT, ok.getEvent().getKindCase());
    }

    /** {@code Join} cru sem {@code session_token} no corpo, só o header {@code authorization}. */
    private JoinResponse joinComCredencialNaMetadata(int port, PlayerColor color, String bearer)
            throws Exception {
        ManagedChannel channel =
                Grpc.newChannelBuilderForAddress(
                                "127.0.0.1", port, InsecureChannelCredentials.create())
                        .build();
        rawChannels.add(channel);

        Metadata md = new Metadata();
        md.put(
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + bearer);
        GameServiceGrpc.GameServiceStub stub =
                GameServiceGrpc.newStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md));

        BlockingQueue<Object> first = new ArrayBlockingQueue<>(1);
        stub.join(
                JoinRequest.newBuilder()
                        .setNick("Ana")
                        .setGuestId("guest-ana")
                        .setColor(ProtoMapper.toProto(color))
                        .build(),
                new StreamObserver<JoinResponse>() {
                    @Override
                    public void onNext(JoinResponse value) {
                        first.offer(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        first.offer(t);
                    }

                    @Override
                    public void onCompleted() {}
                });

        Object result = first.poll(5, TimeUnit.SECONDS);
        // Já temos a 1ª mensagem: encerra o stream (libera o assento server-side) e devolve.
        channel.shutdownNow();
        if (result instanceof StatusRuntimeException sre) {
            throw sre;
        }
        if (result instanceof Throwable t) {
            throw new AssertionError(t);
        }
        if (result == null) {
            throw new AssertionError("sem resposta do Join em 5s");
        }
        return (JoinResponse) result;
    }

    @Test
    void desfazeresDaRodadaSobrevivemAReconexao() throws Exception {
        int port = freePort();
        GrpcHostTransport host = host(port, 3, 3, List.of(RED, BLUE), RED);
        Collector hostEvents = new Collector();
        host.addListener(hostEvents);
        host.connect();

        GrpcClientTransport ana = client(port, "Ana", "guest-ana", BLUE, null);
        Collector anaEvents = new Collector();
        ana.addListener(anaEvents);
        ana.connect();
        awaitUntil(() -> anaEvents.has(GameEvent.MatchStarted.class));

        host.submitMove(new MoveDto(RED, HORIZONTAL, 0, 0));
        awaitUntil(() -> anaEvents.has(GameEvent.MoveApplied.class));
        ana.submitMove(new MoveDto(BLUE, HORIZONTAL, 3, 0)); // BLUE joga por último
        awaitUntil(() -> anaEvents.of(GameEvent.MoveApplied.class).size() == 2);

        ana.undo(); // BLUE desfaz a própria jogada → 1 de 2 usados na rodada
        awaitUntil(() -> anaEvents.has(GameEvent.HistoryChanged.class));

        String anaToken = ana.issuedSessionToken(); // ADR-0013 [E4c-01]
        ana.disconnect();
        awaitUntil(() -> hostEvents.has(GameEvent.PlayerLeft.class));

        GrpcClientTransport anaBack = client(port, "Ana", "guest-ana", BLUE, null, anaToken);
        GameSnapshotDto resumed = anaBack.connect();
        assertEquals(
                1,
                resumed.undosUsedThisRound().getOrDefault(BLUE, 0),
                "o desfazer que a Ana já usou continua contando após reconectar");
    }
}
