package br.com.mss.tchow.net.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerDirectoryTest {

    @Test
    void credentialsTrustOnlyBundledOfficialTlsEndpoint() throws IOException {
        // Desde 05/10/2026 o oficial embutido usa a identidade MSS: o fluxo de conta antigo não
        // confia em nenhum destino (nem no oficial, nem num registro antigo dele sem identidade).
        org.junit.jupiter.api.Assertions.assertFalse(
                ServerDirectory.isTrustedIdentityEndpoint(
                        ServerDirectory.loadBundled().defaultPreset().orElseThrow()));
        org.junit.jupiter.api.Assertions.assertFalse(
                ServerDirectory.isTrustedIdentityEndpoint(
                        new ServerPreset(
                                "Oficial",
                                "tchowstrick.minashonsoftware.com.br",
                                443,
                                true,
                                true,
                                true)));
        org.junit.jupiter.api.Assertions.assertFalse(
                ServerDirectory.isTrustedIdentityEndpoint(
                        new ServerPreset("Oficial", "192.168.0.5", 443, true, true, true)));
        org.junit.jupiter.api.Assertions.assertFalse(
                ServerDirectory.isTrustedIdentityEndpoint(
                        new ServerPreset(
                                "Oficial",
                                "tchowstrick.minashonsoftware.com.br",
                                443,
                                false,
                                true,
                                true)));
        Path external = tempDir.resolve("untrusted.json");
        Files.writeString(
                external,
                "[{\"name\":\"Oficial\",\"host\":\"attacker.example\",\"port\":443,\"tls\":true,\"default\":true,\"official\":true}]");
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, external.toString());
        org.junit.jupiter.api.Assertions.assertFalse(
                ServerDirectory.isTrustedIdentityEndpoint(
                        ServerDirectory.load().defaultPreset().orElseThrow()));
    }

    @TempDir private Path tempDir;

    @AfterEach
    void limpaAPropriedadeDeSistema() {
        System.clearProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY);
    }

    @Test
    void semArquivoExternoUsaOEmbutidoComOOficialNaOci() {
        System.setProperty(
                ServerDirectory.EXTERNAL_FILE_PROPERTY,
                tempDir.resolve("nao-existe.json").toString());

        ServerPreset preset = ServerDirectory.load().defaultPreset().orElseThrow();

        assertEquals(
                new ServerPreset(
                        "Oficial",
                        "tchowstrick.minashonsoftware.com.br",
                        443,
                        true,
                        true,
                        true,
                        new IdentityTarget("identity.minashonsoftware.com.br", 443, true)),
                preset);
    }

    @Test
    void arquivoExternoValidoSubstituiAListaInteira() throws IOException {
        Path file = tempDir.resolve("servers.json");
        Files.writeString(
                file,
                """
                [
                  {"name": "Casa", "host": "192.168.0.5", "port": 6000, "default": true}
                ]
                """);
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

        ServerDirectory directory = ServerDirectory.load();

        assertEquals(1, directory.presets().size());
        assertEquals(
                new ServerPreset("Casa", "192.168.0.5", 6000, false, true, false),
                directory.defaultPreset().orElseThrow());
    }

    @Test
    void arquivoExternoComTlsExplicitoEhLido() throws IOException {
        Path file = tempDir.resolve("servers.json");
        Files.writeString(
                file,
                """
                [
                  {"name": "Seguro", "host": "meudominio.com", "port": 443, "tls": true, "default": true}
                ]
                """);
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

        ServerPreset preset = ServerDirectory.load().defaultPreset().orElseThrow();

        assertEquals(new ServerPreset("Seguro", "meudominio.com", 443, true, true, false), preset);
    }

    @Test
    void arquivoExternoComOfficialExplicitoEhLido() throws IOException {
        Path file = tempDir.resolve("servers.json");
        Files.writeString(
                file,
                """
                [
                  {"name": "Nosso", "host": "meudominio.com", "port": 443, "default": true, "official": true}
                ]
                """);
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

        ServerPreset preset = ServerDirectory.load().defaultPreset().orElseThrow();

        assertTrue(preset.official());
    }

    @Test
    void arquivoExternoMalformadoCaiParaOEmbutido() throws IOException {
        Path file = tempDir.resolve("servers.json");
        Files.writeString(file, "isto nao e json valido {{{");
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

        ServerPreset preset = ServerDirectory.load().defaultPreset().orElseThrow();

        assertEquals("Oficial", preset.name());
    }

    @Test
    void semNenhumPresetMarcadoDefaultUsaOPrimeiroDaLista() throws IOException {
        Path file = tempDir.resolve("servers.json");
        Files.writeString(
                file,
                """
                [
                  {"name": "A", "host": "10.0.0.1", "port": 1111, "default": false},
                  {"name": "B", "host": "10.0.0.2", "port": 2222, "default": false}
                ]
                """);
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

        ServerPreset preset = ServerDirectory.load().defaultPreset().orElseThrow();

        assertEquals("A", preset.name());
    }

    @Test
    void listaExternaVaziaNaoTemPresetPadrao() throws IOException {
        Path file = tempDir.resolve("servers.json");
        Files.writeString(file, "[]");
        System.setProperty(ServerDirectory.EXTERNAL_FILE_PROPERTY, file.toString());

        ServerDirectory directory = ServerDirectory.load();

        assertTrue(directory.defaultPreset().isEmpty());
        assertEquals(List.of(), directory.presets());
    }

    @Test
    void oRecursoEmbutidoEmSiEstaPresenteEValido() {
        ServerDirectory bundled = ServerDirectory.loadBundled();

        assertEquals(
                new ServerPreset(
                        "Oficial",
                        "tchowstrick.minashonsoftware.com.br",
                        443,
                        true,
                        true,
                        true,
                        new IdentityTarget("identity.minashonsoftware.com.br", 443, true)),
                bundled.defaultPreset().orElseThrow());
    }
}
