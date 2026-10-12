package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterSuccess.module.achievement.AchievementCatalog.Category;
import fr.eternom.eterSuccess.module.achievement.SuccessService.View;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu /succes, 6 lignes : le joueur (points, niveaux débloqués) en haut, les catégories au milieu (Premiers pas en
 * premier), retour en bas.
 */
class SuccessMenu implements Menu {

    private static final int INFO = 4;
    private static final int[] SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
    private static final int BACK = 49;

    private final SuccessService service;
    private final Player viewer;
    private final Inventory inventory;
    private final List<Category> shown = new ArrayList<>();

    SuccessMenu(SuccessService service, Player viewer, View view) {
        this.service = service;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, text("menu.title"));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);

        int unlocked = view.tiers().values().stream().mapToInt(Integer::intValue).sum();
        inventory.setItem(INFO, Items.head(viewer.getPlayerProfile(), text("menu.player", "player", viewer.getName()), List.of(
                text("menu.points", "points", String.valueOf(view.points())),
                text("menu.unlocked", "count", String.valueOf(unlocked), "total", String.valueOf(service.total())))));

        List<Category> categories = service.categories();
        for (int i = 0; i < categories.size() && i < SLOTS.length; i++) {
            Category category = categories.get(i);
            int done = 0;
            int total = 0;
            for (Achievement achievement : category.achievements()) {
                done += view.tiers().getOrDefault(achievement.id(), 0);
                total += achievement.tiers().length;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(text("menu.category.progress", "done", String.valueOf(done), "total", String.valueOf(total)));
            if (category.guided()) {
                lore.add(text(done >= total ? "menu.category.guided-done" : "menu.category.guided"));
            }
            lore.add(Component.empty());
            lore.add(text("menu.category.open"));
            inventory.setItem(SLOTS[i], Items.item(category.icon(), text("category." + category.id()), lore,
                    category.guided() && done < total));
            shown.add(category);
        }
        inventory.setItem(BACK, service.backButton().item(viewer));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            service.backButton().click(player);
            return;
        }
        for (int i = 0; i < shown.size(); i++) {
            if (SLOTS[i] == slot) {
                Sounds.click(player);
                service.openCategory(player, shown.get(i), 0);
                return;
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private Component text(String key, String... placeholders) {
        return service.messages().get(viewer, key, placeholders);
    }
}
