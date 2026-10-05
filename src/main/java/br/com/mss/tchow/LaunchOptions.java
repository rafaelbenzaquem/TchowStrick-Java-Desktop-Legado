package br.com.mss.tchow;

import br.com.mss.tchow.net.NetworkConfig;
import java.util.List;

/**
 * Opções de linha de comando do cliente desktop ([E4.5-02], ADR-0014):
 *
 * <ul>
 *   <li>{@code --embedded-server} — sobe o servidor no próprio processo ([E4a-02]); {@code
 *       --port=NNNN} escolhe a porta desse servidor embutido (senão {@link
 *       NetworkConfig#DEFAULT_PORT}).
 *   <li>{@code --server=host:porta} — aponta direto para um servidor remoto conhecido, sem
 *       consultar {@code servers.json} nem rodar descoberta em LAN: pré-preenche host/porta nos
 *       diálogos de Criar partida/Entrar.
 *   <li>{@code --no-discovery} — não responde a buscas de servidor na rede local quando hospedando
 *       embutido ([E4.5-03]).
 * </ul>
 *
 * <p>{@code --embedded-server} e {@code --server=} são mutuamente exclusivos: o primeiro diz "esta
 * máquina é o servidor", o segundo aponta para outra — juntos seriam ambíguos.
 */
public record LaunchOptions(
        boolean embeddedServer,
        int embeddedPort,
        String remoteHost,
        int remotePort,
        boolean discoveryEnabled) {

    public static LaunchOptions defaults() {
        return new LaunchOptions(false, NetworkConfig.DEFAULT_PORT, null, 0, true);
    }

    /**
     * @throws IllegalArgumentException combinação inválida de flags, ou porta/endereço mal formado.
     */
    public static LaunchOptions parse(String[] args) {
        List<String> asList = List.of(args);
        boolean embeddedServer = asList.contains("--embedded-server");
        boolean discoveryEnabled = !asList.contains("--no-discovery");
        int embeddedPort = NetworkConfig.DEFAULT_PORT;
        String remoteHost = null;
        int remotePort = 0;

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                embeddedPort = parsePort(arg.substring("--port=".length()), arg);
            } else if (arg.startsWith("--server=")) {
                String value = arg.substring("--server=".length());
                int separator = value.lastIndexOf(':');
                if (separator <= 0 || separator == value.length() - 1) {
                    throw new IllegalArgumentException(
                            "--server= precisa ser host:porta, ex.: --server=147.15.109.255:5050"
                                    + " (recebido: \""
                                    + value
                                    + "\")");
                }
                remoteHost = value.substring(0, separator);
                remotePort = parsePort(value.substring(separator + 1), arg);
            }
        }

        if (embeddedServer && remoteHost != null) {
            throw new IllegalArgumentException(
                    "--embedded-server e --server= não fazem sentido juntos — o primeiro diz"
                            + " \"esta máquina é o servidor\", o segundo aponta para outra.");
        }

        return new LaunchOptions(
                embeddedServer, embeddedPort, remoteHost, remotePort, discoveryEnabled);
    }

    private static int parsePort(String raw, String originalArg) {
        try {
            int port = Integer.parseInt(raw.strip());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("fora do intervalo 1-65535");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "porta inválida em \"" + originalArg + "\": " + e.getMessage());
        }
    }
}
