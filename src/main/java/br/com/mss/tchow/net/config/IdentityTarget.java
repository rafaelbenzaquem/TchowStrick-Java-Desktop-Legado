package br.com.mss.tchow.net.config;

import java.util.Locale;

/**
 * Destino do serviço de identidade MSS usado por um servidor de jogo (MSSIdentity M4-04, M1 deste
 * repositório). Opcional em {@link ServerPreset}: sem ele, o servidor segue o fluxo atual (LAN ou
 * conta oficial {@code tchowstrick.auth.v1}).
 *
 * <p>{@code tls = false} (texto claro) só é aceito para hosts de loopback ({@link
 * #isLoopbackHost}): credenciais de conta nunca trafegam em claro fora da própria máquina.
 */
public record IdentityTarget(String host, int port, boolean tls) {

    public IdentityTarget {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host da identidade vazio");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("porta da identidade fora de 1-65535: " + port);
        }
        host = host.strip();
    }

    /**
     * Interpreta {@code host:porta}.
     *
     * @throws IllegalArgumentException formato inválido.
     */
    public static IdentityTarget parse(String hostPort, boolean tls) {
        String value = hostPort == null ? "" : hostPort.strip();
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(
                    "destino da identidade precisa ser host:porta, ex.: localhost:9100 (recebido: \""
                            + value
                            + "\")");
        }
        int port;
        try {
            port = Integer.parseInt(value.substring(separator + 1).strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("porta inválida no destino da identidade: " + value);
        }
        return new IdentityTarget(value.substring(0, separator), port, tls);
    }

    /** {@code host:porta}, no formato do builder do {@code IdentityClient}. */
    public String authority() {
        return host + ":" + port;
    }

    /** Texto claro só é aceitável contra a própria máquina. */
    public boolean plaintextAllowed() {
        return tls || isLoopbackHost(host);
    }

    /** {@code localhost}, {@code 127.x.y.z} ou {@code ::1} — sem consultar DNS. */
    public static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.strip().toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        return h.equals("localhost") || h.equals("::1") || h.startsWith("127.");
    }
}
