package br.com.mss.tchow;

import br.com.mss.tchow.app.ServerChoiceStore;
import br.com.mss.tchow.net.NetworkConfig;
import br.com.mss.tchow.net.config.ServerDirectory;
import br.com.mss.tchow.net.config.ServerPreset;
import java.util.Optional;
import java.util.function.UnaryOperator;

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
        return resolveDefault(
                options, directory, savedChoice, ServerDirectory::withOfficialIdentity);
    }

    /**
     * Como {@link #resolveDefault(LaunchOptions, ServerDirectory, ServerChoiceStore)}; {@code
     * upgrade} atualiza uma escolha salva antiga (oficial sem identidade MSS → preset oficial com
     * identidade), e a escolha atualizada é regravada.
     */
    static ServerPreset resolveDefault(
            LaunchOptions options,
            ServerDirectory directory,
            ServerChoiceStore savedChoice,
            UnaryOperator<ServerPreset> upgrade) {
        if (options.remoteHost() != null) {
            // --server=host:porta é sempre em claro (uso LAN/dev, não passa pelo Caddy) e nunca
            // oficial (quem sabe digitar host:porta de cor não é o fluxo de conta guiado).
            // --identity= (M1) acrescenta a identidade MSS a esse servidor; sem ela, nada muda.
            return new ServerPreset(
                    options.identity() == null ? "linha de comando" : "linha de comando (MSS)",
                    options.remoteHost(),
                    options.remotePort(),
                    false,
                    true,
                    false,
                    options.identity());
        }
        if (savedChoice != null) {
            Optional<ServerPreset> remembered = savedChoice.lastChoice();
            if (remembered.isPresent()) {
                ServerPreset current = upgrade.apply(remembered.get());
                if (!current.equals(remembered.get())) {
                    savedChoice.remember(current);
                }
                return current;
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
