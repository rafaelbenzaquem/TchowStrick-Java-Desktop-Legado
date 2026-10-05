package br.com.mss.tchow.app;

import br.com.mss.tchow.domain.Board;
import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.net.Dtos.BoxDto;
import br.com.mss.tchow.net.Dtos.GameOutcomeDto;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MarkedEdgeDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import br.com.mss.tchow.net.Dtos.PlayerDto;
import br.com.mss.tchow.net.GameEvent;
import br.com.mss.tchow.net.GameEventListener;
import br.com.mss.tchow.net.GameTransport;
import br.com.mss.tchow.net.TransportException;
import br.com.mss.tchow.ui.BoardView;
import br.com.mss.tchow.ui.ChatPanel;
import br.com.mss.tchow.ui.PlayerColors;
import br.com.mss.tchow.ui.PlayersPanel;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.JLabel;

/**
 * Cola entre o {@link GameTransport} e as telas. Mantém uma cópia local do {@link Board} (projeção
 * do estado autoritativo do servidor), traduz cliques em jogadas e reage aos {@link GameEvent}s —
 * que chegam sempre na EDT.
 *
 * <p>O servidor é a autoridade: aqui nunca se roda regra, só se replica o que ele já validou.
 */
public final class MatchController implements GameEventListener {

    private final GameTransport transport;
    private final BoardView boardView;
    private final PlayersPanel playersPanel;
    private final ChatPanel chatPanel;
    private final JLabel statusLabel;
    private final PlayerColor localColor;
    private final Consumer<Boolean> onGameOver;

    /** Recebe (podeDesfazer, podeRefazer) sempre que o estado muda, para habilitar os botões. */
    private final BiConsumer<Boolean, Boolean> onHistoryButtons;

    private final Predicate<PlayerColor> rematchConsent;

    /** Carteira de tokens de desfazer; {@code null} no modo local (não usa token). */
    private final UndoWallet undoWallet;

    /**
     * Recebe (saldoGlobal, disponíveisNestaPartida) para exibir; só relevante com {@link
     * #undoWallet}.
     */
    private final BiConsumer<Integer, Integer> onUndoTokens;

    /** Desfazeres por rodada em rede (ADR-0008). Espelha a cota do {@code MatchService}. */
    private static final int NETWORK_UNDOS_PER_ROUND = 2;

    private List<PlayerColor> turnOrder = List.of();
    private Set<PlayerColor> joined = new LinkedHashSet<>();
    private final Map<PlayerColor, String> nicks = new HashMap<>();
    private Map<PlayerColor, Integer> scores = Map.of();
    private PlayerColor currentPlayer;
    private Board board;
    private boolean started;
    private boolean finished;
    private GameOutcomeDto outcome;

    private boolean awaitingUndoOutcome;

    /**
     * Cliquei em "Refazer" e ainda não chegou o {@code HistoryChanged} correspondente (só local).
     */
    private boolean expectingRedo;

    /**
     * Desfazeres que EU já usei na rodada atual. Vem do snapshot (autoritativo) — sobrevive a sair
     * e reconectar.
     */
    private int undosUsedThisRound;

    /** +1 por vitória já creditado nesta rodada. */
    private boolean tokenAwardedThisRound;

    /**
     * Vi a partida em andamento nesta sessão do controller — evita creditar de novo ao reconectar
     * numa partida já terminada.
     */
    private boolean sawUnfinished;

    /** O resultado desta rodada já foi anunciado no chat ({@code [E3-15]}). */
    private boolean gameOverAnnounced;

    public MatchController(
            GameTransport transport,
            BoardView boardView,
            PlayersPanel playersPanel,
            ChatPanel chatPanel,
            JLabel statusLabel,
            Consumer<Boolean> onGameOver,
            BiConsumer<Boolean, Boolean> onHistoryButtons,
            Predicate<PlayerColor> rematchConsent,
            UndoWallet undoWallet,
            BiConsumer<Integer, Integer> onUndoTokens,
            GameSnapshotDto initialState) {
        this.transport = transport;
        this.boardView = boardView;
        this.playersPanel = playersPanel;
        this.chatPanel = chatPanel;
        this.statusLabel = statusLabel;
        this.onGameOver = onGameOver;
        this.onHistoryButtons = onHistoryButtons;
        this.rematchConsent = rematchConsent;
        this.undoWallet = undoWallet;
        this.onUndoTokens = onUndoTokens;
        this.localColor = transport.localColor();

        boardView.setEdgeClickHandler(this::onEdgeClicked);
        chatPanel.setSendHandler(this::onChatTyped);

        applySnapshot(initialState);
        transport.addListener(this);
    }

    public void disconnect() {
        transport.disconnect();
    }

    /** Pede uma revanche ao servidor (só faz sentido com a partida terminada). */
    public void requestRematch() {
        try {
            transport.rematch();
        } catch (TransportException e) {
            flash(e.getMessage());
        }
    }

    public void requestUndo() {
        if (undoWallet != null) {
            if (undosUsedThisRound >= NETWORK_UNDOS_PER_ROUND) {
                flash("você já usou os desfazeres desta partida");
                return;
            }
            if (!undoWallet.trySpend()) {
                flash("sem tokens de desfazer");
                pushTokens();
                return;
            }
            awaitingUndoOutcome = true; // débito otimista; devolvido no UndoRejected
            pushTokens();
        }
        try {
            transport.undo();
        } catch (TransportException e) {
            refundOptimisticSpend();
            flash(e.getMessage());
        }
    }

    private void refundOptimisticSpend() {
        if (awaitingUndoOutcome && undoWallet != null) {
            undoWallet.award(1);
            awaitingUndoOutcome = false;
            pushTokens();
        }
    }

    private void pushTokens() {
        if (undoWallet != null) {
            onUndoTokens.accept(
                    undoWallet.balance(),
                    Math.max(0, NETWORK_UNDOS_PER_ROUND - undosUsedThisRound));
        }
    }

    /** Nova rodada (revanche): zera os contadores locais; o do desfazer vem do snapshot. */
    private void newRound() {
        awaitingUndoOutcome = false;
        tokenAwardedThisRound = false;
        sawUnfinished = false;
        gameOverAnnounced = false;
    }

    /** Vitória em rede: +1 token, uma vez por rodada. Empate e derrota não dão nada. */
    private void maybeAwardWinToken() {
        if (undoWallet == null || tokenAwardedThisRound || !sawUnfinished) {
            return;
        }
        if (started
                && finished
                && outcome != null
                && !outcome.draw()
                && outcome.winners().contains(localColor)) {
            undoWallet.award(1);
            tokenAwardedThisRound = true;
        }
    }

    public void requestRedo() {
        expectingRedo = true;
        try {
            transport.redo();
        } catch (TransportException e) {
            expectingRedo = false;
            flash(e.getMessage());
        }
    }

    // --- ações vindas da UI ------------------------------------------------

    private void onEdgeClicked(Edge edge) {
        if (finished) {
            flash("A partida terminou");
            return;
        }
        if (!started) {
            flash("Aguardando jogadores");
            return;
        }
        if (currentPlayer != localColor) {
            flash("Não é a sua vez");
            return;
        }
        try {
            transport.submitMove(MoveDto.of(localColor, edge));
        } catch (TransportException e) {
            flash(e.getMessage());
        }
    }

    private void onChatTyped(String text) {
        try {
            transport.sendChat(text);
        } catch (TransportException e) {
            flash(e.getMessage());
        }
    }

    // --- eventos da rede (já na EDT) -------------------------------------

    @Override
    public void onEvent(GameEvent event) {
        switch (event) {
            case GameEvent.PlayerJoined e -> {
                setJoined(e.joined());
                started = started || e.matchReady();
                if (e.player().color() != localColor) {
                    logSystem(nickOf(e.player().color()) + " entrou na partida");
                }
                renderPlayers();
                refreshInteractivity();
                updateStatus();
            }
            case GameEvent.PlayerLeft e -> {
                setJoined(e.joined());
                logSystem(nickOf(e.color()) + " saiu — a partida está pausada");
                renderPlayers();
                updateStatus();
            }
            case GameEvent.MatchStarted e -> {
                awaitingUndoOutcome = false;
                applySnapshot(e.snapshot()); // desfazeres usados vêm do snapshot
            }
            case GameEvent.RematchStarted e -> {
                newRound();
                applySnapshot(e.snapshot());
                logSystem("revanche! tabuleiro zerado");
            }
            case GameEvent.HistoryChanged e -> {
                boolean wasRedo = expectingRedo;
                boolean wasMine = undoWallet == null || awaitingUndoOutcome;
                expectingRedo = false;
                awaitingUndoOutcome = false; // o desfecho do meu desfazer chegou
                applySnapshot(e.snapshot());
                if (wasRedo) {
                    logSystem("você refez sua jogada");
                } else {
                    logUndoApplied(wasMine);
                }
            }
            case GameEvent.RematchRequested e -> {
                logSystem(nickOf(e.requester()) + " quer uma revanche");
                boolean allow = rematchConsent.test(e.requester());
                try {
                    transport.respondRematch(allow);
                } catch (TransportException ex) {
                    flash(ex.getMessage());
                }
            }
            case GameEvent.RematchRejected e -> {
                flash(e.reason());
                logSystem("revanche recusada: " + e.reason());
            }
            case GameEvent.UndoRejected e -> {
                boolean refunded = awaitingUndoOutcome && undoWallet != null;
                refundOptimisticSpend();
                flash(e.reason());
                logSystem(
                        "não deu pra desfazer: "
                                + e.reason()
                                + (refunded ? " (token devolvido)" : ""));
            }
            case GameEvent.MoveApplied e -> applyMove(e);
            case GameEvent.ChatPosted e -> {
                var message = e.message();
                chatPanel.append(message.nick(), message.text(), PlayerColors.awt(message.color()));
            }
            case GameEvent.MoveRejected e -> flash(e.reason());
        }
    }

    private void applySnapshot(GameSnapshotDto snapshot) {
        turnOrder = snapshot.turnOrder();
        setJoined(snapshot.joined());
        scores = snapshot.scores();
        currentPlayer = snapshot.currentPlayer();
        started = snapshot.started();
        finished = snapshot.finished();
        outcome = snapshot.outcome();
        undosUsedThisRound = snapshot.undosUsedThisRound().getOrDefault(localColor, 0);
        if (started && !finished) {
            sawUnfinished = true;
        }

        Board projected = new Board(snapshot.width(), snapshot.height());
        for (MarkedEdgeDto marked : snapshot.markedEdges()) {
            projected.markEdge(marked.toEdge(), marked.owner());
        }
        for (BoxDto box : snapshot.capturedBoxes()) {
            projected.setOwner(box.row(), box.col(), box.owner());
        }
        board = projected;

        boardView.showBoard(board);
        boardView.highlightLastMove(null);
        renderPlayers();
        refreshInteractivity();
        updateStatus();
    }

    private void applyMove(GameEvent.MoveApplied event) {
        if (board != null) {
            board.markEdge(event.edge().toEdge(), event.edge().owner());
            for (BoxDto box : event.capturedBoxes()) {
                board.setOwner(box.row(), box.col(), box.owner());
            }
            boardView.showBoard(board);
            boardView.highlightLastMove(event.edge().toEdge());
        }
        currentPlayer = event.nextPlayer();
        scores = event.scores();
        finished = event.gameOver();
        outcome = event.outcome();
        if (started && !finished) {
            sawUnfinished = true;
        }
        renderPlayers();
        refreshInteractivity();
        updateStatus();
    }

    // --- helpers -------------------------------------------------------

    private void setJoined(List<PlayerDto> players) {
        Set<PlayerColor> next = new LinkedHashSet<>();
        for (PlayerDto player : players) {
            next.add(player.color());
            nicks.put(player.color(), player.nick());
        }
        joined = next;
    }

    // --- log de eventos no chat ([E3-15]) -----------------------------

    private void logSystem(String text) {
        chatPanel.system(text);
    }

    private String nickOf(PlayerColor color) {
        String nick = nicks.get(color);
        return nick == null || nick.isBlank() ? color.name() : nick;
    }

    /** Uma linha de chat quando um desfazer é aplicado — só quem gastou vê saldo/cota. */
    private void logUndoApplied(boolean wasMine) {
        if (!wasMine) {
            logSystem(opponentName() + " desfez a jogada dele");
            return;
        }
        if (undoWallet == null) {
            logSystem("você desfez sua jogada (tirando junto a resposta da IA)");
            return;
        }
        int left = Math.max(0, NETWORK_UNDOS_PER_ROUND - undosUsedThisRound);
        logSystem(
                "você desfez sua jogada — 1 token gasto · saldo "
                        + undoWallet.balance()
                        + " · restam "
                        + left
                        + " nesta partida");
    }

    private String opponentName() {
        return turnOrder.stream()
                .filter(color -> color != localColor)
                .findFirst()
                .map(this::nickOf)
                .orElse("o oponente");
    }

    /** Anuncia o resultado da rodada no chat, uma vez. */
    private void announceGameOverOnce() {
        if (gameOverAnnounced || !started || !finished || outcome == null || !sawUnfinished) {
            return;
        }
        gameOverAnnounced = true;
        if (outcome.draw()) {
            logSystem("empate!  " + scoreboard());
        } else if (outcome.winners().contains(localColor)) {
            if (undoWallet != null && tokenAwardedThisRound) {
                logSystem(
                        "você venceu!  +1 token de desfazer (saldo " + undoWallet.balance() + ")");
            } else {
                logSystem("você venceu!  " + scoreboard());
            }
        } else {
            logSystem("você perdeu.  clique em Revanche pra jogar de novo");
        }
    }

    private void renderPlayers() {
        playersPanel.render(turnOrder, joined, scores, currentPlayer, finished, localColor);
    }

    private void refreshInteractivity() {
        boardView.setInteractive(started && !finished);
    }

    private void updateStatus() {
        onGameOver.accept(started && finished);
        maybeAwardWinToken();
        announceGameOverOnce();
        boolean playing = started && !finished;
        boolean tokensOk =
                undoWallet == null
                        || (undoWallet.balance() > 0
                                && undosUsedThisRound < NETWORK_UNDOS_PER_ROUND);
        onHistoryButtons.accept(
                transport.supportsUndo() && playing && transport.canUndo() && tokensOk,
                transport.supportsRedo() && playing && transport.canRedo());
        pushTokens();
        if (!started) {
            statusLabel.setText(
                    "Aguardando jogadores (%d/%d)…".formatted(joined.size(), turnOrder.size()));
            return;
        }
        if (finished && outcome != null) {
            String names =
                    outcome.winners().stream().map(Enum::name).collect(Collectors.joining(", "));
            statusLabel.setText(
                    (outcome.draw() ? "Empate entre " : "Venceu ")
                            + names
                            + "   —   "
                            + scoreboard());
            return;
        }
        String turn = currentPlayer == localColor ? "Sua vez" : "Vez de " + currentPlayer;
        statusLabel.setText(turn + "   —   " + scoreboard());
    }

    private String scoreboard() {
        return turnOrder.stream()
                .map(color -> color + " " + scores.getOrDefault(color, 0))
                .collect(Collectors.joining("   "));
    }

    private void flash(String message) {
        Toolkit.getDefaultToolkit().beep();
        statusLabel.setText(message);
    }
}
