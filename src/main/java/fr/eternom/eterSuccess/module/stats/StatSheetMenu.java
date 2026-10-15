package fr.eternom.eterSuccess.module.stats;

import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.message.Numbers;
import fr.eternom.eterLib.helper.task.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fiche d'un joueur, 6 lignes :
 * <pre>
 *  ▣ ▣ ▢ ▢ ☺ ▢ ▢ ▣ ▣     ☺ = le joueur : temps de jeu total, première venue
 *  ▣ · · · · · · · ▣
 *  ▢ · M · J · C · ▢     combat : morts, joueurs tués (et ratio), monstres tués
 *  ▢ · B C · P É · ▢     récolte : blocs minés, cultures, pêche, animaux élevés
 *  ▣ · · · · · · · ▣
 *  ▣ ▣ ▢ ▢ « ▢ ▢ ▣ ▣
 * </pre>
 */
class StatSheetMenu implements Menu {

    private static final int HEAD = 4;
    private static final int BACK = 49;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StatService service;
    private final Inventory inventory;

    private StatSheetMenu(StatService service, Player viewer, StatService.Sheet sheet) {
        this.service = service;
        Messages messages = service.messages();
        this.inventory = Bukkit.createInventory(this, 54, messages.get(viewer, "stats.sheet.title", "player", sheet.name()));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);

        String since = sheet.firstSeen() <= 0 ? "?" : DATE.format(Instant.ofEpochMilli(sheet.firstSeen()).atZone(ZoneId.systemDefault()));
        inventory.setItem(HEAD, Items.head(sheet.profile(), messages.get(viewer, "stats.sheet.player", "player", sheet.name()), List.of(
                messages.get(viewer, "stats.sheet.playtime", "time", hours(sheet.get(Stat.PLAYTIME))),
                messages.get(viewer, "stats.sheet.since", "date", since))));

        put(viewer, 20, Stat.DEATHS, sheet, List.of());
        long deaths = sheet.get(Stat.DEATHS);
        String ratio = String.format(Locale.FRANCE, "%.2f", sheet.get(Stat.PLAYER_KILLS) / (double) Math.max(1, deaths));
        put(viewer, 22, Stat.PLAYER_KILLS, sheet, List.of(messages.get(viewer, "stats.sheet.ratio", "ratio", ratio)));
        put(viewer, 24, Stat.MOB_KILLS, sheet, List.of());
        put(viewer, 29, Stat.BLOCKS_MINED, sheet, List.of());
        put(viewer, 30, Stat.CROPS, sheet, List.of());
        put(viewer, 32, Stat.FISH, sheet, List.of());
        put(viewer, 33, Stat.BRED, sheet, List.of());
        inventory.setItem(BACK, service.backButton().item(viewer));
    }

    /** Lit la fiche en tâche de fond, puis l'ouvre. */
    static void open(StatService service, Player viewer, UUID player, String name) {
        Tasks.async(service.plugin(), viewer, () -> service.sheet(player, name),
                sheet -> viewer.openInventory(new StatSheetMenu(service, viewer, sheet).getInventory()),
                () -> service.messages().send(viewer, "error.generic"));
    }

    private void put(Player viewer, int slot, Stat stat, StatService.Sheet sheet, List<Component> extra) {
        Messages messages = service.messages();
        List<Component> lore = new ArrayList<>();
        lore.add(messages.get(viewer, "stats.sheet.value", "value", Numbers.format(sheet.get(stat))));
        lore.addAll(extra);
        inventory.setItem(slot, Items.item(stat.icon(), messages.get(viewer, "stats.stat." + stat.id()), lore));
    }

    /** Minutes en « 12 h 34 ». */
    static String hours(long minutes) {
        return String.format(Locale.FRANCE, "%s h %02d", Numbers.format(minutes / 60), minutes % 60);
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            service.backButton().click(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
