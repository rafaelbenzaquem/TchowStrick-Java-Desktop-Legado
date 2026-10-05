package br.com.mss.tchow.net;

import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import java.util.Optional;

/**
 * O que a UI enxerga da rede. Esconde se somos o host (servidor + jogador) ou um cliente puro. Hoje
 * só existe implementação RMI; a interface é o ponto de troca para sockets/WebSocket na Fase 6.
 */
public interface GameTransport {

    /** Entra na partida e devolve o estado inicial. */
    GameSnapshotDto connect() throws TransportException;

    /**
     * Pede uma jogada ao servidor. Recusa chega como {@link GameEvent.MoveRejected} nos listeners.
     */
    void submitMove(MoveDto move) throws TransportException;

    void sendChat(String text) throws TransportException;

    /**
     * Pede uma revanche (mesmo elenco, tabuleiro zerado). Local: imediata. Rede: o oponente
     * confirma — chega um {@link GameEvent.RematchRequested} nos listeners dele, e o desfecho é
     * {@link GameEvent.RematchStarted} ou {@link GameEvent.RematchRejected}.
     */
    void rematch() throws TransportException;

    /** Responde a um {@link GameEvent.RematchRequested} do oponente (só em rede). */
    default void respondRematch(boolean accept) throws TransportException {
        throw new TransportException("sem pedido de revanche para responder");
    }

    /**
     * Desfazer está disponível? Localmente é imediato (limite de 1 por partida); em rede é um
     * pedido ao oponente (E3-08) — {@link #undo()} dispara o pedido e o desfecho chega como {@link
     * GameEvent.HistoryChanged} / {@link GameEvent.UndoRejected}.
     */
    default boolean supportsUndo() {
        return false;
    }

    /** Refazer só existe no modo local (não há refazer compartilhado em rede). */
    default boolean supportsRedo() {
        return false;
    }

    /**
     * Cada desfazer custa 1 token da carteira global do jogador (ADR-0008)? Verdadeiro só em rede;
     * no modo local o desfazer é grátis (limite de 1 por partida, sem economia).
     */
    default boolean undoUsesTokens() {
        return false;
    }

    default boolean canUndo() {
        return false;
    }

    default boolean canRedo() {
        return false;
    }

    /** Desfaz (local) ou pede para desfazer (rede) a última jogada do jogador local. */
    default void undo() throws TransportException {
        throw new TransportException("desfazer não está disponível nesta partida");
    }

    default void redo() throws TransportException {
        throw new TransportException("refazer não está disponível nesta partida");
    }

    /**
     * Material para salvar a partida em arquivo ({@code [E3-09]}), quando este transporte tem o log
     * completo em mãos. Vazio = não dá para salvar aqui (por ora, só o modo local contra a IA).
     */
    default Optional<SaveMaterial> saveMaterial() {
        return Optional.empty();
    }

    void disconnect();

    void addListener(GameEventListener listener);

    PlayerColor localColor();
}
