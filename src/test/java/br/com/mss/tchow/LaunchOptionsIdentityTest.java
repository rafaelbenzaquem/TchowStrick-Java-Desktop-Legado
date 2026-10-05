package br.com.mss.tchow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.net.config.IdentityTarget;
import br.com.mss.tchow.net.config.ServerDirectory;
import br.com.mss.tchow.net.config.ServerPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code --identity=} (M1): destino de identidade MSS para o servidor de {@code --server=}. */
class LaunchOptionsIdentityTest {

    @Test
    void identidadeComServidorDaLinhaDeComando() {
        LaunchOptions options =
                LaunchOptions.parse(
                        new String[] {
                            "--server=localhost:5050",
                            "--identity=localhost:9100",
                            "--identity-plaintext"
                        });

        assertEquals(new IdentityTarget("localhost", 9100, false), options.identity());
    }

    @Test
    void identidadeUsaTlsPorPadrao() {
        LaunchOptions options =
                LaunchOptions.parse(
                        new String[] {"--server=staging:443", "--identity=identity.staging:443"});

        assertTrue(options.identity().tls());
    }

    @Test
    void semIdentidadeNadaMuda() {
        assertNull(LaunchOptions.parse(new String[] {"--server=localhost:5050"}).identity());
    }

    @Test
    void identidadeExigeServidorDaLinhaDeComando() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--identity=localhost:9100"}));
    }

    @Test
    void textoClaroSoParaLocalhost() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LaunchOptions.parse(
                                new String[] {
                                    "--server=10.0.0.5:5050",
                                    "--identity=10.0.0.5:9100",
                                    "--identity-plaintext"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LaunchOptions.parse(
                                new String[] {"--server=localhost:5050", "--identity-plaintext"}));
    }

    @Test
    void resolverLevaAIdentidadeParaOPreset() {
        LaunchOptions options =
                LaunchOptions.parse(
                        new String[] {
                            "--server=localhost:5050",
                            "--identity=localhost:9100",
                            "--identity-plaintext"
                        });

        ServerPreset preset =
                ConnectionResolver.resolveDefault(options, ServerDirectory.of(List.of()), null);

        assertTrue(preset.usesMssIdentity());
        assertEquals("localhost:9100", preset.identity().authority());
        assertEquals(5050, preset.port());
    }
}
