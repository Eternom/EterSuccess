package fr.eternom.eterSuccess.listeners;

import fr.eternom.eterSuccess.Main;
import fr.eternom.eterSuccess.module.achievement.NetworkListeners;
import fr.eternom.eterSuccess.module.achievement.SuccessListener;
import org.bukkit.plugin.PluginManager;

public class Events {

    public Events(Main main) {
        PluginManager plugins = main.getServer().getPluginManager();
        plugins.registerEvents(new SuccessListener(main, main.getSuccess(), main.getStats()), main);
        // Événements des autres plugins : seulement s'ils sont là (leurs classes n'existent pas sinon)
        if (plugins.isPluginEnabled("EterMarket")) {
            plugins.registerEvents(new NetworkListeners.Quests(main.getSuccess()), main);
            plugins.registerEvents(new NetworkListeners.Auctions(main.getSuccess()), main);
        }
        if (plugins.isPluginEnabled("EterReward")) {
            plugins.registerEvents(new NetworkListeners.Challenges(main.getSuccess()), main);
            plugins.registerEvents(new NetworkListeners.Daily(main.getSuccess()), main);
        }
    }
}
