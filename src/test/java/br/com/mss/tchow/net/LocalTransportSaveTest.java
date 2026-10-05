package br.com.mss.tchow.net;

import static br.com.mss.tchow.domain.EdgeOrientation.HORIZONTAL;
import static br.com.mss.tchow.domain.PlayerColor.BLUE;
import static br.com.mss.tchow.domain.PlayerColor.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.Move;
import br.com.mss.tchow.domain.ai.GreedyStrategy;
import br.com.mss.tchow.domain.history.MoveLog;
import br.com.mss.tchow.net.Dtos.MoveDto;
import br.com.mss.tchow.save.SaveMeta;
import br.com.mss.tchow.save.Savegame;
import br.com.mss.tchow.save.SavegameCodec;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Salvar o modo local em bytes e reabrir continuando exatamente da mesma posição ([E3-09]). */
class LocalTransportSaveTest {

    private static LocalTransport connectLocal(long seed, MoveLog history) throws Exception {
        LocalTransport t =
                new LocalTransport(
                        3,
                        3,
                        List.of(RED, BLUE),
                        RED,
                        "eu",
                        List.of(new LocalTransport.Bot(BLUE, "IA", new GreedyStrategy())),
                        seed,
                        history);
        AtomicReference<TransportException> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        t.connect();
                    } catch (TransportException e) {
                        err.set(e);
                    }
                });
        if (err.get() != null) {
            throw err.get();
        }
        return t;
    }

    private static SaveMaterial materialOf(LocalTransport t) throws Exception {
        AtomicReference<SaveMaterial> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(t.saveMaterial().orElseThrow()));
        return ref.get();
    }

    private static br.com.mss.tchow.net.Dtos.GameSnapshotDto connectSnapshot(
            long seed, MoveLog history) throws Exception {
        LocalTransport t =
                new LocalTransport(
                        3,
                        3,
                        List.of(RED, BLUE),
                        RED,
                        "eu",
                        List.of(new LocalTransport.Bot(BLUE, "IA", new GreedyStrategy())),
                        seed,
                        history);
        AtomicReference<br.com.mss.tchow.net.Dtos.GameSnapshotDto> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        ref.set(t.connect());
                    } catch (TransportException e) {
                        throw new RuntimeException(e);
                    }
                });
        t.disconnect();
        return ref.get();
    }

    @Test
    void salvaEReabreNaMesmaPosicao() throws Exception {
        LocalTransport original = connectLocal(11L, MoveLog.empty());

        // RED joga uma linha que não fecha quadro; o bot responde
        SwingUtilities.invokeAndWait(
                () -> original.submitMove(MoveDto.of(RED, new Edge(HORIZONTAL, 0, 0))));
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {});

        SaveMaterial mat = materialOf(original);
        assertFalse(mat.log().moves().isEmpty(), "há jogadas para salvar");

        byte[] bytes =
                SavegameCodec.encode(Savegame.of(mat.spec(), mat.log(), SaveMeta.now("test")));
        Savegame decoded = SavegameCodec.decode(bytes);

        LocalTransport reopened = connectLocal(22L, decoded.moveLog());
        SaveMaterial reMat = materialOf(reopened);

        assertEquals(
                mat.log().moves(), reMat.log().moves(), "o histórico reaberto é idêntico ao salvo");
        assertEquals(mat.spec().turnOrder(), reMat.spec().turnOrder(), "mesma ordem de turno");

        original.disconnect();
        reopened.disconnect();
    }

    @Test
    void oSnapshotDaReaberturaJaVemComOTabuleiro() throws Exception {
        // joga algumas linhas, salva o log, reabre: o snapshot inicial NÃO pode vir vazio
        LocalTransport t = connectLocal(30L, MoveLog.empty());
        SwingUtilities.invokeAndWait(
                () -> t.submitMove(MoveDto.of(RED, new Edge(HORIZONTAL, 0, 0))));
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {});
        MoveLog log = materialOf(t).log();
        t.disconnect();
        assertFalse(log.moves().isEmpty());

        var snapshot = connectSnapshot(31L, log);

        assertFalse(
                snapshot.markedEdges().isEmpty(),
                "o snapshot devolvido pelo connect já traz o tabuleiro do save");
    }

    @Test
    void logIlegalNoLoadViraTransportException() {
        MoveLog bad =
                MoveLog.of(
                        List.of(
                                new Move(RED, new Edge(HORIZONTAL, 0, 0)),
                                new Move(BLUE, new Edge(HORIZONTAL, 0, 0)))); // aresta repetida
        TransportException ex = assertThrows(TransportException.class, () -> connectLocal(1L, bad));
        assertTrue(ex.getMessage().toLowerCase().contains("save inválido"));
    }
}
