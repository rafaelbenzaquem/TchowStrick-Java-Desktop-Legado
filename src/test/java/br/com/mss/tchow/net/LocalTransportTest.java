package br.com.mss.tchow.net;

import static br.com.mss.tchow.domain.EdgeOrientation.HORIZONTAL;
import static br.com.mss.tchow.domain.PlayerColor.BLUE;
import static br.com.mss.tchow.domain.PlayerColor.GREEN;
import static br.com.mss.tchow.domain.PlayerColor.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.GameView;
import br.com.mss.tchow.domain.ai.GreedyStrategy;
import br.com.mss.tchow.domain.ai.RandomStrategy;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Modo local: o assento humano é conduzido pelo teste; os demais são bots. */
class LocalTransportTest {

    @Test
    void humanoMaisUmBotJogamUmaPartidaCompleta() throws Exception {
        LocalTransport transport =
                new LocalTransport(
                        3,
                        3,
                        List.of(RED, BLUE),
                        RED,
                        "eu",
                        List.of(new LocalTransport.Bot(BLUE, "IA", new GreedyStrategy())),
                        42L);

        List<GameEvent> events = Collections.synchronizedList(new java.util.ArrayList<>());
        transport.addListener(events::add);

        GameSnapshotDto snapshot = connectOnEdt(transport);
        assertTrue(snapshot.started(), "2 de 2 assentos → partida começa");

        playAsHumanUntilFinished(transport, RED, 1L);

        assertTrue(currentViewOnEdt(transport).isFinished(), "a partida deve terminar");
        assertTrue(
                events.stream().anyMatch(e -> e instanceof GameEvent.MoveApplied m && m.gameOver()),
                "deve ter chegado um MoveApplied de fim de jogo");

        transport.disconnect();
    }

    @Test
    void humanoContraDoisBots() throws Exception {
        LocalTransport transport =
                new LocalTransport(
                        3,
                        3,
                        List.of(RED, BLUE, GREEN),
                        RED,
                        "eu",
                        List.of(
                                new LocalTransport.Bot(BLUE, "IA-1", new RandomStrategy()),
                                new LocalTransport.Bot(GREEN, "IA-2", new GreedyStrategy())),
                        7L);
        connectOnEdt(transport);

        playAsHumanUntilFinished(transport, RED, 3L);

        assertTrue(currentViewOnEdt(transport).isFinished());
        transport.disconnect();
    }

    @Test
    void revancheReiniciaAPartidaContraOBot() throws Exception {
        LocalTransport transport =
                new LocalTransport(
                        2,
                        2,
                        List.of(RED, BLUE),
                        RED,
                        "eu",
                        List.of(new LocalTransport.Bot(BLUE, "IA", new GreedyStrategy())),
                        9L);
        List<GameEvent> events = Collections.synchronizedList(new java.util.ArrayList<>());
        transport.addListener(events::add);

        connectOnEdt(transport);
        playAsHumanUntilFinished(transport, RED, 4L);
        assertTrue(currentViewOnEdt(transport).isFinished());

        SwingUtilities.invokeAndWait(transport::rematch);
        SwingUtilities.invokeAndWait(() -> {}); // drena a 1ª jogada do bot, se houve

        assertFalse(currentViewOnEdt(transport).isFinished(), "revanche recomeça a partida");
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.RematchStarted));

        transport.disconnect();
    }

    @Test
    void desfazerERefazerNoModoLocal() throws Exception {
        LocalTransport transport =
                new LocalTransport(
                        3,
                        3,
                        List.of(RED, BLUE),
                        RED,
                        "eu",
                        List.of(new LocalTransport.Bot(BLUE, "IA", new GreedyStrategy())),
                        5L);
        List<GameEvent> events = Collections.synchronizedList(new java.util.ArrayList<>());
        transport.addListener(events::add);
        connectOnEdt(transport);

        // RED joga; o bot responde
        SwingUtilities.invokeAndWait(
                () -> transport.submitMove(MoveDto.of(RED, new Edge(HORIZONTAL, 0, 0))));
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {});

        int markedAfterMoves = markedEdges(currentViewOnEdt(transport));
        assertTrue(markedAfterMoves >= 1);
        assertTrue(callOnEdt(transport::canUndo));

        SwingUtilities.invokeAndWait(transport::undo);
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(0, markedEdges(currentViewOnEdt(transport)), "desfazer volta ao começo");
        assertTrue(callOnEdt(transport::canRedo));
        assertTrue(
                events.stream().anyMatch(e -> e instanceof GameEvent.HistoryChanged),
                "chega um HistoryChanged");

        SwingUtilities.invokeAndWait(transport::redo);
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(
                markedAfterMoves,
                markedEdges(currentViewOnEdt(transport)),
                "refazer restaura o turno");
        assertFalse(callOnEdt(transport::canRedo));

        transport.disconnect();
    }

    private static int markedEdges(GameView v) {
        int total = 2 * v.width() * v.height() + v.width() + v.height();
        return total - v.freeEdges().size();
    }

    private static boolean callOnEdt(java.util.function.BooleanSupplier s) throws Exception {
        AtomicBoolean r = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> r.set(s.getAsBoolean()));
        return r.get();
    }

    // --- helpers ---------------------------------------------------------

    private static GameSnapshotDto connectOnEdt(LocalTransport transport) throws Exception {
        AtomicReference<GameSnapshotDto> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        ref.set(transport.connect());
                    } catch (TransportException e) {
                        throw new RuntimeException(e);
                    }
                });
        return ref.get();
    }

    private static GameView currentViewOnEdt(LocalTransport transport) throws Exception {
        AtomicReference<GameView> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(transport.currentView()));
        return ref.get();
    }

    private static void playAsHumanUntilFinished(
            LocalTransport transport, br.com.mss.tchow.domain.PlayerColor me, long seed)
            throws Exception {
        Random rng = new Random(seed);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            AtomicBoolean finished = new AtomicBoolean();
            SwingUtilities.invokeAndWait(
                    () -> {
                        GameView v = transport.currentView();
                        if (v.isFinished()) {
                            finished.set(true);
                            return;
                        }
                        if (v.currentPlayer() == me) {
                            List<Edge> free = v.freeEdges();
                            transport.submitMove(
                                    MoveDto.of(me, free.get(rng.nextInt(free.size()))));
                        }
                    });
            if (finished.get()) {
                return;
            }
            SwingUtilities.invokeAndWait(() -> {}); // drena as jogadas dos bots
        }
        throw new AssertionError("a partida não terminou em 15 s");
    }
}
