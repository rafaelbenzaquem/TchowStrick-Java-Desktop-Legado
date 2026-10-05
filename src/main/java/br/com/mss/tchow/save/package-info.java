/**
 * Formato de arquivo de partida salva (E3). <b>Separado do contrato de rede</b> ({@code
 * game.proto}): aqui vive o {@code save.proto} ({@code tchowstrick.save.v1}) e o codec.
 *
 * <ul>
 *   <li>{@link br.com.mss.tchow.save.Savegame} — a partida salva em memória (versão + {@code
 *       BoardSpec} + {@code MoveLog} + {@link br.com.mss.tchow.save.SaveMeta}).
 *   <li>{@link br.com.mss.tchow.save.SavegameCodec} — {@code encode}/{@code decode} para bytes;
 *       {@code decode} nunca deixa vazar exceção que não seja {@link
 *       br.com.mss.tchow.save.SavegameFormatException}.
 * </ul>
 *
 * <p>Depende de {@code domain} e {@code domain.history} (não o contrário — {@code domain} continua
 * puro, sem protobuf).
 */
package br.com.mss.tchow.save;
