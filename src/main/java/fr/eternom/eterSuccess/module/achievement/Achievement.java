package fr.eternom.eterSuccess.module.achievement;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

/**
 * Un succès (achievements.yml) : ce qu'il compte (kind, restreint par match), ses niveaux (tiers : le compteur à
 * atteindre pour chaque niveau, de 1 à 5) et sa catégorie. Les textes sont dans lang/ (voir AchievementTexts).
 */
public record Achievement(String id, String category, Kind kind, Set<Material> blocks, Set<EntityType> entities,
                          boolean monsters, String tag, long[] tiers, Material icon, String subject,
                          List<Reward> rewards) {

    /** Ce qu'un succès compte. Les « états » (job, homes, money...) sont relus régulièrement, pas comptés. */
    public enum Kind {
        BREAK, HARVEST, KILL, FISH, BREED, ENCHANT, DEATH, PLAYTIME, RESOURCE, QUEST, CHALLENGE, SELL, BUY, DAILY_SERIES,
        JOB(true), HOMES(true), MONEY(true), CLAN(true), CLAN_LAND(true), PASS_TIER(true), DAILY(true);

        private final boolean state;

        Kind() {
            this(false);
        }

        Kind(boolean state) {
            this.state = state;
        }

        /** Valeur relue (le plus haut atteint est gardé), pas un compteur qu'on augmente. */
        public boolean isState() {
            return state;
        }
    }

    /** Récompense d'un niveau : argent, points, commandes de la console ({player}). */
    public record Reward(double money, int points, List<String> commands) {
    }

    public boolean matchesBlock(Material material) {
        return blocks.isEmpty() || blocks.contains(material);
    }

    public boolean matchesEntity(EntityType type, boolean isMonster) {
        if (monsters) {
            return isMonster;
        }
        return entities.isEmpty() || entities.contains(type);
    }

    /** tag : métier pour quest, daily/weekly pour challenge, catégorie de prise pour fish (fish ou treasure). */
    public boolean matchesTag(String value) {
        return tag == null || tag.equals(value);
    }

    /** Niveau atteint avec cette valeur (0 à tiers.length). */
    public int tierFor(long value) {
        int tier = 0;
        while (tier < tiers.length && value >= tiers[tier]) {
            tier++;
        }
        return tier;
    }

    public Reward reward(int tier) {
        return rewards.get(Math.min(tier, rewards.size()) - 1);
    }
}
