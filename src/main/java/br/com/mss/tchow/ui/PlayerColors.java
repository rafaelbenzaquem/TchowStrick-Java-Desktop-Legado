package br.com.mss.tchow.ui;

import br.com.mss.tchow.domain.PlayerColor;
import java.awt.Color;

/** Mapeia cada {@link PlayerColor} do domínio para uma cor concreta do AWT. */
public final class PlayerColors {

    private PlayerColors() {}

    public static Color awt(PlayerColor color) {
        return switch (color) {
            case RED -> new Color(0xD3, 0x2F, 0x2F);
            case BLUE -> new Color(0x19, 0x76, 0xD2);
            case GREEN -> new Color(0x38, 0x8E, 0x3C);
            case YELLOW -> new Color(0xF9, 0xA8, 0x25);
            case PINK -> new Color(0xC2, 0x18, 0x5B);
        };
    }

    /** Versão translúcida, para o preenchimento de quadros. */
    public static Color fill(PlayerColor color) {
        Color base = awt(color);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), 70);
    }
}
