package br.com.mss.tchow.net.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Destino de identidade MSS por servidor (M1). */
class IdentityPresetTest {

    @TempDir private Path tempDir;

    @AfterEach
    void limpaAPropriedadeDeSistema() {
        System.clearProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY);
    }

    @Test
    void interpretaHostPorta() {
        IdentityTarget target = IdentityTarget.parse(" localhost:9100 ", false);

        assertEquals("localhost", target.host());
        assertEquals(9100, target.port());
        assertFalse(target.tls());
        assertEquals("localhost:9100", target.authority());
    }

    @Test
    void recusaFormatoOuPortaInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> IdentityTarget.parse("localhost", true));
        assertThrows(IllegalArgumentException.class, () -> IdentityTarget.parse(":9100", true));
        assertThrows(IllegalArgumentException.class, () -> IdentityTarget.parse("h:abc", true));
        assertThrows(IllegalArgumentException.class, () -> IdentityTarget.parse("h:70000", true));
    }

    @Test
    void textoClaroSoParaLoopback() {
        assertTrue(new IdentityTarget("localhost", 9100, false).plaintextAllowed());
        assertTrue(new IdentityTarget("127.0.0.1", 9100, false).plaintextAllowed());
        assertTrue(new IdentityTarget("[::1]", 9100, false).plaintextAllowed());
        assertFalse(new IdentityTarget("192.168.0.5", 9100, false).plaintextAllowed());
        assertTrue(new IdentityTarget("identity.example", 443, true).plaintextAllowed());
    }

    @Test
    void presetSemIdentidadeMantemOComportamentoAnterior() {
        ServerPreset preset = new ServerPreset("Casa", "192.168.0.5", 6000, false, true, false);

        assertNull(preset.identity());
        assertFalse(preset.usesMssIdentity());
    }

    @Test
    void servidorDoJsonPodeDeclararIdentidade() throws IOException {
        Path file = tempDir.resolve("servers.json");
        Files.writeString(
                file,
                """
                [
                  {"name": "Local MSS", "host": "localhost", "port": 5050, "default": true,
                   "identity": "localhost:9100", "identityTls": false},
                  {"name": "Staging", "host": "staging.example", "port": 443, "tls": true,
                   "identity": "identity.staging.example:443"},
                  {"name": "LAN", "host": "192.168.0.5", "port": 5050}
                ]
                """);
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

        var presets = ServerDirectory.load().presets();

        assertEquals(new IdentityTarget("localhost", 9100, false), presets.get(0).identity());
        assertEquals(
                new IdentityTarget("identity.staging.example", 443, true),
                presets.get(1).identity());
        assertNull(presets.get(2).identity());
    }

    @Test
    void identidadeMalFormadaNoJsonCaiParaOEmbutido() throws IOException {
        Path file = tempDir.resolve("servers.json");
        Files.writeString(
                file,
                "[{\"name\":\"X\",\"host\":\"localhost\",\"port\":5050,\"identity\":\"sem-porta\"}]");
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

        ServerPreset preset = ServerDirectory.load().defaultPreset().orElseThrow();

        assertEquals("Oficial", preset.name());
    }

    @Test
    void catalogoEmbutidoNaoApontaOOficialParaAIdentidade() {
        assertTrue(
                ServerDirectory.loadBundled().presets().stream()
                        .noneMatch(ServerPreset::usesMssIdentity));
    }

    @Test
    void arquivoDeDesenvolvimentoLocalDoRepositorioEValido() {
        Path dev = Path.of("config", "servers-local-identidade.json");
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, dev.toString());

        ServerPreset preset = ServerDirectory.load().defaultPreset().orElseThrow();

        assertEquals("localhost", preset.host());
        assertEquals(5050, preset.port());
        assertFalse(preset.official());
        assertEquals(new IdentityTarget("localhost", 9100, false), preset.identity());
    }
}
