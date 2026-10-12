package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterLib.helper.ranking.RankingSource;

import java.util.List;
import java.util.UUID;

/** Classement des points de succès, offert aux autres plugins (EterReward l'affiche dans /classement). */
public class SuccessRanking implements RankingSource {

    private final SuccessStore store;

    public SuccessRanking(SuccessStore store) {
        this.store = store;
    }

    @Override
    public String id() {
        return "success";
    }

    @Override
    public List<Ranked> top(int limit) {
        return store.top(limit).stream().map(ranked -> new Ranked(ranked.player(), ranked.points())).toList();
    }

    @Override
    public long[] of(UUID player) {
        return new long[]{store.rank(player), store.points(player)};
    }
}
