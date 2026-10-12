package fr.eternom.eterSuccess.api;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Un niveau de succès vient d'être débloqué (thread principal), une seule fois : la base l'a enregistré et la
 * récompense est donnée. Pour les autres plugins (ex : classements).
 */
public class AchievementUnlockEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String achievement;
    private final int tier;
    private final int points;

    public AchievementUnlockEvent(Player player, String achievement, int tier, int points) {
        super(player);
        this.achievement = achievement;
        this.tier = tier;
        this.points = points;
    }

    /** Identifiant du succès (achievements.yml). */
    public String getAchievement() {
        return achievement;
    }

    /** Niveau débloqué (1 à 5). */
    public int getTier() {
        return tier;
    }

    /** Points de succès gagnés. */
    public int getPoints() {
        return points;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
