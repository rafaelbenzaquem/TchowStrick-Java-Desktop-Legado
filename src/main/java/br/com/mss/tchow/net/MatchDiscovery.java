package br.com.mss.tchow.net;

import br.com.mss.tchow.net.Dtos.MatchInfoDto;
import br.com.mss.tchow.net.match.OpenMatchSummary;
import java.util.List;

/**
 * Consulta partidas de um servidor sem entrar nelas. Implementado por {@code
 * net.grpc.GrpcDiscovery}.
 */
public interface MatchDiscovery {

    /** {@code tls} usa {@code TlsChannelCredentials} em vez de texto claro (E5, ADR-0005). */
    MatchInfoDto peek(String host, int port, boolean tls) throws TransportException;

    /** Lobby (ADR-0012): as partidas abertas naquele servidor, para escolher qual entrar. */
    List<OpenMatchSummary> listOpenMatches(String host, int port, boolean tls)
            throws TransportException;
}
