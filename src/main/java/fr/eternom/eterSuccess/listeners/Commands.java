package fr.eternom.eterSuccess.listeners;

import fr.eternom.eterSuccess.Main;
import fr.eternom.eterSuccess.module.stats.StatsCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public class Commands {

    public Commands(Main main) {
        PluginCommand success = Objects.requireNonNull(main.getCommand("succes"), "Commande absente du plugin.yml : succes");
        success.setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                main.getSuccess().open(player);
            } else {
                main.getMessages().send(sender, "command.players-only");
            }
            return true;
        });
        success.setTabCompleter((sender, command, label, args) -> List.of());

        StatsCommand stats = new StatsCommand(main.getStats());
        PluginCommand statsCommand = Objects.requireNonNull(main.getCommand("stats"), "Commande absente du plugin.yml : stats");
        statsCommand.setExecutor(stats);
        statsCommand.setTabCompleter(stats);
    }
}
