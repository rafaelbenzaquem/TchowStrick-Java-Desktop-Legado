/**
 * Componentes e diálogos Swing. Tudo é dirigido pela EDT.
 *
 * <p>{@link br.com.mss.tchow.ui.BoardView} desenha um {@link br.com.mss.tchow.domain.Board} com
 * pintura vetorial e avisa cliques em arestas; {@link br.com.mss.tchow.ui.BoardGeometry} faz a
 * conta pixel ↔ aresta. As telas não conhecem rede nem regras — quem liga as pontas é {@link
 * br.com.mss.tchow.app.MatchController}.
 *
 * <p>Depende de {@code domain} (e de {@code net} apenas para os tipos de DTO).
 */
package br.com.mss.tchow.ui;
