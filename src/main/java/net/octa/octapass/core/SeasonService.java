package net.octa.octapass.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class SeasonService {
    private final JavaPlugin plugin;
    private Season season;
    private LevelCurve curve;
    private Boolean forcedActive;

    public SeasonService(JavaPlugin plugin) { this.plugin = plugin; }

    public void load() {
        File file = new File(plugin.getDataFolder(), "season.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = yaml.getString("season.id", "season-1");
        String name = yaml.getString("season.name", "The First Dawn");
        Instant start = parseInstant(yaml.getString("season.start", "2026-01-01T00:00:00Z"), Instant.EPOCH);
        Instant end = parseInstant(yaml.getString("season.end", "2030-01-01T00:00:00Z"), Instant.parse("2030-01-01T00:00:00Z"));
        int maxLevel = Math.max(1, yaml.getInt("max-level", 50));
        season = new Season(id, name, start, end, maxLevel);
        curve = new LevelCurve(yaml, maxLevel);
    }

    public Season season() { return season; }
    public LevelCurve curve() { return curve; }
    public boolean active() { return forcedActive != null ? forcedActive : season != null && season.isActive(Instant.now()); }
    public void forceActive(Boolean active) { forcedActive = active; }
    private Instant parseInstant(String value, Instant fallback) {
        try { return Instant.parse(value); }
        catch (DateTimeParseException exception) {
            plugin.getLogger().warning("Invalid season date in season.yml: " + value + "; using fallback");
            return fallback;
        }
    }
}
