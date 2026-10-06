package br.com.mss.tchow.net.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void catalogoEmbutidoApontaOOficialParaAIdentidadeDeProducaoComTls() {
        ServerPreset oficial =
                ServerDirectory.loadBundled().presets().stream()
                        .filter(ServerPreset::official)
                        .findFirst()
                        .orElseThrow();
        assertTrue(oficial.usesMssIdentity());
        assertEquals(
                new IdentityTarget("identity.minashonsoftware.com.br", 443, true),
                oficial.identity());
        assertTrue(oficial.tls());
    }

    @Test
    void registroAntigoDoOficialSemIdentidadePassaAUsarOPresetNovo() {
        ServerPreset antigo =
                new ServerPreset(
                        "Oficial", "tchowstrick.minashonsoftware.com.br", 443, true, true, true);

        ServerPreset atual = ServerDirectory.withOfficialIdentity(antigo);

        assertEquals(
                new IdentityTarget("identity.minashonsoftware.com.br", 443, true),
                atual.identity());
        assertTrue(atual.official());
        assertTrue(atual.isDefault());
        assertFalse(ServerDirectory.isTrustedIdentityEndpoint(atual));
    }

    @Test
    void atualizacaoNaoMexeEmLanEnderecoPersonalizadoNemOutrosDestinos() {
        ServerPreset lan = new ServerPreset("LAN", "192.168.0.5", 5050, false, true, false);
        ServerPreset semTls =
                new ServerPreset(
                        "Oficial", "tchowstrick.minashonsoftware.com.br", 443, false, true, true);
        ServerPreset outraPorta =
                new ServerPreset(
                        "Oficial", "tchowstrick.minashonsoftware.com.br", 8443, true, true, true);
        ServerPreset comIdentidade =
                new ServerPreset(
                        "Oficial",
                        "tchowstrick.minashonsoftware.com.br",
                        443,
                        true,
                        true,
                        true,
                        new IdentityTarget("localhost", 9100, false));

        assertEquals(lan, ServerDirectory.withOfficialIdentity(lan));
        assertEquals(semTls, ServerDirectory.withOfficialIdentity(semTls));
        assertEquals(outraPorta, ServerDirectory.withOfficialIdentity(outraPorta));
        assertEquals(comIdentidade, ServerDirectory.withOfficialIdentity(comIdentidade));
    }

    @Test
    void servidoresJsonExternoComOOficialSemIdentidadeGanhaAIdentidade() throws Exception {
        Path file = Files.createTempFile("servers-oficial-antigo", ".json");
        try {
            Files.writeString(
                    file,
                    """
                    [{"name": "Oficial", "host": "tchowstrick.minashonsoftware.com.br",
                      "port": 443, "tls": true, "default": true, "official": true},
                     {"name": "LAN", "host": "192.168.0.5", "port": 5050, "tls": false}]
                    """);
            System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

            List<ServerPreset> presets = ServerDirectory.load().presets();

            assertTrue(presets.get(0).usesMssIdentity());
            assertNull(presets.get(1).identity());
        } finally {
            System.clearProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY);
            Files.deleteIfExists(file);
        }
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
