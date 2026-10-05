package br.com.mss.tchow.save;

import br.com.mss.tchow.domain.PlayerColor;
import java.time.Instant;

/**
 * Metadados de um save. <b>Nunca</b> entram no <i>fold</i> do log (não afetam o replay). {@link
 * #createdAt} e {@link #appVersion} servem para exibição/diagnóstico; {@link #humanColor} e {@link
 * #aiLevel} guiam a <b>reabertura</b> contra a IA ({@code [E3-09]}) — {@code null}/{@code ""} num
 * save antigo ou de partida em rede, e aí a UI pergunta.
 *
 * @param createdAt quando o save foi gerado (UTC); pode ser {@code null}
 * @param appVersion versão do app que gerou o save; {@code ""} quando desconhecida
 * @param humanColor cor do jogador humano na partida salva; {@code null} quando desconhecida
 * @param aiLevel nível da IA ({@code "EASY"} / {@code "MEDIUM"}); {@code ""} quando desconhecido
 */
public record SaveMeta(
        Instant createdAt, String appVersion, PlayerColor humanColor, String aiLevel) {

    public SaveMeta {
        appVersion = appVersion == null ? "" : appVersion;
        aiLevel = aiLevel == null ? "" : aiLevel;
    }

    /** Sem dados de reabertura (para replay puro ou testes). */
    public static SaveMeta of(Instant createdAt, String appVersion) {
        return new SaveMeta(createdAt, appVersion, null, "");
    }

    public static SaveMeta now(String appVersion) {
        return new SaveMeta(Instant.now(), appVersion, null, "");
    }

    public static SaveMeta now(String appVersion, PlayerColor humanColor, String aiLevel) {
        return new SaveMeta(Instant.now(), appVersion, humanColor, aiLevel);
    }
}
