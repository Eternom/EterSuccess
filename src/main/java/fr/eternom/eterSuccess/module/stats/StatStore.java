package fr.eternom.eterSuccess.module.stats;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Table etersuccess_stats : un compteur par joueur et par statistique, depuis toujours. Bloquant : hors du thread principal. */
public class StatStore {

    private static final String TABLE = "stats";

    /** Un joueur de la liste et son temps de jeu (tri de la liste). */
    public record Entry(UUID player, long playtime) {
    }

    private final Database database;

    public StatStore(Database database) {
        this.database = database;
        database.createTable(TABLE,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("stat", Column.Type.STRING).length(32).primaryKey(),
                Column.of("amount", Column.Type.LONG).notNull());
    }

    public void add(UUID player, Stat stat, long amount) {
        database.execute("INSERT INTO " + database.table(TABLE) + " (uuid, stat, amount) VALUES (?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE amount = amount + ?", player, stat.id(), amount, amount);
    }

    public Map<Stat, Long> of(UUID player) {
        Map<Stat, Long> stats = new EnumMap<>(Stat.class);
        database.get(TABLE, Map.of("uuid", player)).forEach(row -> {
            for (Stat stat : Stat.values()) {
                if (stat.id().equals(row.getString("stat"))) {
                    stats.put(stat, row.getLong("amount"));
                }
            }
        });
        return stats;
    }

    /** Une page de joueurs, du plus grand temps de jeu au plus petit. */
    public List<Entry> page(int offset, int limit) {
        return database.query("SELECT uuid, amount FROM " + database.table(TABLE) + " WHERE stat = ? ORDER BY amount DESC, uuid"
                + " LIMIT ? OFFSET ?", Stat.PLAYTIME.id(), limit, offset).stream()
                .map(row -> new Entry(row.getUUID("uuid"), row.getLong("amount"))).toList();
    }

    public long get(UUID player, Stat stat) {
        return database.getFirst(TABLE, Map.of("uuid", player, "stat", stat.id())).map(row -> row.getLong("amount")).orElse(0L);
    }

    public int players() {
        return database.query("SELECT COUNT(*) AS total FROM " + database.table(TABLE) + " WHERE stat = ?", Stat.PLAYTIME.id())
                .getFirst().getInt("total");
    }
}
