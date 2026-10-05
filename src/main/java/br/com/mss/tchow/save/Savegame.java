package br.com.mss.tchow.save;

import br.com.mss.tchow.domain.history.BoardSpec;
import br.com.mss.tchow.domain.history.GameReducer;
import br.com.mss.tchow.domain.history.MoveLog;
import java.util.Objects;

/**
 * Uma partida salva, em memória: a versão do formato, as condições iniciais ({@link BoardSpec}), o
 * {@link MoveLog} (a fonte de verdade) e {@link SaveMeta}. Serializa para bytes via {@link
 * SavegameCodec}.
 */
public record Savegame(int formatVersion, BoardSpec boardSpec, MoveLog moveLog, SaveMeta meta) {

    /** Versão do formato que este código escreve. */
    public static final int CURRENT_FORMAT_VERSION = 1;

    public Savegame {
        Objects.requireNonNull(boardSpec, "boardSpec");
        Objects.requireNonNull(moveLog, "moveLog");
        Objects.requireNonNull(meta, "meta");
    }

    public static Savegame of(BoardSpec boardSpec, MoveLog moveLog, SaveMeta meta) {
        return new Savegame(CURRENT_FORMAT_VERSION, boardSpec, moveLog, meta);
    }

    /** Reconstrói o estado desta partida ({@code null} não acontece — sempre há um estado). */
    public br.com.mss.tchow.domain.GameEngine replay() {
        return GameReducer.replay(boardSpec, moveLog);
    }
}
