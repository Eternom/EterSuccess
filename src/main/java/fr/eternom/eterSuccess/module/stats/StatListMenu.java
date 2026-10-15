package fr.eternom.eterSuccess.module.stats;

import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu /stats, 6 lignes : tous les joueurs venus un jour, du plus grand temps de jeu au plus petit, 28 par page ;
 * ta propre fiche en haut, pages et retour en bas. Un clic sur une tête ouvre la fiche du joueur.
 */
public class StatListMenu implements Menu {

    private static final int MINE = 4;
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int PREVIOUS = 48;
    private static final int BACK = 49;
    private static final int NEXT = 50;

    private final StatService service;
    private final StatService.Page page;
    private final Inventory inventory;
    private final Map<Integer, StatService.Listed> shown = new HashMap<>();

    private StatListMenu(StatService service, Player viewer, StatService.Page page) {
        this.service = service;
        this.page = page;
        Messages messages = service.messages();
        this.inventory = Bukkit.createInventory(this, 54, messages.get(viewer, "stats.list.title",
                "page", String.valueOf(page.page() + 1), "pages", String.valueOf(page.pages())));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        inventory.setItem(MINE, Items.head(viewer.getPlayerProfile(), messages.get(viewer, "stats.list.mine"),
                List.of(messages.get(viewer, "stats.list.mine-lore"))));
        for (int i = 0; i < page.players().size(); i++) {
            StatService.Listed listed = page.players().get(i);
            int place = page.page() * StatService.PAGE_SIZE + i + 1;
            inventory.setItem(SLOTS[i], Items.head(listed.profile(),
                    messages.get(viewer, "stats.list.player", "place", String.valueOf(place), "player", listed.name()),
                    List.of(messages.get(viewer, "stats.list.playtime", "time", StatSheetMenu.hours(listed.playtime())),
                            Component.empty(), messages.get(viewer, "stats.list.open"))));
            shown.put(SLOTS[i], listed);
        }
        if (page.page() > 0) {
            inventory.setItem(PREVIOUS, Items.item(Material.ARROW, messages.get(viewer, "stats.list.previous"), List.of()));
        }
        if (page.page() < page.pages() - 1) {
            inventory.setItem(NEXT, Items.item(Material.ARROW, messages.get(viewer, "stats.list.next"), List.of()));
        }
        inventory.setItem(BACK, service.backButton().item(viewer));
    }

    /** Lit la page en tâche de fond, puis l'ouvre. */
    public static void open(StatService service, Player player, int page) {
        Tasks.async(service.plugin(), player, () -> service.page(page),
                result -> player.openInventory(new StatListMenu(service, player, result).getInventory()),
                () -> service.messages().send(player, "error.generic"));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            service.backButton().click(player);
        } else if (slot == PREVIOUS && page.page() > 0) {
            Sounds.page(player);
            open(service, player, page.page() - 1);
        } else if (slot == NEXT && page.page() < page.pages() - 1) {
            Sounds.page(player);
            open(service, player, page.page() + 1);
        } else if (slot == MINE) {
            Sounds.click(player);
            StatSheetMenu.open(service, player, player.getUniqueId(), player.getName());
        } else if (shown.containsKey(slot)) {
            Sounds.click(player);
            StatService.Listed listed = shown.get(slot);
            StatSheetMenu.open(service, player, listed.player(), listed.name());
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
