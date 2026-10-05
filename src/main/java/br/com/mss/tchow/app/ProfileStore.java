package br.com.mss.tchow.app;

import java.util.List;
import java.util.Optional;

/**
 * Guarda os perfis locais e qual está ativo. A implementação atual ({@link LocalProfileStore}) é
 * por dispositivo; na E6 o perfil passa a ligar-se a uma conta no servidor.
 */
public interface ProfileStore {

    /** Todos os perfis do dispositivo, em ordem de criação. */
    List<PlayerProfile> list();

    /** O perfil ativo, se já houver algum. */
    Optional<PlayerProfile> active();

    Optional<PlayerProfile> find(PlayerId id);

    /** Cria um perfil novo (id de convidado novo), que passa a ser o ativo. */
    PlayerProfile create(String displayName);

    /** Marca {@code id} como ativo. Ignora ids desconhecidos. */
    void setActive(PlayerId id);
}
