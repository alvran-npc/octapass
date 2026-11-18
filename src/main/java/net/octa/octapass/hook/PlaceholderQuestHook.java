package net.octa.octapass.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import net.octa.octapass.AdvancedOctaPass;
import net.octa.octapass.core.PlayerProfile;
import net.octa.octapass.quest.QuestDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Polls configured PlaceholderAPI objectives and converts them into regular quest progress. */
public final class PlaceholderQuestHook {
    private final AdvancedOctaPass plugin;
    private final Map<String, String> lastMatches = new ConcurrentHashMap<>();
    public PlaceholderQuestHook(AdvancedOctaPass plugin) { this.plugin = plugin; }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig().getLong("anti-exploit.placeholder-poll-ticks", 100L));
        Bukkit.getScheduler().runTaskTimer(plugin, this::poll, interval, interval);
    }

    private void poll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = plugin.profiles().get(player);
            if (profile == null) continue;
            for (QuestDefinition quest : plugin.quests().activeFor(profile)) {
                String type = quest.type();
                if (type.startsWith("placeholderapi_integer_")) {
                    String placeholder = type.substring("placeholderapi_integer_".length());
                    String parsed = PlaceholderAPI.setPlaceholders(player, placeholder);
                    try {
                        int value = Math.max(0, Integer.parseInt(parsed.replaceAll("[^0-9-]", "")));
                        int current = profile.questProgress().getOrDefault(quest.id(), 0);
                        int next = Math.min(quest.amount(), value);
                        if (next > current) plugin.progress().signal(player, type, quest.variable(), next - current);
                    } catch (NumberFormatException ignored) { }
                } else if (type.startsWith("placeholderapi_match_")) {
                    String placeholder = type.substring("placeholderapi_match_".length());
                    String parsed = PlaceholderAPI.setPlaceholders(player, placeholder);
                    String expected = quest.variable();
                    if (parsed.equalsIgnoreCase(expected)) {
                        String sampleKey = player.getUniqueId() + ":" + quest.id();
                        String previous = lastMatches.put(sampleKey, parsed);
                        if (previous == null || !previous.equalsIgnoreCase(parsed)) plugin.progress().signal(player, type, expected, 1);
                    } else {
                        lastMatches.remove(player.getUniqueId() + ":" + quest.id());
                    }
                }
            }
        }
    }
}
