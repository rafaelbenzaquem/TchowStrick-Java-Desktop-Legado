package br.com.mss.tchow.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** Linhas de fala vs. linhas de "sistema" no chat ([E3-15]). */
class ChatPanelTest {

    @Test
    void falaELinhaDeSistemaConvivem() {
        ChatPanel chat = new ChatPanel();

        chat.append("Ana", "boa jogada", Color.RED);
        chat.system("você desfez sua jogada");

        String transcript = chat.transcript();
        assertTrue(transcript.contains("Ana: boa jogada"), transcript);
        assertTrue(transcript.contains("• você desfez sua jogada"), transcript);
        // a linha de sistema não vira "nick:"
        assertFalse(transcript.contains("sistema:"), transcript);
    }
}
