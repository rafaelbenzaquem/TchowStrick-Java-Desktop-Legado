package br.com.mss.tchow.net;

/** Falha de transporte vista pela UI (não conectou, conexão caiu, cor indisponível...). */
public class TransportException extends Exception {

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
