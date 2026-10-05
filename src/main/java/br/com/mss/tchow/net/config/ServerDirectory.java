package br.com.mss.tchow.net.config;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lista de servidores conhecidos pelo cliente ([E4.5-01], ADR-0014) — resolve, em ordem:
 *
 * <ol>
 *   <li>um arquivo externo {@code servers.json} (caminho: propriedade de sistema {@value
 *       #EXTERNAL_FILE_PROPERTY}, senão {@value #DEFAULT_EXTERNAL_FILE} no diretório de trabalho) —
 *       se existir e for válido, <b>substitui</b> a lista inteira, sem mesclar com a embutida;
 *   <li>senão, o {@code servers.json} embutido no {@code .jar} ({@value #BUNDLED_RESOURCE}), que
 *       traz o preset "Oficial" apontando para o servidor de produção na OCI.
 * </ol>
 *
 * <p>Nunca lança: um arquivo externo malformado só gera um aviso no log e cai para o embutido; se
 * até o embutido faltar/for inválido, a lista fica vazia (quem chama decide o que fazer — ver
 * {@code ConnectionResolver.resolveDefault}, [E4.5-04]).
 */
public final class ServerDirectory {

    private static final Logger logger = LoggerFactory.getLogger(ServerDirectory.class);

    static final String EXTERNAL_FILE_PROPERTY = "tchow.servers.file";
    static final String DEFAULT_EXTERNAL_FILE = "servers.json";
    private static final String BUNDLED_RESOURCE = "/servers-default.json";

    private final List<ServerPreset> presets;

    private ServerDirectory(List<ServerPreset> presets) {
        this.presets = List.copyOf(presets);
    }

    /** Constrói uma lista em memória, sem tocar arquivo/recurso nenhum — útil para teste. */
    public static ServerDirectory of(List<ServerPreset> presets) {
        return new ServerDirectory(presets);
    }

    public List<ServerPreset> presets() {
        return presets;
    }

    /**
     * O marcado {@code default}; se nenhum estiver marcado, o primeiro da lista; vazio se a lista
     * estiver vazia.
     */
    public Optional<ServerPreset> defaultPreset() {
        return presets.stream()
                .filter(ServerPreset::isDefault)
                .findFirst()
                .or(() -> presets.stream().findFirst());
    }

    public static ServerDirectory load() {
        Path external = Path.of(System.getProperty(EXTERNAL_FILE_PROPERTY, DEFAULT_EXTERNAL_FILE));
        if (Files.isRegularFile(external)) {
            try {
                List<ServerPreset> parsed =
                        parse(Files.readString(external, StandardCharsets.UTF_8));
                logger.info(
                        "servers.json externo carregado de {} ({} servidor(es))",
                        external.toAbsolutePath(),
                        parsed.size());
                return new ServerDirectory(parsed);
            } catch (IOException | JsonParseException e) {
                logger.warn(
                        "falha ao ler {} ({}) — usando a lista embutida",
                        external.toAbsolutePath(),
                        e.getMessage());
            }
        }
        return loadBundled();
    }

    /** Trust comes from the shipped catalog, never from LAN discovery or an external label. */
    public static boolean isTrustedIdentityEndpoint(ServerPreset server) {
        return server.tls()
                && server.official()
                && loadBundled().presets().stream()
                        .anyMatch(
                                p ->
                                        p.official()
                                                && p.tls()
                                                && p.host().equalsIgnoreCase(server.host())
                                                && p.port() == server.port());
    }

    static ServerDirectory loadBundled() {
        try (InputStream in = ServerDirectory.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                logger.warn("recurso embutido {} não encontrado", BUNDLED_RESOURCE);
                return new ServerDirectory(List.of());
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new ServerDirectory(parse(json));
        } catch (IOException | JsonParseException e) {
            logger.warn("falha ao ler o servers.json embutido: {}", e.getMessage());
            return new ServerDirectory(List.of());
        }
    }

    private static List<ServerPreset> parse(String json) {
        Type type = new TypeToken<List<ServerPresetJson>>() {}.getType();
        List<ServerPresetJson> raw = new Gson().fromJson(json, type);
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(ServerPresetJson::toPreset).toList();
    }

    /**
     * DTO só para o Gson — {@code default} é palavra reservada em Java, não dá para usar como nome
     * de componente de record.
     */
    private record ServerPresetJson(
            String name,
            String host,
            int port,
            boolean tls,
            @SerializedName("default") boolean isDefault,
            boolean official) {
        ServerPreset toPreset() {
            return new ServerPreset(name, host, port, tls, isDefault, official);
        }
    }
}
