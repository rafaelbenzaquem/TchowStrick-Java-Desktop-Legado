package br.com.mss.tchow.save;

import static br.com.mss.tchow.domain.PlayerColor.BLUE;
import static br.com.mss.tchow.domain.PlayerColor.GREEN;
import static br.com.mss.tchow.domain.PlayerColor.RED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import br.com.mss.tchow.domain.GameEngine;
import br.com.mss.tchow.domain.Move;
import br.com.mss.tchow.domain.ai.RandomStrategy;
import br.com.mss.tchow.domain.history.BoardSpec;
import br.com.mss.tchow.domain.history.MoveLog;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class SavegameCodecPropertyTest {

    private static Savegame randomGame(int width, int height, int players, long seed) {
        BoardSpec spec =
                new BoardSpec(
                        width,
                        height,
                        players == 3 ? List.of(RED, BLUE, GREEN) : List.of(RED, BLUE));
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

    @Property(tries = 200)
    void roundTripByteAByteParaPartidasAleatorias(
            @ForAll @IntRange(min = 2, max = 6) int width,
            @ForAll @IntRange(min = 2, max = 6) int height,
            @ForAll @IntRange(min = 2, max = 3) int players,
            @ForAll long seed)
            throws Exception {

        Savegame original = randomGame(width, height, players, seed);
        byte[] once = SavegameCodec.encode(original);
        byte[] twice = SavegameCodec.encode(SavegameCodec.decode(once));
        assertArrayEquals(once, twice);
    }

    @Property(tries = 2000)
    void decodeNuncaQuebraComBytesArbitrarios(@ForAll @Size(max = 256) byte[] bytes) {
        try {
            assertNotNull(SavegameCodec.decode(bytes));
        } catch (SavegameFormatException expected) {
            // é o único jeito de o decode falhar
        }
    }
}
