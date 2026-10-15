package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterSuccess.module.stats.Stat;
import fr.eternom.eterSuccess.module.stats.StatService;
import fr.eternom.eterSuccess.module.achievement.Achievement.Kind;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ce qui fait avancer les succès. En survie seulement : un bloc posé par un joueur ne compte pas (EterLib
 * PlacedBlocks), une culture doit être mûre, une créature doit être tuée par le joueur, le temps de jeu doit être actif.
 * Les mêmes actions alimentent les statistiques de toujours (/stats), plus les morts et les joueurs tués.
 */
public class SuccessListener implements Listener {

    /** Prises de pêche qui sont des « trésors » (le reste : des poissons ou des déchets). */
    private static final Set<Material> TREASURES = Set.of(Material.ENCHANTED_BOOK, Material.NAME_TAG, Material.SADDLE,
            Material.NAUTILUS_SHELL, Material.BOW, Material.FISHING_ROD);
    private static final Set<Material> FISHES = Set.of(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);

    private final JavaPlugin plugin;
    private final SuccessService success;
    private final StatService stats;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    /** Dernier regard ou message : le temps de jeu des statistiques ne compte que les minutes où le joueur a regardé ou parlé. */
    private final Map<UUID, Long> lastLook = new ConcurrentHashMap<>();

    public SuccessListener(JavaPlugin plugin, SuccessService success, StatService stats) {
        this.plugin = plugin;
        this.success = success;
        this.stats = stats;
        // Chaque minute : une minute de jeu pour ceux qui ont bougé, agi ou parlé pendant la minute
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long since = System.currentTimeMillis() - 60_000;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (lastActive.getOrDefault(player.getUniqueId(), 0L) >= since) {
                    success.count(player, Kind.PLAYTIME, achievement -> true, 1);
                }
                if (lastLook.getOrDefault(player.getUniqueId(), 0L) >= since) {
                    stats.add(player, Stat.PLAYTIME);
                }
            }
        }, 20 * 60, 20 * 60);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        success.join(player);
        // Le monde ressource : une visite par arrivée
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && States.isResourceServer()) {
                success.count(player, Kind.RESOURCE, achievement -> true, 1);
            }
        }, 20);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastActive.remove(event.getPlayer().getUniqueId());
        lastLook.remove(event.getPlayer().getUniqueId());
        stats.quit(event.getPlayer().getUniqueId());
        success.quit(event.getPlayer().getUniqueId());
    }

    /** HIGH : après les protections (claims), avant que la note « posé » soit retirée (MONITOR). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Material type = event.getBlock().getType();
        if (event.getBlock().getBlockData() instanceof Ageable crop && (Tag.CROPS.isTagged(type) || type == Material.NETHER_WART)) {
            if (crop.getAge() >= crop.getMaximumAge()) {
                success.countBlock(player, Kind.HARVEST, type);
                stats.add(player, Stat.CROPS);
            }
            return;
        }
        if (!EterLib.get().getPlacedBlocks().isPlaced(event.getBlock())) {
            success.countBlock(player, Kind.BREAK, type);
            stats.add(player, Stat.BLOCKS_MINED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (event.getEntity() instanceof Player victim) {
            if (victim != killer) {
                stats.add(killer, Stat.PLAYER_KILLS);
            }
            return;
        }
        success.countEntity(killer, Kind.KILL, event.getEntityType(), event.getEntity() instanceof Enemy);
        if (event.getEntity() instanceof Enemy) {
            stats.add(killer, Stat.MOB_KILLS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        success.count(event.getPlayer(), Kind.DEATH, achievement -> true, 1);
        stats.add(event.getPlayer(), Stat.DEATHS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) {
            return;
        }
        Material caught = item.getItemStack().getType();
        String tag = TREASURES.contains(caught) ? "treasure" : FISHES.contains(caught) ? "fish" : "junk";
        stats.add(event.getPlayer(), Stat.FISH);
        // tag absent (null) : toutes les prises ; sinon seulement la catégorie
        success.count(event.getPlayer(), Kind.FISH, achievement -> achievement.matchesTag(tag)
                && (achievement.blocks().isEmpty() || achievement.blocks().contains(caught)), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            success.countEntity(player, Kind.BREED, event.getEntityType(), false);
            stats.add(player, Stat.BRED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        success.count(event.getEnchanter(), Kind.ENCHANT, achievement -> true, 1);
    }

    // ---------- Activité (temps de jeu) ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock() || event.hasChangedOrientation()) {
            lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
        if (event.hasChangedOrientation()) {
            lastLook.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        lastActive.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        lastLook.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
}
