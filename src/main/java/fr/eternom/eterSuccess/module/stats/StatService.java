package fr.eternom.eterSuccess.module.stats;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les statistiques de toujours : comptées en mémoire sur le thread principal, enregistrées toutes les 20 s et au
 * départ du joueur (une requête par statistique qui a bougé), lues pour /stats.
 */
public class StatService {

    /** Joueurs par page de la liste (4 lignes de 7). */
    public static final int PAGE_SIZE = 28;

    /** Une ligne de la liste : le joueur, son profil (tête) et son temps de jeu. */
    public record Listed(UUID player, String name, PlayerProfile profile, long playtime) {
    }

    public record Page(List<Listed> players, int page, int pages) {
    }

    /** La fiche d'un joueur : son nom, sa première venue (ms, 0 si inconnue) et ses statistiques. */
    public record Sheet(UUID player, String name, PlayerProfile profile, long firstSeen, Map<Stat, Long> stats) {

        public long get(Stat stat) {
            return stats.getOrDefault(stat, 0L);
        }
    }

    private final JavaPlugin plugin;
    private final StatStore store;
    private final PlayerDirectory players;
    private final Messages messages;
    private final BackButton backButton;
    private final Map<UUID, Map<Stat, Long>> pending = new ConcurrentHashMap<>();

    public StatService(JavaPlugin plugin, StatStore store, PlayerDirectory players, Messages messages, BackButton backButton) {
        this.plugin = plugin;
        this.store = store;
        this.players = players;
        this.messages = messages;
        this.backButton = backButton;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushAll, 20 * 20, 20 * 20);
    }

    /** Thread principal. */
    public void add(Player player, Stat stat) {
        pending.compute(player.getUniqueId(), (uuid, counts) -> {
            Map<Stat, Long> map = counts == null ? new EnumMap<>(Stat.class) : counts;
            map.merge(stat, 1L, Long::sum);
            return map;
        });
    }

    public void quit(UUID player) {
        Tasks.async(plugin, () -> flush(player), "Statistiques non enregistrées pour " + player);
    }

    /** À l'arrêt du serveur, sur le thread principal : rien ne doit se perdre. */
    public void flushAllNow() {
        flushAll();
    }

    private void flushAll() {
        List.copyOf(pending.keySet()).forEach(this::flush);
    }

    private void flush(UUID player) {
        Map<Stat, Long> counts = pending.remove(player);
        if (counts != null) {
            counts.forEach((stat, amount) -> store.add(player, stat, amount));
        }
    }

    /** Bloquant. Pages comptées à partir de 0 ; une page trop loin est ramenée à la dernière. */
    Page page(int wanted) {
        int pages = Math.max(1, (store.players() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(wanted, 0, pages - 1);
        List<Listed> listed = new ArrayList<>();
        for (StatStore.Entry entry : store.page(page * PAGE_SIZE, PAGE_SIZE)) {
            String name = players.get(entry.player()).map(PlayerDirectory.NetworkPlayer::name).orElse("?");
            listed.add(new Listed(entry.player(), name, profile(entry.player(), name), entry.playtime()));
        }
        return new Page(listed, page, pages);
    }

    /** Bloquant. Vide si le joueur n'est jamais venu. */
    Optional<Sheet> sheet(String name) {
        return players.find(name).map(found -> sheet(found.uuid(), found.name(), found.firstSeen()));
    }

    /** Bloquant. */
    Sheet sheet(UUID player, String name) {
        return sheet(player, name, players.get(player).map(PlayerDirectory.NetworkPlayer::firstSeen).orElse(0L));
    }

    private Sheet sheet(UUID player, String name, long firstSeen) {
        Map<Stat, Long> stats = store.of(player);
        // Ce qui n'est pas encore enregistré compte aussi
        pending.getOrDefault(player, Map.of()).forEach((stat, amount) -> stats.merge(stat, amount, Long::sum));
        return new Sheet(player, name, profile(player, name), firstSeen, stats);
    }

    /** Profil avec le skin (lu chez Mojang au besoin, mis en cache par le serveur). Bloquant. */
    private static PlayerProfile profile(UUID player, String name) {
        PlayerProfile profile = Bukkit.createProfile(player, name);
        try {
            profile.complete(true);
        } catch (RuntimeException e) {
            // pas de skin : tête par défaut
        }
        return profile;
    }

    JavaPlugin plugin() {
        return plugin;
    }

    Messages messages() {
        return messages;
    }

    BackButton backButton() {
        return backButton;
    }
}
