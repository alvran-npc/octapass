package net.octa.octapass.core;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.TreeMap;

public final class LevelCurve {
    private final String mode;
    private final long base;
    private final long step;
    private final double factor;
    private final int maxLevel;
    private final Map<Integer, Long> custom;

    public LevelCurve(ConfigurationSection section, int seasonMaxLevel) {
        this.mode = section.getString("xp-curve.mode", "linear").toLowerCase();
        this.base = Math.max(1, section.getLong("xp-curve.base", 100));
        this.step = Math.max(0, section.getLong("xp-curve.step", 25));
        this.factor = Math.max(1.0, section.getDouble("xp-curve.factor", 1.25));
        this.maxLevel = Math.max(1, section.getInt("max-level", seasonMaxLevel));
        this.custom = new TreeMap<>();
        ConfigurationSection customSection = section.getConfigurationSection("xp-curve.custom");
        if (customSection != null) {
            for (String key : customSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    long amount = Math.max(1, customSection.getLong(key));
                    custom.put(level, amount);
                } catch (NumberFormatException ignored) { }
            }
        }
    }

    public long xpForNextLevel(int currentLevel) {
        int target = Math.max(1, currentLevel);
        if (custom.containsKey(target)) return custom.get(target);
        return switch (mode) {
            case "exponential" -> Math.max(1, Math.round(base * Math.pow(factor, target - 1)));
            case "custom" -> custom.getOrDefault(target, base + (long) (target - 1) * step);
            default -> base + (long) (target - 1) * step;
        };
    }

    public int levelForXp(long totalXp) {
        long remaining = Math.max(0, totalXp);
        int level = 1;
        while (level < maxLevel) {
            long needed = xpForNextLevel(level);
            if (remaining < needed) break;
            remaining -= needed;
            level++;
        }
        return level;
    }

    public long xpIntoLevel(long totalXp) {
        long remaining = Math.max(0, totalXp);
        int level = 1;
        while (level < maxLevel) {
            long needed = xpForNextLevel(level);
            if (remaining < needed) break;
            remaining -= needed;
            level++;
        }
        return remaining;
    }

    public long xpTotalToReach(int targetLevel) {
        int target = Math.max(1, Math.min(maxLevel, targetLevel));
        long sum = 0;
        for (int level = 1; level < target; level++) sum = saturatingAdd(sum, xpForNextLevel(level));
        return sum;
    }

    public long xpNeededForCurrentLevel(long totalXp) {
        int level = levelForXp(totalXp);
        return level >= maxLevel ? 0 : xpForNextLevel(level);
    }

    public int maxLevel() { return maxLevel; }

    private long saturatingAdd(long a, long b) {
        try { return Math.addExact(a, b); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }
}
