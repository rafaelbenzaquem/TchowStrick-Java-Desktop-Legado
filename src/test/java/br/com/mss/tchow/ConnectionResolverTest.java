package br.com.mss.tchow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.app.ServerChoiceStore;
import br.com.mss.tchow.net.NetworkConfig;
import br.com.mss.tchow.net.config.ServerDirectory;
import br.com.mss.tchow.net.config.ServerPreset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectionResolverTest {

    /** Fake em memória — sem tocar {@code Preferences} de verdade. */
    private static final class FakeServerChoiceStore implements ServerChoiceStore {
        private Optional<ServerPreset> saved = Optional.empty();

        @Override
        public Optional<ServerPreset> lastChoice() {
            return saved;
        }

        @Override
        public void remember(ServerPreset preset) {
            saved = Optional.of(preset);
        }
    }

    private static final ServerChoiceStore SEM_ESCOLHA_SALVA = new FakeServerChoiceStore();

    @Test
    void cliExplicitaTemPrioridadeSobreEscolhaSalvaEPresetPadrao() {
        LaunchOptions options =
                new LaunchOptions(false, NetworkConfig.DEFAULT_PORT, "1.2.3.4", 6000, true);
        ServerDirectory directory =
                ServerDirectory.of(
                        List.of(new ServerPreset("Oficial", "9.9.9.9", 5050, true, true, true)));
        FakeServerChoiceStore saved = new FakeServerChoiceStore();
        saved.remember(new ServerPreset("Casa", "192.168.0.5", 7000, false, false, false));

        ServerPreset resolved = ConnectionResolver.resolveDefault(options, directory, saved);

        assertEquals("1.2.3.4", resolved.host());
        assertEquals(6000, resolved.port());
    }

    @Test
    void escolhaSalvaTemPrioridadeSobreOPresetPadrao() {
        ServerDirectory directory =
                ServerDirectory.of(
                        List.of(new ServerPreset("Oficial", "9.9.9.9", 5050, true, true, true)));
        FakeServerChoiceStore saved = new FakeServerChoiceStore();
        saved.remember(new ServerPreset("Casa", "192.168.0.5", 7000, false, false, false));

        ServerPreset resolved =
                ConnectionResolver.resolveDefault(LaunchOptions.defaults(), directory, saved);

        assertEquals(new ServerPreset("Casa", "192.168.0.5", 7000, false, false, false), resolved);
    }

    @Test
    void semEscolhaSalvaUsaOPresetPadraoDoDiretorio() {
        ServerDirectory directory =
                ServerDirectory.of(
                        List.of(new ServerPreset("Casa", "192.168.0.5", 6000, false, true, false)));

        ServerPreset resolved =
                ConnectionResolver.resolveDefault(
                        LaunchOptions.defaults(), directory, SEM_ESCOLHA_SALVA);

        assertEquals(new ServerPreset("Casa", "192.168.0.5", 6000, false, true, false), resolved);
    }

    @Test
    void semNadaCaiParaLocalhost() {
        ServerPreset resolved =
                ConnectionResolver.resolveDefault(
                        LaunchOptions.defaults(), ServerDirectory.of(List.of()), SEM_ESCOLHA_SALVA);

        assertEquals("localhost", resolved.host());
        assertEquals(NetworkConfig.DEFAULT_PORT, resolved.port());
    }

    @Test
    void storeNuloEEquivalenteASemEscolhaSalva() {
        ServerDirectory directory =
                ServerDirectory.of(
                        List.of(new ServerPreset("Casa", "192.168.0.5", 6000, false, true, false)));

        ServerPreset resolved =
                ConnectionResolver.resolveDefault(LaunchOptions.defaults(), directory, null);

        assertTrue(resolved.name().equals("Casa"));
    }
}
