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
