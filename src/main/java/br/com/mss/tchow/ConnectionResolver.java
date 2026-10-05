package br.com.mss.tchow;

import br.com.mss.tchow.app.ServerChoiceStore;
import br.com.mss.tchow.net.NetworkConfig;
import br.com.mss.tchow.net.config.ServerDirectory;
import br.com.mss.tchow.net.config.ServerPreset;
import java.util.Optional;

/**
 * Lógica pura de resolução do servidor ([E4.5-04]/[E4.5-06], ADR-0014/ADR-0017) — sem Swing, sem
 * rede, só decide a partir do que já foi lido em outro lugar (fácil de testar com "fakes" para cada
 * fonte).
 */
final class ConnectionResolver {

    private ConnectionResolver() {}

    /**
     * Servidor sugerido ao abrir o app: {@code --server=} explícito tem prioridade máxima; senão a
     * última escolha manual guardada em {@code savedChoice} ([E4.5-06]); senão o preset {@code
     * default} do {@code directory} (externo ou embutido); se nem isso existir, cai num {@code
     * localhost} de última instância — nunca lança.
     */
    static ServerPreset resolveDefault(
            LaunchOptions options, ServerDirectory directory, ServerChoiceStore savedChoice) {
        if (options.remoteHost() != null) {
            // --server=host:porta é sempre em claro (uso LAN/dev, não passa pelo Caddy) e nunca
            // oficial (quem sabe digitar host:porta de cor não é o fluxo de conta guiado).
            return new ServerPreset(
                    "linha de comando",
                    options.remoteHost(),
                    options.remotePort(),
                    false,
                    true,
                    false);
        }
        if (savedChoice != null) {
            Optional<ServerPreset> remembered = savedChoice.lastChoice();
            if (remembered.isPresent()) {
                return remembered.get();
            }
        }
        return directory
                .defaultPreset()
                .orElse(
                        new ServerPreset(
                                "localhost",
                                "localhost",
                                NetworkConfig.DEFAULT_PORT,
                                false,
                                true,
                                false));
    }
}
