/**
 * Camada de composição. {@link br.com.mss.tchow.app.MatchController} é a cola entre {@link
 * br.com.mss.tchow.net.GameTransport} e as telas de {@link br.com.mss.tchow.ui}: traduz cliques em
 * jogadas, reage aos {@link br.com.mss.tchow.net.GameEvent}s e mantém uma projeção local do {@link
 * br.com.mss.tchow.domain.Board} (o servidor é a autoridade).
 *
 * <p>Depende de {@code domain}, {@code net} e {@code ui}.
 */
package br.com.mss.tchow.app;
