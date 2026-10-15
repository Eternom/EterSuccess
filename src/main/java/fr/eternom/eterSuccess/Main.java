package fr.eternom.eterSuccess;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.ranking.RankingSource;
import fr.eternom.eterSuccess.api.SuccessApi;
import fr.eternom.eterSuccess.listeners.Commands;
import fr.eternom.eterSuccess.listeners.Events;
import fr.eternom.eterSuccess.module.achievement.Achievement.Reward;
import fr.eternom.eterSuccess.module.achievement.AchievementCatalog;
import fr.eternom.eterSuccess.module.achievement.SuccessApiService;
import fr.eternom.eterSuccess.module.achievement.SuccessRanking;
import fr.eternom.eterSuccess.module.achievement.SuccessService;
import fr.eternom.eterSuccess.module.achievement.SuccessStore;
import fr.eternom.eterSuccess.module.stats.StatService;
import fr.eternom.eterSuccess.module.stats.StatStore;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Les succès du réseau (ceux de Minecraft sont désactivés par EterSync) : « Premiers pas » pour guider les nouveaux
 * joueurs, puis des centaines de niveaux à débloquer (minage, bois, cultures, pêche, chasse, métiers, défis...).
 */
public final class Main extends JavaPlugin {

    /** PlacedBlocks (blocs posés par un joueur) depuis 1.11.0, RankingSource (classement des points) depuis 1.12.0. */
    private static final String REQUIRED_ETERLIB = "1.16.0";
    private static final String TABLE_PREFIX = "etersuccess_";

    private Messages messages;
    private SuccessService success;
    private StatService stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            if (!EterLib.requireVersion(this, REQUIRED_ETERLIB)) {
                return;
            }
        } catch (LinkageError tooOld) {
            getLogger().severe("EterLib " + REQUIRED_ETERLIB + " ou plus récent est nécessaire.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EterLib lib = EterLib.get();
        messages = lib.messages(this, "en_us", "fr_fr");
        SuccessStore store = new SuccessStore(lib.database(TABLE_PREFIX));
        success = new SuccessService(this, store, AchievementCatalog.load(this, tierRewards()), messages,
                lib.backButton(getConfig().getString("menus.back-command", "")),
                lib.network(this, "etersuccess", messages), getConfig().getString("announce", "network"));
        stats = new StatService(this, new StatStore(lib.database(TABLE_PREFIX)), lib.getPlayers(), messages,
                lib.backButton(getConfig().getString("menus.back-command", "")));
        getLogger().info(success.count() + " succès chargés (" + success.total() + " niveaux)");
        getServer().getServicesManager().register(SuccessApi.class, new SuccessApiService(store, success, stats), this, ServicePriority.Normal);
        // Classement des points, lu par EterReward sans qu'il dépende d'EterSuccess (EterLib RankingSource)
        getServer().getServicesManager().register(RankingSource.class,
                new SuccessRanking(store), this, ServicePriority.Normal);

        new Commands(this);
        new Events(this);
        success.start();
    }

    /** Récompense par défaut de chaque niveau (config.yml > tiers.1 à 5). */
    private List<Reward> tierRewards() {
        List<Reward> rewards = new ArrayList<>();
        for (int tier = 1; tier <= 5; tier++) {
            String path = "tiers." + tier + ".";
            rewards.add(new Reward(Math.max(0, getConfig().getDouble(path + "money")), Math.max(0, getConfig().getInt(path + "points")),
                    getConfig().getStringList(path + "commands")));
        }
        return rewards;
    }

    /** Tout ce qui n'est pas encore enregistré, avant l'arrêt. */
    @Override
    public void onDisable() {
        if (success != null) {
            success.stop();
        }
        if (stats != null) {
            stats.flushAllNow();
        }
    }

    public StatService getStats() {
        return stats;
    }

    public Messages getMessages() {
        return messages;
    }

    public SuccessService getSuccess() {
        return success;
    }
}
