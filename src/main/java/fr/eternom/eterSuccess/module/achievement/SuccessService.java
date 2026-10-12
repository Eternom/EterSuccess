package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterEconomy.api.EconomyApi;
import fr.eternom.eterLib.helper.economy.Money;
import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterSuccess.api.AchievementUnlockEvent;
import fr.eternom.eterSuccess.module.achievement.Achievement.Kind;
import fr.eternom.eterSuccess.module.achievement.Achievement.Reward;
import fr.eternom.eterSuccess.module.achievement.AchievementCatalog.Category;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Les succès. Les actions (bloc naturel cassé, créature tuée, prise...) s'ajoutent en mémoire, puis partent en base
 * toutes les 20 s et au départ (une requête relative par succès). Les états (métier, homes, argent, clan, pass) sont
 * relus chaque minute chez les autres plugins. Un niveau atteint est débloqué par INSERT IGNORE : récompense donnée une
 * seule fois, même avec plusieurs serveurs. « Premiers pas » (catégorie guided) guide le joueur : à l'arrivée et après
 * chaque étape, l'indice de l'étape suivante.
 */
public class SuccessService {

    public static final String PERMISSION = "etersuccess.use";
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    private final JavaPlugin plugin;
    private final SuccessStore store;
    private final List<Category> categories;
    private final Map<String, Achievement> byId = new LinkedHashMap<>();
    private final Map<Kind, List<Achievement>> byKind = new EnumMap<>(Kind.class);
    private final Messages messages;
    private final BackButton backButton;
    /** Compteurs pas encore enregistrés : joueur -> succès -> quantité. */
    private final Map<UUID, Map<String, Long>> pending = new ConcurrentHashMap<>();
    /** Plus haut niveau débloqué de chaque succès, pour les joueurs connectés (évite les requêtes inutiles). */
    private final Map<UUID, Map<String, Integer>> unlocked = new ConcurrentHashMap<>();

    public SuccessService(JavaPlugin plugin, SuccessStore store, List<Category> categories, Messages messages, BackButton backButton) {
        this.plugin = plugin;
        this.store = store;
        this.categories = categories;
        this.messages = messages;
        this.backButton = backButton;
        for (Category category : categories) {
            for (Achievement achievement : category.achievements()) {
                byId.put(achievement.id(), achievement);
                byKind.computeIfAbsent(achievement.kind(), kind -> new ArrayList<>()).add(achievement);
            }
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushAll, 20 * 20, 20 * 20);
        Bukkit.getScheduler().runTaskTimer(plugin, this::pollStates, 20 * 30, 20 * 60);
    }

    public int count() {
        return byId.size();
    }

    // ---------- Comptage ----------

    /** Une action (thread principal) : chaque succès de ce kind qui la reconnaît avance de amount. */
    public void count(Player player, Kind kind, Predicate<Achievement> matches, long amount) {
        for (Achievement achievement : byKind.getOrDefault(kind, List.of())) {
            if (matches.test(achievement)) {
                // compute est atomique avec le remove de flush : aucune action perdue entre les deux
                pending.compute(player.getUniqueId(), (uuid, counts) -> {
                    Map<String, Long> map = counts == null ? new HashMap<>() : counts;
                    map.merge(achievement.id(), amount, Long::sum);
                    return map;
                });
            }
        }
    }

    public void countBlock(Player player, Kind kind, Material material) {
        count(player, kind, achievement -> achievement.matchesBlock(material), 1);
    }

    public void countEntity(Player player, Kind kind, EntityType type, boolean monster) {
        count(player, kind, achievement -> achievement.matchesEntity(type, monster), 1);
    }

    public void countTag(Player player, Kind kind, String tag) {
        count(player, kind, achievement -> achievement.matchesTag(tag), 1);
    }

    private void flushAll() {
        for (UUID uuid : List.copyOf(pending.keySet())) {
            flush(uuid);
        }
    }

    private void flush(UUID uuid) {
        Map<String, Long> counts = pending.remove(uuid);
        if (counts == null) {
            return;
        }
        counts.forEach((id, amount) -> {
            Achievement achievement = byId.get(id);
            try {
                check(uuid, achievement, store.add(uuid, id, amount));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Succès " + id + " non enregistré pour " + uuid + " : " + e.getMessage());
            }
        });
    }

    /** Chaque minute : relit les états des joueurs connectés (métier, homes, argent, clan, pass, /daily). */
    private void pollStates() {
        List<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).filter(unlocked::containsKey).toList();
        if (online.isEmpty()) {
            return;
        }
        Tasks.async(plugin, () -> {
            for (UUID uuid : online) {
                for (Map.Entry<Kind, List<Achievement>> entry : byKind.entrySet()) {
                    if (!entry.getKey().isState()) {
                        continue;
                    }
                    long value = States.read(entry.getKey(), uuid);
                    if (value < 0) {
                        continue; // rien à lire ici (plugin absent)
                    }
                    for (Achievement achievement : entry.getValue()) {
                        check(uuid, achievement, store.raise(uuid, achievement.id(), value));
                    }
                }
            }
        }, "Succès : lecture des états impossible");
    }

    /** Débloque les niveaux atteints et pas encore débloqués. Bloquant. */
    private void check(UUID uuid, Achievement achievement, long value) {
        int reached = achievement.tierFor(value);
        Map<String, Integer> known = unlocked.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>());
        int already = known.getOrDefault(achievement.id(), 0);
        for (int tier = already + 1; tier <= reached; tier++) {
            Reward reward = achievement.reward(tier);
            if (store.unlock(uuid, achievement.id(), tier, reward.points())) {
                reward(uuid, achievement, tier, reward);
            }
            known.merge(achievement.id(), tier, Math::max);
        }
    }

    /** Tâche de fond, juste après le déblocage : l'argent (même si le joueur est parti), puis le reste sur le thread principal. */
    private void reward(UUID uuid, Achievement achievement, int tier, Reward reward) {
        if (reward.money() > 0) {
            EconomyApi.get().ifPresent(economy -> economy.deposit(uuid, reward.money(), "EterSuccess · succès"));
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            String name = player == null ? Bukkit.getOfflinePlayer(uuid).getName() : player.getName();
            for (String command : reward.commands()) {
                if (name != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", name));
                }
            }
            if (player == null) {
                return;
            }
            boolean guided = isGuided(achievement);
            player.sendMessage(messages.prefix().append(messages.render(raw(player, guided ? "unlock.step" : "unlock.message"),
                    TagResolver.resolver(Placeholder.component("name", fullName(player, achievement, tier))),
                    "money", Money.format(reward.money()), "points", String.valueOf(reward.points()))));
            player.playSound(player, tier >= 5 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            if (tier >= 5 && achievement.tiers().length >= 5) {
                Component announce = messages.prefix().append(messages.render(raw(player, "unlock.legend"),
                        TagResolver.resolver(Placeholder.component("name", fullName(player, achievement, tier))),
                        "player", player.getName()));
                Bukkit.getOnlinePlayers().forEach(online -> online.sendMessage(announce));
            }
            Bukkit.getPluginManager().callEvent(new AchievementUnlockEvent(player, achievement.id(), tier, reward.points()));
            if (guided) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> guide(player), 40);
            }
        });
    }

    // ---------- Arrivée, départ, Premiers pas ----------

    /** Arrivée : charge les niveaux débloqués, puis (après quelques secondes) l'indice de la prochaine étape. */
    public void join(Player player) {
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, player, () -> store.tiers(uuid), tiers -> {
            unlocked.put(uuid, new ConcurrentHashMap<>(tiers));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    guide(player);
                }
            }, 20 * 6);
        }, () -> { });
    }

    public void quit(UUID uuid) {
        Tasks.async(plugin, () -> {
            flush(uuid);
            unlocked.remove(uuid);
        }, "Succès non enregistrés pour " + uuid);
    }

    /** L'étape suivante de « Premiers pas », avec un bouton vers le menu ; rien si tout est fait. */
    void guide(Player player) {
        Map<String, Integer> known = unlocked.getOrDefault(player.getUniqueId(), Map.of());
        for (Category category : categories) {
            if (!category.guided()) {
                continue;
            }
            List<Achievement> steps = category.achievements();
            for (int i = 0; i < steps.size(); i++) {
                Achievement step = steps.get(i);
                if (known.getOrDefault(step.id(), 0) > 0) {
                    continue;
                }
                messages.send(player, "guide.next", "step", String.valueOf(i + 1), "steps", String.valueOf(steps.size()),
                        "hint", messages.plain(player, "hint." + step.id()));
                Component button = messages.get(player, "guide.button")
                        .clickEvent(ClickEvent.runCommand("/succes"))
                        .hoverEvent(HoverEvent.showText(messages.get(player, "guide.button-hover")));
                player.sendMessage(messages.prefix().append(button));
                return;
            }
        }
    }

    private boolean isGuided(Achievement achievement) {
        return categories.stream().anyMatch(category -> category.guided() && category.id().equals(achievement.category()));
    }

    // ---------- Menus ----------

    /** Ce que montrent les menus : compteurs et niveaux du joueur, et ses points. */
    record View(Map<String, Long> values, Map<String, Integer> tiers, int points) {
    }

    public void open(Player player) {
        load(player, view -> player.openInventory(new SuccessMenu(this, player, view).getInventory()));
    }

    void openCategory(Player player, Category category, int page) {
        load(player, view -> player.openInventory(new CategoryMenu(this, player, view, category, page).getInventory()));
    }

    private void load(Player player, java.util.function.Consumer<View> then) {
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, player, () -> {
            flush(uuid); // les dernières actions comptent dans ce qu'on montre
            return new View(store.values(uuid), store.tiers(uuid), store.points(uuid));
        }, then, () -> messages.send(player, "error.generic"));
    }

    // ---------- Textes ----------

    /** Nom d'un succès : lang name.<id>, sinon son sujet (le nom du bloc ou de la créature, dans la langue du joueur). */
    Component name(Player viewer, Achievement achievement) {
        if (messages.raw(viewer, "name." + achievement.id()) != null) {
            return messages.get(viewer, "name." + achievement.id());
        }
        return subject(viewer, achievement);
    }

    /** « Nom III » (ou le nom seul pour un succès à un niveau). */
    Component fullName(Player viewer, Achievement achievement, int tier) {
        Component name = name(viewer, achievement);
        return achievement.tiers().length > 1 ? name.append(Component.text(" " + roman(tier))) : name;
    }

    /** Le sujet : lang subject.<subject>, sinon le nom traduit du premier bloc ou de la première créature. */
    Component subject(Player viewer, Achievement achievement) {
        if (achievement.subject() != null) {
            return messages.get(viewer, "subject." + achievement.subject());
        }
        if (!achievement.blocks().isEmpty()) {
            return Component.translatable(achievement.blocks().iterator().next().translationKey());
        }
        if (!achievement.entities().isEmpty()) {
            return Component.translatable(achievement.entities().iterator().next().translationKey());
        }
        return messages.get(viewer, "subject.any");
    }

    /** Description d'un niveau : lang kind.<kind> avec <target> et <subject>. */
    Component description(Player viewer, Achievement achievement, long target) {
        String raw = messages.raw(viewer, "description." + achievement.id());
        String key = raw != null ? "description." + achievement.id() : "kind." + achievement.kind().name().toLowerCase();
        return messages.render(raw(viewer, key), TagResolver.resolver(Placeholder.component("subject", subject(viewer, achievement))),
                "target", formatNumber(target));
    }

    static String roman(int tier) {
        return tier >= 1 && tier <= ROMAN.length ? ROMAN[tier - 1] : String.valueOf(tier);
    }

    /** 1 234 567 */
    static String formatNumber(long value) {
        return String.format(java.util.Locale.FRANCE, "%,d", value).replace(' ', ' ').replace(' ', ' ');
    }

    List<Category> categories() {
        return categories;
    }

    Messages messages() {
        return messages;
    }

    BackButton backButton() {
        return backButton;
    }

    JavaPlugin plugin() {
        return plugin;
    }

    String raw(Player player, String key) {
        String raw = messages.raw(player, key);
        return raw == null ? key : raw;
    }

    public int total() {
        return byId.values().stream().mapToInt(achievement -> achievement.tiers().length).sum();
    }
}
