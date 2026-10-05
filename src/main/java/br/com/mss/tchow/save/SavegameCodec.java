package br.com.mss.tchow.save;

import br.com.mss.tchow.domain.Edge;
import br.com.mss.tchow.domain.EdgeOrientation;
import br.com.mss.tchow.domain.Move;
import br.com.mss.tchow.domain.PlayerColor;
import br.com.mss.tchow.domain.history.BoardSpec;
import br.com.mss.tchow.domain.history.MoveLog;
import br.com.mss.tchow.save.proto.SaveColor;
import br.com.mss.tchow.save.proto.SaveOrientation;
import br.com.mss.tchow.save.proto.SavedMove;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializa um {@link Savegame} para bytes ({@code tchowstrick.save.v1}) e de volta. O {@link
 * #decode} trata <b>qualquer</b> entrada — corrompida, truncada, de versão desconhecida — lançando
 * {@link SavegameFormatException}, nunca deixando outra exceção vazar (ver {@code docs/VALIDACAO.md
 * §1.11}).
 */
public final class SavegameCodec {

    private SavegameCodec() {}

    public static byte[] encode(Savegame savegame) {
        BoardSpec spec = savegame.boardSpec();
        br.com.mss.tchow.save.proto.Savegame.Builder b =
                br.com.mss.tchow.save.proto.Savegame.newBuilder()
                        .setFormatVersion(savegame.formatVersion())
                        .setBoardWidth(spec.width())
                        .setBoardHeight(spec.height());

        for (PlayerColor color : spec.turnOrder()) {
            b.addTurnOrder(toProto(color));
        }
        for (Move move : savegame.moveLog().moves()) {
            Edge edge = move.edge();
            b.addMoves(
                    SavedMove.newBuilder()
                            .setPlayer(toProto(move.player()))
                            .setOrientation(toProto(edge.orientation()))
                            .setRow(edge.row())
                            .setCol(edge.col()));
        }

        SaveMeta meta = savegame.meta();
        br.com.mss.tchow.save.proto.SaveMeta.Builder metaBuilder =
                br.com.mss.tchow.save.proto.SaveMeta.newBuilder()
                        .setCreatedAt(meta.createdAt() == null ? "" : meta.createdAt().toString())
                        .setAppVersion(meta.appVersion())
                        .setAiLevel(meta.aiLevel());
        if (meta.humanColor() != null) {
            metaBuilder.setHumanColor(toProto(meta.humanColor()));
        }
        b.setMeta(metaBuilder);

        return b.build().toByteArray();
    }

    public static Savegame decode(byte[] bytes) throws SavegameFormatException {
        br.com.mss.tchow.save.proto.Savegame p;
        try {
            p = br.com.mss.tchow.save.proto.Savegame.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new SavegameFormatException("bytes não são um save válido", e);
        }

        int version = p.getFormatVersion();
        if (version != Savegame.CURRENT_FORMAT_VERSION) {
            throw new SavegameFormatException(
                    "versão de formato não suportada: "
                            + version
                            + " (suportadas: "
                            + Savegame.CURRENT_FORMAT_VERSION
                            + ")");
        }

        try {
            int width = p.getBoardWidth();
            int height = p.getBoardHeight();
            if (width < 1 || height < 1) {
                throw new SavegameFormatException("tabuleiro inválido: " + width + "x" + height);
            }

            List<PlayerColor> turnOrder = new ArrayList<>();
            for (SaveColor sc : p.getTurnOrderList()) {
                turnOrder.add(fromProto(sc));
            }
            if (turnOrder.isEmpty()) {
                throw new SavegameFormatException("ordem de turno vazia");
            }

            List<Move> moves = new ArrayList<>();
            for (SavedMove sm : p.getMovesList()) {
                moves.add(
                        new Move(
                                fromProto(sm.getPlayer()),
                                new Edge(
                                        fromProto(sm.getOrientation()), sm.getRow(), sm.getCol())));
            }

            br.com.mss.tchow.save.proto.SaveMeta protoMeta = p.getMeta();
            SaveMeta meta =
                    new SaveMeta(
                            parseInstant(protoMeta.getCreatedAt()),
                            protoMeta.getAppVersion(),
                            optionalColor(protoMeta.getHumanColor()),
                            protoMeta.getAiLevel());

            return new Savegame(
                    version, new BoardSpec(width, height, turnOrder), MoveLog.of(moves), meta);
        } catch (SavegameFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            // Ex.: Edge com coordenada negativa. Qualquer surpresa é "formato inválido".
            throw new SavegameFormatException("save malformado: " + e.getMessage(), e);
        }
    }

    private static Instant parseInstant(String raw) throws SavegameFormatException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new SavegameFormatException("created_at inválido: " + raw, e);
        }
    }

    // --- enums (mapeamento explícito) -------------------------------------

    private static SaveColor toProto(PlayerColor color) {
        return switch (color) {
            case RED -> SaveColor.SAVE_COLOR_RED;
            case BLUE -> SaveColor.SAVE_COLOR_BLUE;
            case GREEN -> SaveColor.SAVE_COLOR_GREEN;
            case YELLOW -> SaveColor.SAVE_COLOR_YELLOW;
            case PINK -> SaveColor.SAVE_COLOR_PINK;
        };
    }

    /** Cor opcional (metadado de reabertura): valor ausente/desconhecido vira {@code null}. */
    private static PlayerColor optionalColor(SaveColor color) {
        return switch (color) {
            case SAVE_COLOR_RED -> PlayerColor.RED;
            case SAVE_COLOR_BLUE -> PlayerColor.BLUE;
            case SAVE_COLOR_GREEN -> PlayerColor.GREEN;
            case SAVE_COLOR_YELLOW -> PlayerColor.YELLOW;
            case SAVE_COLOR_PINK -> PlayerColor.PINK;
            case SAVE_COLOR_UNSPECIFIED, UNRECOGNIZED -> null;
        };
    }

    private static PlayerColor fromProto(SaveColor color) throws SavegameFormatException {
        return switch (color) {
            case SAVE_COLOR_RED -> PlayerColor.RED;
            case SAVE_COLOR_BLUE -> PlayerColor.BLUE;
            case SAVE_COLOR_GREEN -> PlayerColor.GREEN;
            case SAVE_COLOR_YELLOW -> PlayerColor.YELLOW;
            case SAVE_COLOR_PINK -> PlayerColor.PINK;
            case SAVE_COLOR_UNSPECIFIED, UNRECOGNIZED ->
                    throw new SavegameFormatException("cor inválida no save: " + color);
        };
    }

    private static SaveOrientation toProto(EdgeOrientation orientation) {
        return switch (orientation) {
            case HORIZONTAL -> SaveOrientation.SAVE_ORIENTATION_HORIZONTAL;
            case VERTICAL -> SaveOrientation.SAVE_ORIENTATION_VERTICAL;
        };
    }

    private static EdgeOrientation fromProto(SaveOrientation orientation)
            throws SavegameFormatException {
        return switch (orientation) {
            case SAVE_ORIENTATION_HORIZONTAL -> EdgeOrientation.HORIZONTAL;
            case SAVE_ORIENTATION_VERTICAL -> EdgeOrientation.VERTICAL;
            case SAVE_ORIENTATION_UNSPECIFIED, UNRECOGNIZED ->
                    throw new SavegameFormatException(
                            "orientação inválida no save: " + orientation);
        };
    }
}
