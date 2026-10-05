package br.com.mss.tchow.save;

import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzing do decodificador ({@code docs/VALIDACAO.md §1.11}). Invariante: {@link
 * SavegameCodec#decode} ou devolve um {@link Savegame}, ou lança {@link SavegameFormatException} —
 * nunca qualquer outra coisa (crash, {@code RuntimeException} não tratada, OOM…).
 *
 * <p>Em {@code mvn test} roda o corpus semente de {@code
 * src/test/resources/br/com/mss/tchow/save/SavegameCodecFuzzTestInputs/} (regressão, rápido).
 * Fuzzing de verdade: {@code JAZZER_FUZZ=1} + {@code -Djazzer.instrument=br.com.mss.tchow.**}.
 */
class SavegameCodecFuzzTest {

    @FuzzTest(maxDuration = "10s")
    void decodeNuncaEstoura(byte[] data) throws Exception {
        try {
            SavegameCodec.decode(data);
        } catch (SavegameFormatException expected) {
            // é o único jeito legítimo de falhar
        }
    }
}
