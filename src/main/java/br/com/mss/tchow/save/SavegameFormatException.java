package br.com.mss.tchow.save;

/**
 * Os bytes não são um save legível: corrompidos, truncados, de uma versão de formato não suportada
 * ou com um enum desconhecido. {@link SavegameCodec#decode} nunca deixa vazar outra exceção.
 */
public final class SavegameFormatException extends Exception {

    public SavegameFormatException(String message) {
        super(message);
    }

    public SavegameFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
