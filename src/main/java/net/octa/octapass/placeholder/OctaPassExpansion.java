package net.octa.octapass.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.octa.octapass.AdvancedOctaPass;
import net.octa.octapass.core.PlayerProfile;
import net.octa.octapass.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class OctaPassExpansion extends PlaceholderExpansion {
    private final AdvancedOctaPass plugin;
    public OctaPassExpansion(AdvancedOctaPass plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "octapass"; }
    @Override public @NotNull String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return "";
        PlayerProfile profile = plugin.profiles().get(offlinePlayer.getUniqueId());
        String key = params.toLowerCase(Locale.ROOT);
        if (key.startsWith("top_level_")) {
            String[] parts = key.split("_");
            if (parts.length >= 4) {
                try {
                    int position = Integer.parseInt(parts[2]) - 1;
                    var top = plugin.topLeaderboard();
                    if (position < 0 || position >= top.size()) return "";
                    if (parts[3].equals("name")) return top.get(position).name();
                    if (parts[3].equals("value")) return String.valueOf(top.get(position).value());
                } catch (NumberFormatException ignored) { return ""; }
            }
        }
        if (key.equals("season_name")) return plugin.seasons().season().name();
        if (key.equals("season_time_left")) return TimeUtil.compact(plugin.seasons().season().timeLeft(Instant.now()));
        if (key.equals("daily_reset_in")) return TimeUtil.compact(plugin.quests().untilDailyReset());
        if (key.equals("weekly_reset_in")) return TimeUtil.compact(plugin.quests().untilWeeklyReset());
        if (profile == null) return key.equals("premium") ? "false" : "0";
        Player online = offlinePlayer.getPlayer();
        int level = plugin.seasons().curve().levelForXp(profile.totalXp());
        long into = plugin.seasons().curve().xpIntoLevel(profile.totalXp());
        long needed = plugin.seasons().curve().xpNeededForCurrentLevel(profile.totalXp());
        return switch (key) {
            case "level" -> String.valueOf(level);
            case "xp" -> String.valueOf(into);
            case "xp_total", "total_xp" -> String.valueOf(profile.totalXp());
            case "xp_needed" -> String.valueOf(needed);
            case "xp_progress_bar" -> net.octa.octapass.quest.ProgressService.progressBar(needed <= 0 ? 100 : (int) Math.min(100, into * 100 / needed), 12);
            case "premium" -> String.valueOf(profile.premium() || (online != null && online.hasPermission("octapass.premium")));
            case "quests_completed" -> String.valueOf(profile.questsCompleted());
            case "booster_active" -> String.valueOf(plugin.boosters().active(offlinePlayer.getUniqueId()));
            case "booster_time_left" -> TimeUtil.compact(plugin.boosters().remaining(offlinePlayer.getUniqueId()));
            case "booster_multiplier" -> String.valueOf(plugin.boosters().activeMultiplier(offlinePlayer.getUniqueId()));
            default -> "";
        };
    }
}
