package br.com.mss.tchow.net.config;

/**
 * Um servidor conhecido em {@code servers.json} ([E4.5-01], ADR-0014). {@code isDefault} é o
 * servidor sugerido quando nada mais (CLI, descoberta em LAN) decidiu por outro. {@code tls} diz se
 * a conexão deve usar {@code TlsChannelCredentials} (produção, atrás do Caddy, [E5-00]/[E5-02]) em
 * vez de texto claro (LAN/dev — descoberta local e {@code --server=} continuam sempre em claro).
 *
 * <p>{@code official} ({@code [E6-13]}, docs/PLANO.md §E6 — matriz de acesso) é um campo próprio,
 * não inferido do {@code name}: um preset renomeado, um {@code servers.json} externo mal montado,
 * ou outro preset também chamado "Oficial" não bastam pra exigir conta oficial — só o servidor
 * marcado {@code true} no JSON exige. Presets criados pelo próprio cliente (descoberta em LAN,
 * "Endereço personalizado…", `--server=`) nunca são oficiais.
 *
 * <p>{@code identity} (MSSIdentity M4-04, M1 deste repositório) é opcional: quando presente, o
 * jogador entra com a conta MSS nesse destino e o {@code authorization} das chamadas de jogo leva o
 * acesso de jogo da identidade (audiência {@code tchowstrick}); o fluxo de conta oficial antigo não
 * é usado nesse servidor. {@code null} mantém o comportamento anterior.
 */
public record ServerPreset(
        String name,
        String host,
        int port,
        boolean tls,
        boolean isDefault,
        boolean official,
        IdentityTarget identity) {

    /** Servidor sem identidade MSS (comportamento anterior ao M1). */
    public ServerPreset(
            String name, String host, int port, boolean tls, boolean isDefault, boolean official) {
        this(name, host, port, tls, isDefault, official, null);
    }

    /** {@code true} se este servidor usa a identidade MSS para as credenciais de jogo. */
    public boolean usesMssIdentity() {
        return identity != null;
    }
}
