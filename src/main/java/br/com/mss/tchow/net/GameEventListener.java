package br.com.mss.tchow.net;

/**
 * Recebe os {@link GameEvent}s no lado da UI. As implementações de transporte garantem que {@link
 * #onEvent} é sempre chamado na Event Dispatch Thread do Swing.
 */
public interface GameEventListener {

    void onEvent(GameEvent event);
}
