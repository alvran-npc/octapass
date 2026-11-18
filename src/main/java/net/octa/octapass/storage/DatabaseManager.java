package net.octa.octapass.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.octa.octapass.core.PlayerProfile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** JDBC work is always submitted to the database executor. */
public final class DatabaseManager {
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private final Gson gson = new Gson();
    private volatile HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, ExecutorService executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    public CompletableFuture<Void> initialize(FileConfiguration config) {
        return CompletableFuture.runAsync(() -> {
            try {
                HikariConfig hikari = new HikariConfig();
                String type = config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
                hikari.setPoolName("AdvancedOctaPass-Pool");
                hikari.setMaximumPoolSize(Math.max(2, config.getInt("storage.pool.maximum-size", 5)));
                hikari.setMinimumIdle(Math.max(1, config.getInt("storage.pool.minimum-idle", 1)));
                hikari.setConnectionTimeout(Math.max(1000, config.getLong("storage.pool.connection-timeout-ms", 10000)));
                hikari.setMaxLifetime(Math.max(30000, config.getLong("storage.pool.max-lifetime-ms", 1500000)));
                if (type.equals("mysql") || type.equals("mariadb")) {
                    String host = config.getString("storage.mysql.host", "127.0.0.1");
                    int port = config.getInt("storage.mysql.port", 3306);
                    String database = config.getString("storage.mysql.database", "octapass");
                    hikari.setDriverClassName("org.mariadb.jdbc.Driver");
                    hikari.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf8");
                    hikari.setUsername(config.getString("storage.mysql.username", "root"));
                    hikari.setPassword(config.getString("storage.mysql.password", ""));
                } else {
                    File file = new File(plugin.getDataFolder(), config.getString("storage.sqlite.file", "octapass.db"));
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Could not create SQLite directory");
                    hikari.setDriverClassName("org.sqlite.JDBC");
                    hikari.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
                    hikari.setMaximumPoolSize(1);
                }
                dataSource = new HikariDataSource(hikari);
                migrate();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private void migrate() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS octapass_schema_version (version INTEGER PRIMARY KEY, applied_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS octapass_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, season_id VARCHAR(100) NOT NULL, data_json TEXT NOT NULL, updated_at BIGINT NOT NULL)");
            try { statement.executeUpdate("CREATE INDEX idx_octapass_updated ON octapass_players(updated_at)"); }
            catch (SQLException ignored) { /* Index may already exist or be managed by the SQL dialect. */ }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS octapass_season_history (" +
                    "uuid VARCHAR(36) NOT NULL, season_id VARCHAR(100) NOT NULL, level_value INT NOT NULL, xp_value BIGINT NOT NULL, " +
                    "quests_completed BIGINT NOT NULL, archived_at BIGINT NOT NULL, PRIMARY KEY(uuid, season_id))");
            try (PreparedStatement versionCheck = connection.prepareStatement("SELECT version FROM octapass_schema_version WHERE version = ?")) {
                versionCheck.setInt(1, 1);
                try (ResultSet result = versionCheck.executeQuery()) {
                    if (!result.next()) {
                        try (PreparedStatement insertVersion = connection.prepareStatement(
                                "INSERT INTO octapass_schema_version(version, applied_at) VALUES(?, ?)")) {
                            insertVersion.setInt(1, 1);
                            insertVersion.setLong(2, System.currentTimeMillis());
                            insertVersion.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    public CompletableFuture<PlayerProfile> load(UUID uuid, String seasonId) {
        return CompletableFuture.supplyAsync(() -> {
            ensureReady();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT data_json FROM octapass_players WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return new PlayerProfile(uuid, seasonId);
                    Map<String, Object> map = gson.fromJson(result.getString(1), MAP_TYPE);
                    PlayerProfile profile = PlayerProfile.fromMap(uuid, seasonId, map == null ? Map.of() : map);
                    if (!profile.seasonId().equals(seasonId)) {
                        // Season transitions do not silently merge incompatible progress.
                        profile.seasonId(seasonId);
                        profile.totalXp(0);
                        profile.claimedRewards().clear();
                        profile.dailyAssigned().clear();
                        profile.weeklyAssigned().clear();
                        profile.completedQuestIds().clear();
                        profile.questProgress().clear();
                        profile.dailyPeriod("");
                        profile.weeklyPeriod("");
                    }
                    return profile;
                }
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveAll(Collection<PlayerProfile> profiles) {
        if (profiles.isEmpty()) return CompletableFuture.completedFuture(null);
        List<PlayerProfile> copy = new ArrayList<>(profiles);
        return CompletableFuture.runAsync(() -> {
            ensureReady();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE octapass_players SET season_id = ?, data_json = ?, updated_at = ? WHERE uuid = ?");
                     PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO octapass_players(uuid, season_id, data_json, updated_at) VALUES(?, ?, ?, ?)")) {
                    for (PlayerProfile profile : copy) {
                        String json = gson.toJson(profile.toMap());
                        update.setString(1, profile.seasonId());
                        update.setString(2, json);
                        update.setLong(3, System.currentTimeMillis());
                        update.setString(4, profile.uniqueId().toString());
                        int changed = update.executeUpdate();
                        if (changed == 0) {
                            insert.setString(1, profile.uniqueId().toString());
                            insert.setString(2, profile.seasonId());
                            insert.setString(3, json);
                            insert.setLong(4, System.currentTimeMillis());
                            insert.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<List<LeaderboardEntry>> leaderboard(String metric, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            ensureReady();
            List<LeaderboardEntry> entries = new ArrayList<>();
            String safeMetric = switch (metric.toLowerCase(Locale.ROOT)) {
                case "quests", "quest", "quests_completed" -> "questsCompleted";
                default -> "totalXp";
            };
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT uuid, data_json FROM octapass_players")) {
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID uuid = UUID.fromString(result.getString(1));
                        Map<String, Object> map = gson.fromJson(result.getString(2), MAP_TYPE);
                        long value = number(map == null ? null : map.get(safeMetric));
                        String name = map == null ? uuid.toString().substring(0, 8) : String.valueOf(map.getOrDefault("lastName", uuid.toString().substring(0, 8)));
                        entries.add(new LeaderboardEntry(uuid, name, value));
                    }
                }
                entries.sort(Comparator.comparingLong(LeaderboardEntry::value).reversed());
                if (entries.size() > Math.max(1, limit)) return new ArrayList<>(entries.subList(0, Math.max(1, limit)));
                return entries;
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> archiveSeason(String seasonId, Collection<PlayerProfile> profiles, net.octa.octapass.core.LevelCurve curve) {
        List<PlayerProfile> copy = new ArrayList<>(profiles);
        return CompletableFuture.runAsync(() -> {
            ensureReady();
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO octapass_season_history(uuid, season_id, level_value, xp_value, quests_completed, archived_at) VALUES(?, ?, ?, ?, ?, ?)")) {
                for (PlayerProfile profile : copy) {
                    statement.setString(1, profile.uniqueId().toString());
                    statement.setString(2, seasonId);
                    statement.setInt(3, curve.levelForXp(profile.totalXp()));
                    statement.setLong(4, profile.totalXp());
                    statement.setLong(5, profile.questsCompleted());
                    statement.setLong(6, System.currentTimeMillis());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> resetAll() {
        return CompletableFuture.runAsync(() -> {
            ensureReady();
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM octapass_players");
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> closeAsync(Collection<PlayerProfile> profiles) {
        return saveAll(profiles).handleAsync((ignored, error) -> {
            if (error != null) plugin.getLogger().severe("Final save failed: " + error.getMessage());
            HikariDataSource source = dataSource;
            if (source != null && !source.isClosed()) source.close();
            return null;
        }, executor);
    }

    public boolean isReady() { return dataSource != null && !dataSource.isClosed(); }
    private void ensureReady() { if (!isReady()) throw new IllegalStateException("Database is not ready"); }
    private static long number(Object value) { return value instanceof Number number ? number.longValue() : 0L; }

    public record LeaderboardEntry(UUID uuid, String name, long value) {}
}
