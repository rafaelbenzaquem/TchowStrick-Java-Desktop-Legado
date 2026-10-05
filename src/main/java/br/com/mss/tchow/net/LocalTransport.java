package br.com.mss.tchow.net;

import br.com.mss.tchow.domain.GameView;
import br.com.mss.tchow.domain.InvalidMoveException;
import br.com.mss.tchow.domain.Move;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.domain.ai.Strategy;
import br.com.mss.tchow.domain.history.MoveLog;
import br.com.mss.tchow.net.Dtos.ChatMessageDto;
import br.com.mss.tchow.net.Dtos.GameSnapshotDto;
import br.com.mss.tchow.net.Dtos.MoveDto;
import br.com.mss.tchow.net.match.MatchService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.random.RandomGenerator;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transporte <b>local</b> (sem rede): um {@link MatchService} em processo com um assento humano e
 * os demais preenchidos por bots ({@link Strategy}). Reaproveita todo o {@code MatchController}/UI
 * — o {@code Main} só escolhe este transporte no lugar do gRPC quando a partida é contra a IA.
 *
 * <p>Os bots jogam de forma assíncrona (via {@code invokeLater}) para não recorrer dentro do
 * broadcast e para a jogada aparecer depois da anterior na tela. A escolha da jogada em si roda num
 * {@link SwingWorker} (fora da EDT) — a IA "Difícil" pode demorar até seu orçamento de tempo
 * pensando, e isso não pode travar a janela.
 *
 * <p>Logging (ADR-0011): conectar/desconectar em INFO — não há round-trip de rede aqui, mas ajuda a
 * diferenciar no log "partida local" de "partida em rede".
 */
public final class LocalTransport implements GameTransport {

    private static final Logger logger = LoggerFactory.getLogger(LocalTransport.class);

    /** Um assento de bot: cor, nick de exibição e a estratégia. */
    public record Bot(PlayerColor color, String nick, Strategy strategy) {}

    private final MatchService match;
    private final PlayerColor humanColor;
    private final String humanNick;
    private final List<Bot> bots;
    private final RandomGenerator rng;
    private final MoveLog history; // não-vazio = abrindo uma partida salva ([E3-09])
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean disconnected;

    public LocalTransport(
            int width,
            int height,
            List<PlayerColor> turnOrder,
            PlayerColor humanColor,
            String humanNick,
            List<Bot> bots,
            long seed) {
        this(width, height, turnOrder, humanColor, humanNick, bots, seed, MoveLog.empty());
    }

    /** Abre a partida já com um histórico ({@code history}) — continua de onde o save parou. */
    public LocalTransport(
            int width,
            int height,
            List<PlayerColor> turnOrder,
            PlayerColor humanColor,
            String humanNick,
            List<Bot> bots,
            long seed,
            MoveLog history) {
        this.humanColor = humanColor;
        this.humanNick = humanNick;
        this.bots = List.copyOf(bots);
        this.rng = new java.util.Random(seed);
        this.history = history == null ? MoveLog.empty() : history;
        // ADR-0008: no modo local, 1 desfazer por partida (zera na revanche).
        this.match = new MatchService(width, height, turnOrder, 1);
    }

    @Override
    public GameSnapshotDto connect() throws TransportException {
        try {
            // Aplica o histórico ANTES dos joins: assim o MatchStarted que o join do
            // humano dispara já carrega o tabuleiro do save (senão a tela abre em branco).
            if (!history.isEmpty()) {
                try {
                    match.loadHistory(history);
                } catch (RuntimeException e) {
                    throw new TransportException("save inválido: " + e.getMessage(), e);
                }
            }
            for (Bot bot : bots) {
                match.join(bot.nick(), bot.color(), event -> scheduleBotTurn());
            }
            GameSnapshotDto snapshot = match.join(humanNick, humanColor, this::deliver);
            scheduleBotTurn();
            logger.info(
                    "partida local iniciada: {} ({}) vs {} bot(s)",
                    humanNick,
                    humanColor,
                    bots.size());
            return snapshot;
        } catch (ColorUnavailableException e) {
            throw new TransportException(e.getMessage(), e);
        }
    }

    @Override
    public Optional<SaveMaterial> saveMaterial() {
        return Optional.of(new SaveMaterial(match.boardSpec(), match.moveLog()));
    }

    @Override
    public void submitMove(MoveDto move) {
        try {
            match.submitMove(move);
        } catch (RejectedMoveException e) {
            deliver(new GameEvent.MoveRejected(e.getMessage()));
        }
        scheduleBotTurn();
    }

    @Override
    public void sendChat(String text) {
        match.sendChat(new ChatMessageDto(humanColor, humanNick, text));
    }

    @Override
    public void rematch() {
        try {
            match.rematch();
        } catch (RejectedMoveException e) {
            deliver(new GameEvent.MoveRejected(e.getMessage()));
            return;
        }
        scheduleBotTurn();
    }

    @Override
    public boolean supportsUndo() {
        return true;
    }

    @Override
    public boolean supportsRedo() {
        return true;
    }

    @Override
    public boolean canUndo() {
        return match.canUndo(humanColor);
    }

    @Override
    public boolean canRedo() {
        return match.canRedo();
    }

    @Override
    public void undo() {
        try {
            match.undo(humanColor);
        } catch (IllegalStateException e) {
            deliver(new GameEvent.MoveRejected(e.getMessage()));
        }
        // após o undo é a vez do humano — nenhum bot a acionar
    }

    @Override
    public void redo() {
        try {
            match.redo();
        } catch (IllegalStateException | InvalidMoveException e) {
            deliver(new GameEvent.MoveRejected(e.getMessage()));
        }
    }

    @Override
    public void disconnect() {
        logger.info("partida local encerrada");
        disconnected = true;
        match.leave(humanColor);
    }

    @Override
    public void addListener(GameEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public PlayerColor localColor() {
        return humanColor;
    }

    /** Visão somente-leitura do estado atual (modo local) — usada por testes e diagnóstico. */
    public GameView currentView() {
        return match.view();
    }

    private void scheduleBotTurn() {
        SwingUtilities.invokeLater(this::playBotIfItsTurn);
    }

    private void playBotIfItsTurn() {
        if (disconnected) {
            return;
        }
        GameView view = match.view();
        if (view.isFinished()) {
            return;
        }
        PlayerColor turn = view.currentPlayer();
        Bot bot = bots.stream().filter(b -> b.color() == turn).findFirst().orElse(null);
        if (bot == null) {
            return; // é a vez do humano
        }
        // chooseMove roda fora da EDT (a IA "Difícil" pode levar centenas de ms pensando; sem
        // isso, a janela inteira travava — sem repintar, sem clique — até a jogada sair). O
        // MatchService é synchronized e valida a jogada de novo em submitMove: se o estado mudou
        // enquanto o bot pensava (undo/revanche do humano em paralelo), a jogada velha é
        // recusada de forma limpa, não corrompe nada.
        new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() {
                return bot.strategy().chooseMove(view, rng);
            }

            @Override
            protected void done() {
                if (disconnected) {
                    return;
                }
                Move move;
                try {
                    move = get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new RuntimeException(e.getCause());
                }
                try {
                    match.submitMove(MoveDto.of(bot.color(), move.edge()));
                } catch (RejectedMoveException e) {
                    deliver(new GameEvent.MoveRejected(e.getMessage()));
                    return;
                }
                scheduleBotTurn(); // se o bot capturou, ainda é a vez dele
            }
        }.execute();
    }

    private void deliver(GameEvent event) {
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onEvent(event)));
    }
}
