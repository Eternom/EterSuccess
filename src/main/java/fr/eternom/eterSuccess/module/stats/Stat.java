package fr.eternom.eterSuccess.module.stats;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Les statistiques de toujours d'un joueur, communes au réseau (table etersuccess_stats). Nom : lang/ > stats.stat.&lt;id&gt;.
 * Comptées comme les succès : en survie, blocs naturels seulement, cultures mûres ; PLAYTIME : minutes actives où le
 * joueur a regardé autour de lui ou parlé (AFK exclu).
 */
public enum Stat {

    PLAYTIME(Material.CLOCK),
    DEATHS(Material.SKELETON_SKULL),
    PLAYER_KILLS(Material.IRON_SWORD),
    MOB_KILLS(Material.ZOMBIE_HEAD),
    BLOCKS_MINED(Material.IRON_PICKAXE),
    CROPS(Material.WHEAT),
    FISH(Material.COD),
    BRED(Material.EGG);

    private final Material icon;

    Stat(Material icon) {
        this.icon = icon;
    }

    public Material icon() {
        return icon;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
