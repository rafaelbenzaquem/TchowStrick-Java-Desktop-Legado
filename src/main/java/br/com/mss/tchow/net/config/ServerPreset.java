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
 */
public record ServerPreset(
        String name, String host, int port, boolean tls, boolean isDefault, boolean official) {}
