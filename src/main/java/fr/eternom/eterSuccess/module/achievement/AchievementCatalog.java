package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterSuccess.module.achievement.Achievement.Kind;
import fr.eternom.eterSuccess.module.achievement.Achievement.Reward;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * achievements.yml (copié au premier démarrage) : les catégories, dans l'ordre du menu, et les succès, dans l'ordre de
 * leur catégorie. La catégorie « guided » (Premiers pas) se fait dans l'ordre, avec un indice pour l'étape suivante.
 */
public final class AchievementCatalog {

    /** Une catégorie du menu. guided : ses succès sont des étapes à faire dans l'ordre. */
    public record Category(String id, Material icon, boolean guided, List<Achievement> achievements) {
    }

    private static final Map<String, List<Tag<Material>>> GROUPS = Map.of(
            "#ores", List.of(Tag.COAL_ORES, Tag.IRON_ORES, Tag.COPPER_ORES, Tag.GOLD_ORES, Tag.REDSTONE_ORES,
                    Tag.LAPIS_ORES, Tag.DIAMOND_ORES, Tag.EMERALD_ORES),
            "#logs", List.of(Tag.LOGS),
            "#crops", List.of(Tag.CROPS));
    private static final Map<String, Set<Material>> EXTRA = Map.of(
            "#ores", Set.of(Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS),
            "#crops", Set.of(Material.NETHER_WART),
            "#stone", Set.of(Material.STONE, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.TUFF,
                    Material.CALCITE));

    private AchievementCatalog() {
    }

    /** tierRewards : récompense par défaut de chaque niveau (config.yml > tiers), 5 niveaux. */
    public static List<Category> load(JavaPlugin plugin, List<Reward> tierRewards) {
        File file = new File(plugin.getDataFolder(), "achievements.yml");
        if (!file.exists()) {
            plugin.saveResource("achievements.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Logger logger = plugin.getLogger();

        Map<String, List<Achievement>> byCategory = new LinkedHashMap<>();
        ConfigurationSection categories = yaml.getConfigurationSection("categories");
        if (categories != null) {
            categories.getKeys(false).forEach(id -> byCategory.put(id, new ArrayList<>()));
        }
        ConfigurationSection achievements = yaml.getConfigurationSection("achievements");
        if (achievements != null) {
            for (String id : achievements.getKeys(false)) {
                ConfigurationSection section = achievements.getConfigurationSection(id);
                Achievement achievement = section == null ? null : achievement(id, section, tierRewards, logger);
                if (achievement == null) {
                    continue;
                }
                List<Achievement> list = byCategory.get(achievement.category());
                if (list == null) {
                    logger.warning("achievements.yml : " + id + " : catégorie inconnue " + achievement.category());
                    continue;
                }
                list.add(achievement);
            }
        }
        List<Category> result = new ArrayList<>();
        byCategory.forEach((id, list) -> {
            if (list.isEmpty()) {
                return;
            }
            Material icon = Material.matchMaterial(categories.getString(id + ".icon", "BOOK"));
            result.add(new Category(id, icon == null || !icon.isItem() ? Material.BOOK : icon,
                    categories.getBoolean(id + ".guided"), List.copyOf(list)));
        });
        return List.copyOf(result);
    }

    private static Achievement achievement(String id, ConfigurationSection section, List<Reward> tierRewards, Logger logger) {
        Kind kind;
        try {
            kind = Kind.valueOf(section.getString("kind", "").toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            logger.warning("achievements.yml : " + id + " : kind inconnu");
            return null;
        }
        List<Long> values = section.getLongList("tiers");
        if (values.isEmpty() || values.size() > 5) {
            logger.warning("achievements.yml : " + id + " : il faut de 1 à 5 niveaux (tiers)");
            return null;
        }
        long[] tiers = values.stream().mapToLong(Long::longValue).toArray();

        // Dans l'ordre de « match » : le premier bloc ou la première créature donne son nom au succès (subject). Set.copyOf
        // ou un HashSet le changeraient d'un démarrage à l'autre (« Minerai de lapis-lazuli » ou « … des abîmes »).
        Set<Material> blocks = new LinkedHashSet<>();
        Set<EntityType> entities = new LinkedHashSet<>();
        boolean monsters = false;
        for (String entry : section.getStringList("match")) {
            String name = entry.trim().toLowerCase(Locale.ROOT);
            if (name.equals("#monsters")) {
                monsters = true;
            } else if (GROUPS.containsKey(name) || EXTRA.containsKey(name)) {
                GROUPS.getOrDefault(name, List.of()).forEach(tag -> blocks.addAll(tag.getValues()));
                blocks.addAll(EXTRA.getOrDefault(name, Set.of()));
            } else if (kind == Kind.KILL || kind == Kind.BREED) {
                NamespacedKey key = NamespacedKey.fromString(name);
                EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
                if (type == null) {
                    logger.warning("achievements.yml : " + id + " : créature inconnue ignorée : " + entry);
                } else {
                    entities.add(type);
                }
            } else {
                Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
                if (material == null) {
                    logger.warning("achievements.yml : " + id + " : bloc inconnu ignoré : " + entry);
                } else {
                    blocks.add(material);
                }
            }
        }

        List<Reward> rewards = new ArrayList<>(tierRewards);
        List<Map<?, ?>> custom = section.getMapList("rewards");
        for (int i = 0; i < custom.size() && i < rewards.size(); i++) {
            rewards.set(i, reward(custom.get(i), rewards.get(i)));
        }
        Material icon = Material.matchMaterial(section.getString("icon", "PAPER"));
        String tag = section.getString("tag");
        return new Achievement(id, section.getString("category", ""), kind, Collections.unmodifiableSet(blocks),
                Collections.unmodifiableSet(entities), monsters,
                tag == null ? null : tag.toLowerCase(Locale.ROOT), tiers, icon == null || !icon.isItem() ? Material.PAPER : icon,
                section.getString("subject"), List.copyOf(rewards));
    }

    /** Une récompense de achievements.yml ; ce qui n'y est pas reprend celle du niveau. */
    static Reward reward(Map<?, ?> map, Reward fallback) {
        double money = map.get("money") instanceof Number number ? number.doubleValue() : fallback.money();
        int points = map.get("points") instanceof Number number ? number.intValue() : fallback.points();
        List<String> commands = map.get("commands") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : fallback.commands();
        return new Reward(Math.max(0, money), Math.max(0, points), commands);
    }
}
