package br.com.mss.tchow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.mss.tchow.net.NetworkConfig;
import org.junit.jupiter.api.Test;

class LaunchOptionsTest {

    @Test
    void semArgumentosUsaOsPadroes() {
        LaunchOptions options = LaunchOptions.parse(new String[0]);

        assertFalse(options.embeddedServer());
        assertEquals(NetworkConfig.DEFAULT_PORT, options.embeddedPort());
        assertNull(options.remoteHost());
        assertTrue(options.discoveryEnabled());
    }

    @Test
    void embeddedServerLigaOModoEmbutido() {
        assertTrue(LaunchOptions.parse(new String[] {"--embedded-server"}).embeddedServer());
    }

    @Test
    void portEscolheAPortaDoServidorEmbutido() {
        LaunchOptions options =
                LaunchOptions.parse(new String[] {"--embedded-server", "--port=6001"});

        assertTrue(options.embeddedServer());
        assertEquals(6001, options.embeddedPort());
    }

    @Test
    void portaInvalidaLanca() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--port=nao-e-numero"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--port=0"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--port=70000"}));
    }

    @Test
    void serverApontaHostEPortaRemotos() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"--server=147.15.109.255:5050"});

        assertEquals("147.15.109.255", options.remoteHost());
        assertEquals(5050, options.remotePort());
        assertFalse(options.embeddedServer());
    }

    @Test
    void serverSemDoisPontosLanca() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--server=semporta"}));
    }

    @Test
    void serverComPortaVaziaLanca() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LaunchOptions.parse(new String[] {"--server=host:"}));
    }

    @Test
    void embeddedServerEServerJuntosLancam() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LaunchOptions.parse(
                                new String[] {"--embedded-server", "--server=1.2.3.4:5050"}));
    }

    @Test
    void noDiscoveryDesligaADescoberta() {
        assertFalse(LaunchOptions.parse(new String[] {"--no-discovery"}).discoveryEnabled());
    }

    @Test
    void argumentosDesconhecidosSaoIgnorados() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"--nao-existe", "--outra=coisa"});

        assertEquals(LaunchOptions.defaults(), options);
    }
}
