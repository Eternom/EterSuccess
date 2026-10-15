package fr.eternom.eterSuccess.module.stats;

import fr.eternom.eterLib.helper.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /stats : la liste des joueurs ; /stats &lt;joueur&gt; : sa fiche (même hors ligne). */
public class StatsCommand implements TabExecutor {

    private final StatService service;

    public StatsCommand(StatService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.messages().send(sender, "command.players-only");
            return true;
        }
        if (args.length == 0) {
            StatListMenu.open(service, player, 0);
            return true;
        }
        String name = args[0];
        Tasks.async(service.plugin(), player, () -> service.sheet(name), sheet -> sheet.ifPresentOrElse(
                found -> StatSheetMenu.open(service, player, found.player(), found.name()),
                () -> service.messages().send(player, "player.unknown", "player", name)),
                () -> service.messages().send(player, "error.generic"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String start = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(start)).toList();
    }
}
