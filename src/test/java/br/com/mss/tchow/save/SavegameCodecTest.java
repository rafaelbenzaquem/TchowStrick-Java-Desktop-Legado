package br.com.mss.tchow.save;

import static br.com.mss.tchow.domain.EdgeOrientation.HORIZONTAL;
import static br.com.mss.tchow.domain.PlayerColor.BLUE;
import static br.com.mss.tchow.domain.PlayerColor.RED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.GameEngine;
import br.com.mss.tchow.domain.Move;
import br.com.mss.tchow.domain.ai.RandomStrategy;
import br.com.mss.tchow.domain.history.BoardSpec;
import br.com.mss.tchow.domain.history.MoveLog;
import br.com.mss.tchow.save.proto.SaveColor;
import br.com.mss.tchow.save.proto.SavedMove;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SavegameCodecTest {

    private static Savegame sampleGame(long seed) {
        BoardSpec spec = new BoardSpec(3, 3, List.of(RED, BLUE));
        GameEngine engine = spec.newEngine();
        MoveLog log = MoveLog.empty();
        Random rng = new Random(seed);
        RandomStrategy strategy = new RandomStrategy();
        while (!engine.isFinished()) {
            Move move = strategy.chooseMove(engine, rng);
            engine.applyMove(move);
            log = log.append(move);
        }
        return Savegame.of(spec, log, SaveMeta.of(Instant.parse("2026-08-30T12:00:00Z"), "1.1.0"));
    }

    @Test
    void roundTripPreservaTudo() throws Exception {
        Savegame original = sampleGame(7L);

        Savegame back = SavegameCodec.decode(SavegameCodec.encode(original));

        assertEquals(original.formatVersion(), back.formatVersion());
        assertEquals(original.boardSpec(), back.boardSpec());
        assertEquals(original.moveLog(), back.moveLog());
        assertEquals(original.meta(), back.meta());
        // e o replay chega ao mesmo estado
        assertEquals(original.replay().outcome(), back.replay().outcome());
    }

    @Test
    void preservaCorDoHumanoENivelDaIa() throws Exception {
        BoardSpec spec = new BoardSpec(2, 2, List.of(RED, BLUE));
        Savegame sg = Savegame.of(spec, MoveLog.empty(), SaveMeta.now("1.1.0", BLUE, "MEDIUM"));

        SaveMeta back = SavegameCodec.decode(SavegameCodec.encode(sg)).meta();

        assertEquals(BLUE, back.humanColor());
        assertEquals("MEDIUM", back.aiLevel());
    }

    @Test
    void semDadosDeReaberturaVoltaNuloEVazio() throws Exception {
        BoardSpec spec = new BoardSpec(2, 2, List.of(RED, BLUE));
        Savegame sg = Savegame.of(spec, MoveLog.empty(), SaveMeta.of(null, "1.1.0"));

        SaveMeta back = SavegameCodec.decode(SavegameCodec.encode(sg)).meta();

        assertNull(back.humanColor());
        assertEquals("", back.aiLevel());
    }

    @Test
    void roundTripEhByteAByte() throws Exception {
        for (long seed = 0; seed < 20; seed++) {
            byte[] once = SavegameCodec.encode(sampleGame(seed));
            byte[] twice = SavegameCodec.encode(SavegameCodec.decode(once));
            assertArrayEquals(once, twice, "seed " + seed);
        }
    }

    @Test
    void metaVaziaEhAceita() throws Exception {
        BoardSpec spec = new BoardSpec(2, 2, List.of(RED, BLUE));
        Savegame sg = Savegame.of(spec, MoveLog.empty(), SaveMeta.of(null, ""));

        Savegame back = SavegameCodec.decode(SavegameCodec.encode(sg));
        assertNull(back.meta().createdAt());
        assertEquals("", back.meta().appVersion());
    }

    @Test
    void versaoDesconhecidaEhRecusada() {
        var proto =
                br.com.mss.tchow.save.proto.Savegame.newBuilder()
                        .setFormatVersion(99)
                        .setBoardWidth(3)
                        .setBoardHeight(3)
                        .addTurnOrder(SaveColor.SAVE_COLOR_RED)
                        .addTurnOrder(SaveColor.SAVE_COLOR_BLUE)
                        .build();

        SavegameFormatException ex =
                assertThrows(
                        SavegameFormatException.class,
                        () -> SavegameCodec.decode(proto.toByteArray()));
        assertTrue(ex.getMessage().contains("não suportada"), ex.getMessage());
    }

    @Test
    void bytesCorrompidosViramSavegameFormatException() {
        assertThrows(
                SavegameFormatException.class,
                () -> SavegameCodec.decode(new byte[] {0x7f, 0x41, 0x13, (byte) 0xff, 0x02}));
        assertThrows(SavegameFormatException.class, () -> SavegameCodec.decode(new byte[0]));
    }

    @Test
    void corInvalidaNoSaveEhRecusada() {
        var proto =
                br.com.mss.tchow.save.proto.Savegame.newBuilder()
                        .setFormatVersion(1)
                        .setBoardWidth(3)
                        .setBoardHeight(3)
                        .addTurnOrder(SaveColor.SAVE_COLOR_RED)
                        .addTurnOrder(SaveColor.SAVE_COLOR_UNSPECIFIED)
                        .build();

        assertThrows(
                SavegameFormatException.class, () -> SavegameCodec.decode(proto.toByteArray()));
    }

    @Test
    void coordenadaNegativaEhRecusada() {
        var proto =
                br.com.mss.tchow.save.proto.Savegame.newBuilder()
                        .setFormatVersion(1)
                        .setBoardWidth(3)
                        .setBoardHeight(3)
                        .addTurnOrder(SaveColor.SAVE_COLOR_RED)
                        .addTurnOrder(SaveColor.SAVE_COLOR_BLUE)
                        .addMoves(
                                SavedMove.newBuilder()
                                        .setPlayer(SaveColor.SAVE_COLOR_RED)
                                        .setOrientation(
                                                br.com.mss.tchow.save.proto.SaveOrientation
                                                        .SAVE_ORIENTATION_HORIZONTAL)
                                        .setRow(-1)
                                        .setCol(0))
                        .build();

        assertThrows(
                SavegameFormatException.class, () -> SavegameCodec.decode(proto.toByteArray()));
    }

    @Test
    void tabuleiroDegeneradoEhRecusado() {
        var proto =
                br.com.mss.tchow.save.proto.Savegame.newBuilder()
                        .setFormatVersion(1)
                        .setBoardWidth(0)
                        .setBoardHeight(3)
                        .addTurnOrder(SaveColor.SAVE_COLOR_RED)
                        .build();

        assertThrows(
                SavegameFormatException.class, () -> SavegameCodec.decode(proto.toByteArray()));
    }

    @Test
    void logInconsistenteSoFalhaNoReplay() throws Exception {
        // decode aceita a estrutura; a ilegalidade das jogadas é problema do GameReducer
        BoardSpec spec = new BoardSpec(2, 2, List.of(RED, BLUE));
        Edge same = new Edge(HORIZONTAL, 0, 0);
        Savegame sg =
                Savegame.of(
                        spec,
                        MoveLog.of(List.of(new Move(RED, same), new Move(BLUE, same))),
                        SaveMeta.of(null, ""));

        Savegame back = SavegameCodec.decode(SavegameCodec.encode(sg));
        assertEquals(2, back.moveLog().size());
        assertThrows(br.com.mss.tchow.domain.InvalidMoveException.class, back::replay);
    }
}
