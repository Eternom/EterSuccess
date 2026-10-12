package fr.eternom.eterSuccess.module.achievement;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les succès en base, communs au réseau. Appels bloquants : hors du thread principal.
 * - etersuccess_progress : le compteur de chaque succès d'un joueur (ou la plus haute valeur vue, pour un état) ;
 * - etersuccess_tiers : les niveaux débloqués (une ligne par niveau, INSERT IGNORE : récompense donnée une seule fois).
 */
public class SuccessStore {

    private static final String PROGRESS = "progress";
    private static final String TIERS = "tiers";

    private final Database database;

    public SuccessStore(Database database) {
        this.database = database;
        database.createTable(PROGRESS,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("achievement", Column.Type.STRING).length(64).primaryKey(),
                Column.of("amount", Column.Type.LONG).notNull());
        database.createTable(TIERS,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("achievement", Column.Type.STRING).length(64).primaryKey(),
                Column.of("tier", Column.Type.INT).primaryKey(),
                Column.of("points", Column.Type.INT).notNull(),
                Column.of("unlocked_at", Column.Type.LONG).notNull());
    }

    /** Ajoute au compteur (une requête relative) et renvoie la nouvelle valeur. */
    public long add(UUID player, String achievement, long amount) {
        database.execute("INSERT INTO " + database.table(PROGRESS) + " (uuid, achievement, amount) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE amount = amount + ?", player, achievement, amount, amount);
        return value(player, achievement);
    }

    /** État relu (argent, homes...) : garde la plus haute valeur vue, et la renvoie. */
    public long raise(UUID player, String achievement, long value) {
        database.execute("INSERT INTO " + database.table(PROGRESS) + " (uuid, achievement, amount) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE amount = GREATEST(amount, ?)", player, achievement, value, value);
        return value(player, achievement);
    }

    public long value(UUID player, String achievement) {
        return database.getFirst(PROGRESS, Map.of("uuid", player, "achievement", achievement))
                .map(row -> row.getLong("amount")).orElse(0L);
    }

    /** Tous les compteurs d'un joueur. */
    public Map<String, Long> values(UUID player) {
        Map<String, Long> values = new HashMap<>();
        for (Row row : database.get(PROGRESS, Map.of("uuid", player))) {
            values.put(row.getString("achievement"), row.getLong("amount"));
        }
        return values;
    }

    /** Niveaux débloqués : succès -> plus haut niveau. */
    public Map<String, Integer> tiers(UUID player) {
        Map<String, Integer> tiers = new HashMap<>();
        for (Row row : database.get(TIERS, Map.of("uuid", player))) {
            tiers.merge(row.getString("achievement"), row.getInt("tier"), Math::max);
        }
        return tiers;
    }

    /** Débloque un niveau ; false s'il l'était déjà (autre serveur, double appel). */
    public boolean unlock(UUID player, String achievement, int tier, int points) {
        return database.execute("INSERT IGNORE INTO " + database.table(TIERS) + " (uuid, achievement, tier, points, unlocked_at)"
                + " VALUES (?, ?, ?, ?, ?)", player, achievement, tier, points, System.currentTimeMillis()) > 0;
    }

    /** Classement des points (somme des niveaux débloqués de chaque joueur). */
    public List<fr.eternom.eterSuccess.api.SuccessApi.Ranked> top(int limit) {
        return database.query("SELECT uuid, SUM(points) AS total FROM " + database.table(TIERS)
                        + " GROUP BY uuid ORDER BY total DESC LIMIT ?", limit).stream()
                .map(row -> new fr.eternom.eterSuccess.api.SuccessApi.Ranked(row.getUUID("uuid"), row.getLong("total")))
                .toList();
    }

    /** Place du joueur (1 = premier), 0 sans aucun succès. */
    public int rank(UUID player) {
        int mine = points(player);
        if (mine <= 0) {
            return 0;
        }
        return (int) database.query("SELECT COUNT(*) AS above FROM (SELECT SUM(points) AS total FROM " + database.table(TIERS)
                + " GROUP BY uuid) AS totals WHERE total > ?", mine).getFirst().getLong("above") + 1;
    }

    public int points(UUID player) {
        List<Row> rows = database.query("SELECT COALESCE(SUM(points), 0) AS total FROM " + database.table(TIERS) + " WHERE uuid = ?",
                player);
        return rows.isEmpty() ? 0 : (int) rows.getFirst().getLong("total");
    }
}
