package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterClan.api.ClanApi;
import fr.eternom.eterEconomy.api.EconomyApi;
import fr.eternom.eterHome.api.HomeApi;
import fr.eternom.eterMarket.api.MarketApi;
import fr.eternom.eterResource.api.ResourceApi;
import fr.eternom.eterReward.api.RewardApi;
import fr.eternom.eterSuccess.module.achievement.Achievement.Kind;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * Les « états » relus chez les autres plugins, par leur API. Chaque plugin facultatif est lu dans sa propre classe,
 * chargée seulement s'il tourne ici (sinon NoClassDefFoundError). -1 : rien à lire sur ce serveur. Bloquant.
 */
final class States {

    private States() {
    }

    static long read(Kind kind, UUID player) {
        return switch (kind) {
            case JOB -> enabled("EterMarket") ? Market.job(player) : -1;
            case HOMES -> enabled("EterHome") ? Homes.count(player) : -1;
            case MONEY -> EconomyApi.get().map(economy -> (long) Math.floor(economy.balance(player))).orElse(-1L);
            case CLAN -> enabled("EterClan") ? Clans.member(player) : -1;
            case CLAN_LAND -> enabled("EterClan") ? Clans.land(player) : -1;
            case PASS_TIER -> enabled("EterReward") ? Rewards.passTier(player) : -1;
            case DAILY -> enabled("EterReward") ? Rewards.daily(player) : -1;
            default -> -1;
        };
    }

    /** Ce serveur est un monde ressource (EterResource). */
    static boolean isResourceServer() {
        return enabled("EterResource") && Resource.isResourceServer();
    }

    private static boolean enabled(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    private static final class Market {
        static long job(UUID player) {
            return MarketApi.get().map(api -> api.job(player).isPresent() ? 1L : 0L).orElse(-1L);
        }
    }

    private static final class Homes {
        static long count(UUID player) {
            return HomeApi.get().map(api -> (long) api.homes(player).size()).orElse(-1L);
        }
    }

    private static final class Clans {
        static long member(UUID player) {
            return ClanApi.get().map(api -> api.clanOf(player).isPresent() ? 1L : 0L).orElse(-1L);
        }

        /** Chunks du clan du joueur (0 sans clan). */
        static long land(UUID player) {
            return ClanApi.get().map(api -> api.clanOf(player).map(clan -> (long) clan.chunks()).orElse(0L)).orElse(-1L);
        }
    }

    private static final class Rewards {
        static long passTier(UUID player) {
            return RewardApi.get().map(api -> (long) api.passTier(player)).orElse(-1L);
        }

        static long daily(UUID player) {
            return RewardApi.get().map(api -> api.streak(player) > 0 || api.claimedToday(player) ? 1L : 0L).orElse(-1L);
        }
    }

    private static final class Resource {
        static boolean isResourceServer() {
            return ResourceApi.get().map(ResourceApi::isResourceServer).orElse(false);
        }
    }
}
