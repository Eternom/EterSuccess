package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterLib.helper.economy.Money;
import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterSuccess.module.achievement.Achievement.Reward;
import fr.eternom.eterSuccess.module.achievement.AchievementCatalog.Category;
import fr.eternom.eterSuccess.module.achievement.SuccessService.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Les succès d'une catégorie, 28 par page : niveau atteint (étoiles), avancée vers le prochain niveau, sa récompense.
 * Pour Premiers pas : les étapes dans l'ordre, avec leur indice.
 */
class CategoryMenu implements Menu {

    private static final int PER_PAGE = 28;
    private static final int PREVIOUS = 45;
    private static final int BACK = 49;
    private static final int NEXT = 53;
    private static final int BAR = 10;

    private final SuccessService service;
    private final Player viewer;
    private final View view;
    private final Category category;
    private final int page;
    private final int pages;
    private final Inventory inventory;

    CategoryMenu(SuccessService service, Player viewer, View view, Category category, int page) {
        this.service = service;
        this.viewer = viewer;
        this.view = view;
        this.category = category;
        this.pages = Math.max(1, (category.achievements().size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.clamp(page, 0, pages - 1);
        this.inventory = Bukkit.createInventory(this, 54, text("menu.category-title", "category",
                service.messages().plain(viewer, "category." + category.id())));
        Frame.draw(inventory, Material.ORANGE_STAINED_GLASS_PANE);
        List<Achievement> achievements = category.achievements();
        int slot = 10;
        for (int i = this.page * PER_PAGE; i < achievements.size() && i < (this.page + 1) * PER_PAGE; i++) {
            inventory.setItem(slot, item(achievements.get(i), i + 1));
            slot += slot % 9 == 7 ? 3 : 1; // colonnes 1 à 7
        }
        if (this.page > 0) {
            inventory.setItem(PREVIOUS, Items.item(Material.ARROW, text("menu.previous"), List.of()));
        }
        if (this.page < pages - 1) {
            inventory.setItem(NEXT, Items.item(Material.ARROW, text("menu.next"), List.of()));
        }
        inventory.setItem(BACK, Items.item(Material.ARROW, text("menu.back"), List.of()));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            Sounds.click(player);
            service.open(player);
        } else if (slot == PREVIOUS && page > 0 || slot == NEXT && page < pages - 1) {
            Sounds.page(player);
            player.openInventory(new CategoryMenu(service, viewer, view, category, page + (slot == NEXT ? 1 : -1)).getInventory());
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private ItemStack item(Achievement achievement, int number) {
        int tier = view.tiers().getOrDefault(achievement.id(), 0);
        int max = achievement.tiers().length;
        long value = view.values().getOrDefault(achievement.id(), 0L);
        boolean complete = tier >= max;
        List<Component> lore = new ArrayList<>();
        if (max > 1) {
            lore.add(text("menu.stars", "stars", "★".repeat(tier) + "☆".repeat(max - tier)));
        }
        if (complete) {
            lore.add(service.description(viewer, achievement, achievement.tiers()[max - 1]));
            lore.add(text("menu.complete"));
        } else {
            long target = achievement.tiers()[tier];
            lore.add(service.description(viewer, achievement, target));
            if (!achievement.kind().isState() || target > 1) {
                long shown = Math.min(value, target);
                int filled = (int) Math.floor(BAR * (double) shown / target);
                lore.add(text("menu.progress", "bar", "■".repeat(filled) + "□".repeat(BAR - filled),
                        "value", SuccessService.formatNumber(shown), "target", SuccessService.formatNumber(target)));
            }
            Reward reward = achievement.reward(tier + 1);
            lore.add(text("menu.reward", "money", Money.format(reward.money()), "points", String.valueOf(reward.points())));
            if (category.guided()) {
                lore.add(Component.empty());
                lore.add(text("menu.hint", "hint", service.messages().plain(viewer, "hint." + achievement.id())));
            }
        }
        Component name = category.guided()
                ? service.messages().render(service.raw(viewer, "menu.step-name"),
                TagResolver.resolver(Placeholder.component("name", service.name(viewer, achievement))), "step", String.valueOf(number))
                : tier > 0 ? service.fullName(viewer, achievement, tier) : service.name(viewer, achievement);
        // L'icône reste celle du succès (on voit ce qu'il faut faire) ; il brille une fois complet
        ItemStack item = Items.item(achievement.icon(), name, lore, complete);
        item.setAmount(Math.max(1, tier));
        return item;
    }

    private Component text(String key, String... placeholders) {
        return service.messages().get(viewer, key, placeholders);
    }
}
