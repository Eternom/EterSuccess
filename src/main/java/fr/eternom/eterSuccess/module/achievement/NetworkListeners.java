package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterMarket.api.JobQuestCompleteEvent;
import fr.eternom.eterReward.api.ChallengeCompleteEvent;
import fr.eternom.eterSuccess.module.achievement.Achievement.Kind;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Les événements des autres plugins Eter. Une classe par plugin, enregistrée seulement s'il tourne ici (sinon
 * NoClassDefFoundError).
 */
public final class NetworkListeners {

    private NetworkListeners() {
    }

    /** Quêtes de métier (EterMarket) : tag = le métier. */
    public static class Quests implements Listener {

        private final SuccessService success;

        public Quests(SuccessService success) {
            this.success = success;
        }

        @EventHandler
        public void onQuest(JobQuestCompleteEvent event) {
            success.countTag(event.getPlayer(), Kind.QUEST, event.getJob());
        }
    }

    /** Ventes de l'hôtel des ventes (EterMarket) : une vente pour le vendeur (même hors ligne), un achat pour l'acheteur. */
    public static class Auctions implements Listener {

        private final SuccessService success;

        public Auctions(SuccessService success) {
            this.success = success;
        }

        @EventHandler
        public void onSale(fr.eternom.eterMarket.api.AuctionSaleEvent event) {
            success.countUuid(event.getSeller(), Kind.SELL, 1);
            success.countUuid(event.getBuyer(), Kind.BUY, 1);
        }
    }

    /** Récompense du jour (EterReward) : une série complète (jour 7). */
    public static class Daily implements Listener {

        private final SuccessService success;

        public Daily(SuccessService success) {
            this.success = success;
        }

        @EventHandler
        public void onDaily(fr.eternom.eterReward.api.DailyClaimEvent event) {
            if (event.getDay() >= 7) {
                success.countUuid(event.getPlayer().getUniqueId(), Kind.DAILY_SERIES, 1);
            }
        }
    }

    /** Défis (EterReward) : tag = daily ou weekly. */
    public static class Challenges implements Listener {

        private final SuccessService success;

        public Challenges(SuccessService success) {
            this.success = success;
        }

        @EventHandler
        public void onChallenge(ChallengeCompleteEvent event) {
            success.countTag(event.getPlayer(), Kind.CHALLENGE, event.isWeekly() ? "weekly" : "daily");
        }
    }
}
