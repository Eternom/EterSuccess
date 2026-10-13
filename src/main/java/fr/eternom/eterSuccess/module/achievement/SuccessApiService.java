package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterSuccess.api.SuccessApi;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** L'API d'EterSuccess (SuccessApi.get()). */
public class SuccessApiService implements SuccessApi {

    private final SuccessStore store;
    private final SuccessService success;

    public SuccessApiService(SuccessStore store, SuccessService success) {
        this.store = store;
        this.success = success;
    }

    @Override
    public int points(UUID player) {
        return store.points(player);
    }

    @Override
    public int tier(UUID player, String achievement) {
        return store.tiers(player).getOrDefault(achievement, 0);
    }

    @Override
    public List<Ranked> top(int limit) {
        return store.top(limit);
    }

    @Override
    public int rank(UUID player) {
        return store.rank(player);
    }

    @Override
    public void openMenu(Player player) {
        success.open(player);
    }
}
