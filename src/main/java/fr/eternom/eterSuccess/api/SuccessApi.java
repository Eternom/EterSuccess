package fr.eternom.eterSuccess.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce qu'EterSuccess offre aux autres plugins. Personne d'autre ne lit les tables etersuccess_* : on demande ici.
 * <pre>
 *     // compileOnly("com.github.Eternom:EterSuccess:&lt;tag&gt;") ; plugin.yml : softdepend: [EterSuccess]
 * </pre>
 */
public interface SuccessApi {

    /** L'API d'EterSuccess si le plugin tourne sur ce serveur. */
    static Optional<SuccessApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(SuccessApi.class));
    }

    /** Points de succès du joueur (somme des niveaux débloqués). Bloquant (base). */
    int points(UUID player);

    /** Niveau débloqué d'un succès (0 : aucun). Bloquant (base). */
    int tier(UUID player, String achievement);

    /** Un joueur et ses points dans le classement. */
    record Ranked(UUID player, long points) {
    }

    /** Les joueurs qui ont le plus de points de succès, du premier au dernier. Bloquant (base). */
    List<Ranked> top(int limit);

    /** Place du joueur dans ce classement (1 = premier), 0 sans aucun succès. Bloquant (base). */
    int rank(UUID player);

    /** Le menu /succes. Thread principal. */
    void openMenu(Player player);
}
