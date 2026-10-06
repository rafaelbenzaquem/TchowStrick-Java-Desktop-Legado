package br.com.mss.tchow.app;

/** Não foi possível abrir o perfil local de dados pedido; a mensagem é para o jogador. */
public final class DataProfileException extends RuntimeException {

    public DataProfileException(String message) {
        super(message);
    }
}
