package br.com.mss.tchow.net;

import br.com.mss.tchow.domain.history.BoardSpec;
import br.com.mss.tchow.domain.history.MoveLog;

/**
 * O que um {@link GameTransport} entrega para salvar a partida em arquivo ({@code [E3-09]}): as
 * condições iniciais e o log de jogadas — a mesma dupla que o {@code SavegameCodec} serializa. Sem
 * metadados (quem é humano, nível da IA): isso é escolhido ao abrir.
 */
public record SaveMaterial(BoardSpec spec, MoveLog log) {}
