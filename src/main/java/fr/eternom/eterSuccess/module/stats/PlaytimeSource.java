package fr.eternom.eterSuccess.module.stats;

import fr.eternom.eterLib.helper.ranking.RankingSource;

import java.util.List;
import java.util.UUID;

/**
 * Le temps de jeu actif total (minutes, /stats), offert aux autres plugins par EterLib sous l'identifiant
 * « playtime_total » : EterMarket et EterClan s'en servent pour réserver certains échanges aux joueurs qui ont assez
 * joué, sans dépendre d'EterSuccess (qui dépend déjà d'eux : un cycle, que Paper refuse).
 */
public class PlaytimeSource implements RankingSource {

    private final StatService stats;
    private final StatStore store;

    public PlaytimeSource(StatService stats, StatStore store) {
        this.stats = stats;
        this.store = store;
    }

    @Override
    public String id() {
        return "playtime_total";
    }

    @Override
    public List<Ranked> top(int limit) {
        return store.page(0, limit).stream().map(entry -> new Ranked(entry.player(), entry.playtime())).toList();
    }

    @Override
    public long[] of(UUID player) {
        long minutes = stats.playtimeMinutes(player);
        return new long[]{minutes > 0 ? store.rankOf(minutes) : 0, minutes};
    }
}
