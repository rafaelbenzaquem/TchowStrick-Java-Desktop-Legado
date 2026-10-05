package br.com.mss.tchow.net.grpc;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.ColorUnavailableException;
import br.com.mss.tchow.net.Dtos.ChatMessageDto;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import br.com.mss.tchow.net.GameEvent;
import br.com.mss.tchow.net.GameEventListener;
import br.com.mss.tchow.net.GameTransport;
import br.com.mss.tchow.net.RejectedMoveException;
import br.com.mss.tchow.net.TransportException;
import br.com.mss.tchow.net.discovery.ServerDiscoveryResponder;
import br.com.mss.tchow.net.match.MatchId;
import br.com.mss.tchow.net.match.MatchRegistry;
import br.com.mss.tchow.net.match.MatchService;
import io.grpc.Server;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transporte do <b>host</b> — o modo {@code --embedded-server} ([E4a-02], ADR-0002): sobe o mesmo
 * {@link Server} gRPC que o {@code ServerMain} standalone (via {@link GrpcServerFactory}), cria a
 * partida num {@link MatchRegistry} local e entra nela chamando o {@link MatchService} direto
 * (mesmo processo, sem round-trip de rede para o próprio host). Os eventos chegam pelo sink local e
 * são entregues na EDT.
 *
 * <p>Logging (ADR-0011): subir/derrubar o servidor embutido em INFO.
 */
public final class GrpcHostTransport implements GameTransport {

    private static final Logger logger = LoggerFactory.getLogger(GrpcHostTransport.class);

    private final int port;
    private final PlayerColor color;
    private final String nick;
    private final String guestId;
    private final String password;
    private final MatchRegistry registry = new MatchRegistry();
    private final MatchId matchId;
    private final Server server;
    private final ServerDiscoveryResponder discoveryResponder;
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    public GrpcHostTransport(
            int port,
            int width,
            int height,
            List<PlayerColor> turnOrder,
            PlayerColor color,
            String nick,
            String guestId)
            throws TransportException {
        this(port, width, height, turnOrder, color, nick, guestId, "");
    }

    /** {@code password} não-vazio tranca a partida embutida (ADR-0012). */
    public GrpcHostTransport(
            int port,
            int width,
            int height,
            List<PlayerColor> turnOrder,
            PlayerColor color,
            String nick,
            String guestId,
            String password)
            throws TransportException {
        this(port, width, height, turnOrder, color, nick, guestId, password, true);
    }

    /**
     * {@code discoverable} = {@code false} desliga a resposta a buscas de servidor na rede local
     * ([E4.5-03], ADR-0014) — quem não quer que a própria máquina apareça pra outros na LAN.
     */
    public GrpcHostTransport(
            int port,
            int width,
            int height,
            List<PlayerColor> turnOrder,
            PlayerColor color,
            String nick,
            String guestId,
            String password,
            boolean discoverable)
            throws TransportException {
        this.port = port;
        this.color = color;
        this.nick = nick;
        this.guestId = guestId;
        this.password = password == null ? "" : password;
        this.matchId = registry.open(width, height, turnOrder, this.password).id();
        try {
            this.server = GrpcServerFactory.start(port, registry);
            logger.info("servidor embutido no ar na porta {} (partida {})", port, matchId);
        } catch (IOException e) {
            logger.warn(
                    "falha ao iniciar o servidor embutido na porta {}: {}", port, e.getMessage());
            throw new TransportException("falha ao iniciar o servidor na porta " + port, e);
        }
        this.discoveryResponder = discoverable ? startDiscovery(nick, port) : null;
    }

    /**
     * Elenco aberto ([E4.5-05], ADR-0015): {@code maxPlayers} é só a quantidade — nenhuma cor
     * pré-declarada. O próprio host reserva {@code color} chamando {@link #connect()} logo em
     * seguida, exatamente como qualquer outro jogador que entrar depois.
     */
    public GrpcHostTransport(
            int port,
            int width,
            int height,
            int maxPlayers,
            PlayerColor color,
            String nick,
            String guestId,
            String password,
            boolean discoverable)
            throws TransportException {
        this.port = port;
        this.color = color;
        this.nick = nick;
        this.guestId = guestId;
        this.password = password == null ? "" : password;
        this.matchId = registry.open(width, height, maxPlayers, this.password).id();
        try {
            this.server = GrpcServerFactory.start(port, registry);
            logger.info("servidor embutido no ar na porta {} (partida {})", port, matchId);
        } catch (IOException e) {
            logger.warn(
                    "falha ao iniciar o servidor embutido na porta {}: {}", port, e.getMessage());
            throw new TransportException("falha ao iniciar o servidor na porta " + port, e);
        }
        this.discoveryResponder = discoverable ? startDiscovery(nick, port) : null;
    }

    private static ServerDiscoveryResponder startDiscovery(String nick, int port) {
        try {
            ServerDiscoveryResponder responder =
                    new ServerDiscoveryResponder("Partida de " + nick, port);
            responder.start();
            return responder;
        } catch (IOException e) {
            logger.warn(
                    "não foi possível subir a descoberta de LAN ({}) — a partida segue no ar"
                            + " normalmente, só sem esse atalho",
                    e.getMessage());
            return null;
        }
    }

    private MatchService match() {
        return registry.get(matchId).service();
    }

    @Override
    public GameSnapshotDto connect() throws TransportException {
        try {
            return match().join(guestId, nick, color, this::deliver, password);
        } catch (ColorUnavailableException e) {
            throw new TransportException(e.getMessage(), e);
        }
    }

    @Override
    public void submitMove(MoveDto move) {
        try {
            match().submitMove(move);
        } catch (RejectedMoveException e) {
            deliver(new GameEvent.MoveRejected(e.getMessage()));
        }
    }

    @Override
    public void sendChat(String text) {
        match().sendChat(new ChatMessageDto(color, nick, text));
    }

    @Override
    public void rematch() {
        match().requestRematch(color); // rede: o desfecho vem como RematchStarted/RematchRejected
    }

    @Override
    public void respondRematch(boolean accept) {
        match().respondRematch(color, accept);
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
        return true; // o servidor valida; recusa vem como UndoRejected
    }

    @Override
    public void undo() {
        match().undoLastMove(color);
    }

    @Override
    public void disconnect() {
        logger.info("derrubando o servidor embutido na porta {}", port);
        try {
            match().leave(color);
        } catch (RuntimeException ignored) {
            // partida já coletada/fechada — segue para derrubar o servidor
        }
        if (discoveryResponder != null) {
            discoveryResponder.close();
        }
        registry.close(matchId);
        server.shutdown();
        try {
            server.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        server.shutdownNow();
    }

    @Override
    public void addListener(GameEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public PlayerColor localColor() {
        return color;
    }

    private void deliver(GameEvent event) {
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onEvent(event)));
    }
}
