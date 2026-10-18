package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterEconomy.api.EconomyApi;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.economy.Money;
import com.google.gson.JsonObject;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.message.Numbers;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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
    private static final String UNLOCK = "unlock";

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
    /** États déjà signalés illisibles : une seule ligne dans la console par état (sinon une par joueur et par minute). */
    private final Set<Kind> unreadable = ConcurrentHashMap.newKeySet();

    /** Annonce publique d'un succès débloqué (à la place des progrès de Minecraft) : network, server ou none. */
    private final String announce;
    private final NetworkBus bus;

    public SuccessService(JavaPlugin plugin, SuccessStore store, List<Category> categories, Messages messages, BackButton backButton,
                          NetworkBus bus, String announce) {
        this.bus = bus;
        this.announce = announce.toLowerCase(Locale.ROOT);
        bus.on(UNLOCK, this::announce);
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

    /** Une action d'un joueur peut-être hors ligne ou ailleurs (ex : le vendeur d'une annonce) : comptée par son UUID. */
    public void countUuid(UUID player, Kind kind, long amount) {
        for (Achievement achievement : byKind.getOrDefault(kind, List.of())) {
            pending.compute(player, (uuid, counts) -> {
                Map<String, Long> map = counts == null ? new HashMap<>() : counts;
                map.merge(achievement.id(), amount, Long::sum);
                return map;
            });
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

    /** À l'arrêt du serveur, sur le thread principal : la progression en mémoire est enregistrée, rien ne se perd. */
    public void stop() {
        flushAll();
    }

    private void flushAll() {
        for (UUID uuid : List.copyOf(pending.keySet())) {
            flush(uuid);
            if (Bukkit.getPlayer(uuid) == null) {
                unlocked.remove(uuid); // compté hors ligne (vendeur d'une annonce) : rien à garder en mémoire
            }
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
        Tasks.async(plugin, () -> online.forEach(this::readStates), "Succès : lecture des états impossible");
    }

    /**
     * Relit tout de suite les états d'un joueur connecté, sans attendre la minute (ex : un home vient d'être posé, et
     * le joueur peut le supprimer avant la relecture suivante).
     */
    public void refreshStates(UUID uuid) {
        if (unlocked.containsKey(uuid)) {
            Tasks.async(plugin, () -> readStates(uuid), "Succès : lecture des états impossible pour " + uuid);
        }
    }

    /**
     * Les états d'un joueur, chacun à part : une lecture qui échoue (donnée abîmée, API d'un autre plugin qui a changé)
     * n'empêche ni les autres états ni les autres joueurs. Bloquant.
     */
    private void readStates(UUID uuid) {
        for (Map.Entry<Kind, List<Achievement>> entry : byKind.entrySet()) {
            if (!entry.getKey().isState()) {
                continue;
            }
            try {
                long value = States.read(entry.getKey(), uuid);
                if (value < 0) {
                    continue; // rien à lire ici (plugin absent)
                }
                for (Achievement achievement : entry.getValue()) {
                    check(uuid, achievement, store.raise(uuid, achievement.id(), value));
                }
            } catch (RuntimeException | LinkageError e) {
                if (unreadable.add(entry.getKey())) {
                    plugin.getLogger().warning("Succès : état " + entry.getKey() + " illisible (" + uuid + ", signalé une fois) : " + e);
                }
            }
        }
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
            if (!guided && !announce.equals("none") && !EterLib.get().getVanish().isVanished(uuid)) {
                JsonObject data = new JsonObject();
                data.addProperty("player", player.getName());
                data.addProperty("achievement", achievement.id());
                data.addProperty("tier", tier);
                announce(data);
                if (announce.equals("network")) {
                    bus.publish(UNLOCK, data);
                }
            }
            Bukkit.getPluginManager().callEvent(new AchievementUnlockEvent(player, achievement.id(), tier, reward.points()));
            if (guided) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> guide(player), 40);
            }
            publishTag(player);
        });
    }

    /** Annonce publique aux joueurs de ce serveur, chacun dans sa langue ; le niveau V a son propre message. */
    private void announce(JsonObject data) {
        Achievement achievement = byId.get(data.get("achievement").getAsString());
        if (achievement == null) {
            return; // succès absent de la config de ce serveur
        }
        int tier = data.get("tier").getAsInt();
        String key = tier >= 5 && achievement.tiers().length >= 5 ? "unlock.legend" : "unlock.public";
        for (Player receiver : Bukkit.getOnlinePlayers()) {
            receiver.sendMessage(messages.render(raw(receiver, key),
                    TagResolver.resolver(Placeholder.component("name", fullName(receiver, achievement, tier))),
                    "player", data.get("player").getAsString()));
        }
    }

    // ---------- Arrivée, départ, Premiers pas ----------

    /** Arrivée : charge les niveaux débloqués, puis (après quelques secondes) l'indice de la prochaine étape. */
    public void join(Player player) {
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, player, () -> store.tiers(uuid), tiers -> {
            unlocked.put(uuid, new ConcurrentHashMap<>(tiers));
            publishTag(player);
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
            // Oublié sur le thread principal, et seulement s'il n'est pas déjà revenu : une reconnexion rapide a pu
            // recharger ses niveaux pendant l'enregistrement, ils doivent rester (sinon ses états ne sont plus relus)
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.getPlayer(uuid) == null) {
                        unlocked.remove(uuid);
                    }
                });
            }
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

    // ---------- Sidebar ----------

    /**
     * Ligne des succès dans la sidebar d'EterTab (étiquette <tag_success> d'EterLib) : l'étape des Premiers pas tant
     * qu'ils ne sont pas finis, puis les points de succès. À l'arrivée et à chaque déblocage.
     */
    void publishTag(Player player) {
        UUID uuid = player.getUniqueId();
        Tasks.async(plugin, player, () -> store.points(uuid), points -> {
            Map<String, Integer> known = unlocked.getOrDefault(uuid, Map.of());
            String value = null;
            for (Category category : categories) {
                if (!category.guided()) {
                    continue;
                }
                List<Achievement> steps = category.achievements();
                long done = steps.stream().filter(step -> known.getOrDefault(step.id(), 0) > 0).count();
                if (done < steps.size()) {
                    String format = messages.raw(player, "sidebar.step");
                    value = format == null ? null : format.replace("<done>", String.valueOf(done))
                            .replace("<step>", String.valueOf(done)) // ancien nom, encore dans les lang/ déjà copiés
                            .replace("<steps>", String.valueOf(steps.size()));
                }
            }
            if (value == null) {
                String format = messages.raw(player, "sidebar.points");
                value = format == null ? "" : format.replace("<points>", String.valueOf(points));
            }
            EterLib.get().getPlayerTags().set(player, "success", value);
        }, () -> { });
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

    private void load(Player player, Consumer<View> then) {
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
                "target", Numbers.format(target));
    }

    static String roman(int tier) {
        return tier >= 1 && tier <= ROMAN.length ? ROMAN[tier - 1] : String.valueOf(tier);
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
