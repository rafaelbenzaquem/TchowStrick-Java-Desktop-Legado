package br.com.mss.tchow.app;

/**
 * Carteira de "tokens de desfazer" do jogador (ADR-0008). Saldo <b>global</b>: persiste entre
 * partidas e sessões. Cada desfazer aplicado <b>em rede</b> custa 1 token; cada vitória em rede dá
 * +1. Nesta etapa (E3, sem contas) é um contador local por dispositivo — a versão
 * server-authoritative por {@code userId} entra na E4/E6.
 */
public interface UndoWallet {

    /** Saldo inicial de um jogador/dispositivo novo. */
    int INITIAL_BALANCE = 5;

    /** Teto do saldo: ganhos acima disso são descartados. */
    int MAX_BALANCE = 20;

    int balance();

    /** Tenta gastar 1 token. {@code false} (sem gastar) quando o saldo é zero. */
    boolean trySpend();

    /** Credita {@code amount} tokens, respeitando {@link #MAX_BALANCE}. */
    void award(int amount);
}
