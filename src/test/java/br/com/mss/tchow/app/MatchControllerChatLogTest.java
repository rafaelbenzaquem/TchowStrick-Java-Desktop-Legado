package br.com.mss.tchow.app;

import static br.com.mss.tchow.domain.EdgeOrientation.HORIZONTAL;
import static br.com.mss.tchow.domain.PlayerColor.BLUE;
import static br.com.mss.tchow.domain.PlayerColor.RED;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.ai.RandomStrategy;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import br.com.mss.tchow.net.LocalTransport;
import br.com.mss.tchow.ui.BoardView;
import br.com.mss.tchow.ui.ChatPanel;
import br.com.mss.tchow.ui.PlayersPanel;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** O chat vira um log de eventos da partida ([E3-15]) — verificado no modo local. */
class MatchControllerChatLogTest {

    private static final class Fixture {
        LocalTransport transport;
        MatchController controller;
        ChatPanel chat;
    }

    private static Fixture localMatch(long seed) throws Exception {
        AtomicReference<Fixture> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    Fixture f = new Fixture();
                    f.transport =
                            new LocalTransport(
                                    3,
                                    3,
                                    List.of(RED, BLUE),
                                    RED,
                                    "eu",
                                    List.of(
                                            new LocalTransport.Bot(
                                                    BLUE, "IA", new RandomStrategy())),
                                    seed);
                    GameSnapshotDto snap;
                    try {
                        snap = f.transport.connect();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    f.chat = new ChatPanel();
                    f.controller =
                            new MatchController(
                                    f.transport,
                                    new BoardView(3, 3),
                                    new PlayersPanel(),
                                    f.chat,
                                    new JLabel(),
                                    b -> {},
                                    (a, b) -> {},
                                    c -> false,
                                    null, // modo local: sem carteira
                                    (a, b) -> {},
                                    snap);
                    ref.set(f);
                });
        return ref.get();
    }

    private static void drain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {});
    }

    @Test
    void desfazerERefazerGeramLinhaDeSistema() throws Exception {
        Fixture f = localMatch(5L);

        SwingUtilities.invokeAndWait(
                () -> f.transport.submitMove(MoveDto.of(RED, new Edge(HORIZONTAL, 0, 0))));
        drain();

        SwingUtilities.invokeAndWait(f.controller::requestUndo);
        drain();
        assertTrue(f.chat.transcript().contains("• você desfez sua jogada"), f.chat.transcript());

        SwingUtilities.invokeAndWait(f.controller::requestRedo);
        drain();
        assertTrue(f.chat.transcript().contains("• você refez sua jogada"), f.chat.transcript());
    }

    @Test
    void fimDePartidaGeraLinhaDeResultado() throws Exception {
        Fixture f = localMatch(3L);

        // conduz o assento humano por jogadas aleatórias válidas até acabar
        var rng = new java.util.Random(9L);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        boolean[] done = {false};
        while (!done[0] && System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(
                    () -> {
                        var view = f.transport.currentView();
                        if (view.isFinished()) {
                            done[0] = true;
                            return;
                        }
                        if (view.currentPlayer() == RED) {
                            var free = view.freeEdges();
                            f.transport.submitMove(
                                    MoveDto.of(RED, free.get(rng.nextInt(free.size()))));
                        }
                    });
            drain();
        }

        String transcript = f.chat.transcript();
        assertTrue(
                transcript.contains("• você venceu")
                        || transcript.contains("• você perdeu")
                        || transcript.contains("• empate"),
                transcript);
    }
}
