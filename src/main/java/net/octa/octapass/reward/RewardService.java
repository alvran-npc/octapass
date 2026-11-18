package net.octa.octapass.reward;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.octa.octapass.AdvancedOctaPass;
import net.octa.octapass.api.event.OctaPassRewardClaimEvent;
import net.octa.octapass.core.PlayerProfile;
import net.octa.octapass.core.ProfileService;
import net.octa.octapass.core.SeasonService;
import net.octa.octapass.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class RewardService {
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private final AdvancedOctaPass plugin;
    private final ProfileService profiles;
    private final SeasonService seasons;
    private final Gson gson = new Gson();
    private final Map<Integer, List<Map<String, Object>>> free = new TreeMap<>();
    private final Map<Integer, List<Map<String, Object>>> premium = new TreeMap<>();
    private final Set<String> claiming = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public RewardService(AdvancedOctaPass plugin, ProfileService profiles, SeasonService seasons) {
        this.plugin = plugin; this.profiles = profiles; this.seasons = seasons;
    }

    public void load() {
        free.clear(); premium.clear();
        loadTrack(new File(plugin.getDataFolder(), "rewards/free.yml"), free);
        loadTrack(new File(plugin.getDataFolder(), "rewards/premium.yml"), premium);
    }

    private void loadTrack(File file, Map<Integer, List<Map<String, Object>>> target) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection levels = yaml.getConfigurationSection("levels");
        if (levels == null) return;
        for (String levelKey : levels.getKeys(false)) {
            try {
                int level = Integer.parseInt(levelKey);
                List<Map<String, Object>> rewards = new ArrayList<>();
                for (Map<?, ?> raw : yaml.getMapList("levels." + levelKey + ".rewards")) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    raw.forEach((key, value) -> map.put(String.valueOf(key), value));
                    rewards.add(map);
                }
                target.put(level, rewards);
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Invalid level key " + levelKey + " in " + file.getName());
            }
        }
    }

    public int rewardCount() {
        return free.values().stream().mapToInt(List::size).sum() + premium.values().stream().mapToInt(List::size).sum();
    }

    public boolean hasClaimed(PlayerProfile profile, int level, boolean premiumTrack) {
        return profile.claimedRewards().contains(claimKey(profile, level, premiumTrack));
    }

    public boolean claimTrack(Player player, int level, boolean premiumTrack) {
        PlayerProfile profile = profiles.get(player);
        if (profile == null) return false;
        int currentLevel = seasons.curve().levelForXp(profile.totalXp());
        if (level < 1 || level > currentLevel) return false;
        if (premiumTrack && !profile.premium() && !player.hasPermission("octapass.premium")) return false;
        String key = claimKey(profile, level, premiumTrack);
        if (profile.claimedRewards().contains(key) || !claiming.add(player.getUniqueId() + ":" + key)) return false;
        try {
            OctaPassRewardClaimEvent event = new OctaPassRewardClaimEvent(player, key);
            org.bukkit.Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            List<Map<String, Object>> rewards = (premiumTrack ? premium : free).getOrDefault(level, List.of());
            for (Map<String, Object> reward : rewards) {
                if (!grant(player, profile, reward)) return false;
            }
            profile.claimedRewards().add(key);
            profiles.markDirty(profile);
            plugin.getLogger().info("Reward claimed: player=" + player.getName() + ", key=" + key);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.25f);
            return true;
        } finally {
            claiming.remove(player.getUniqueId() + ":" + key);
        }
    }

    public int claimAll(Player player) {
        PlayerProfile profile = profiles.get(player);
        if (profile == null) return 0;
        int level = seasons.curve().levelForXp(profile.totalXp());
        int claimed = 0;
        for (int i = 1; i <= level; i++) {
            if (claimTrack(player, i, false)) claimed++;
            if ((profile.premium() || player.hasPermission("octapass.premium")) && claimTrack(player, i, true)) claimed++;
        }
        deliverPending(player, profile);
        return claimed;
    }

    public int deliverPending(Player player, PlayerProfile profile) {
        int delivered = 0;
        synchronized (profile.pendingRewards()) {
            Iterator<String> iterator = profile.pendingRewards().iterator();
            while (iterator.hasNext()) {
                String json = iterator.next();
                try {
                    Map<String, Object> reward = gson.fromJson(json, MAP_TYPE);
                    ItemStack item = createItem(reward);
                    if (item == null) { iterator.remove(); continue; }
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                    if (leftovers.isEmpty()) { iterator.remove(); delivered++; }
                    else break;
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Discarding invalid pending item for " + player.getName());
                    iterator.remove();
                }
            }
        }
        if (delivered > 0) profiles.markDirty(profile);
        return delivered;
    }

    private boolean grant(Player player, PlayerProfile profile, Map<String, Object> reward) {
        String type = String.valueOf(reward.getOrDefault("type", "message")).toLowerCase(Locale.ROOT);
        try {
            switch (type) {
                case "item" -> {
                    ItemStack item = createItem(reward);
                    if (item == null) return false;
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                    for (ItemStack leftover : leftovers.values()) {
                        Map<String, Object> pending = new LinkedHashMap<>(reward);
                        pending.put("amount", leftover.getAmount());
                        profile.pendingRewards().add(gson.toJson(pending));
                    }
                    return true;
                }
                case "message" -> player.sendMessage(TextUtil.component(String.valueOf(reward.getOrDefault("text", "<green>Reward unlocked!</green>"))));
                case "title" -> player.showTitle(net.kyori.adventure.title.Title.title(
                        TextUtil.component(String.valueOf(reward.getOrDefault("title", "<gold>Reward</gold>"))),
                        TextUtil.component(String.valueOf(reward.getOrDefault("subtitle", "<gray>Enjoy your reward</gray>")))));
                case "sound" -> {
                    Sound sound = Sound.valueOf(String.valueOf(reward.getOrDefault("sound", "UI_TOAST_CHALLENGE_COMPLETE")).toUpperCase(Locale.ROOT));
                    player.playSound(player.getLocation(), sound, (float) number(reward.getOrDefault("volume", 1)), (float) number(reward.getOrDefault("pitch", 1)));
                }
                case "money" -> {
                    if (!plugin.vault().available()) {
                        player.sendMessage(TextUtil.component("<red>Economy provider unavailable; reward was not claimed.</red>"));
                        return false;
                    }
                    double amount = number(reward.getOrDefault("amount", 0));
                    if (amount < 0) return false;
                    plugin.vault().economy().depositPlayer(player, amount);
                }
                case "xp-boost" -> {
                    double multiplier = number(reward.getOrDefault("multiplier", 1.5));
                    long seconds = (long) number(reward.getOrDefault("duration-seconds", 3600));
                    plugin.boosters().give(player.getUniqueId(), multiplier, Duration.ofSeconds(seconds));
                    player.sendMessage(TextUtil.component("<light_purple>XP Booster activated: <white>" + multiplier + "x</white> for <white>" + seconds + "s</white>.</light_purple>"));
                }
                case "random-choice" -> {
                    Object choicesObject = reward.get("choices");
                    if (!(choicesObject instanceof List<?> choices) || choices.isEmpty()) return false;
                    Map<String, Object> picked = weightedChoice(choices);
                    return picked != null && grant(player, profile, picked);
                }
                default -> {
                    plugin.getLogger().warning("Unsupported reward type '" + type + "'. Only non-combat rewards are enabled.");
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not grant reward type " + type + " to " + player.getName(), exception);
            return false;
        }
    }

    private Map<String, Object> weightedChoice(List<?> choices) {
        double total = 0;
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (Object choice : choices) {
            if (!(choice instanceof Map<?, ?> raw)) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            raw.forEach((k, v) -> map.put(String.valueOf(k), v));
            total += Math.max(0.01, number(map.getOrDefault("weight", 1)));
            parsed.add(map);
        }
        if (parsed.isEmpty()) return null;
        double needle = ThreadLocalRandom.current().nextDouble(total);
        for (Map<String, Object> map : parsed) {
            needle -= Math.max(0.01, number(map.getOrDefault("weight", 1)));
            if (needle <= 0) {
                Object nested = map.get("reward");
                if (nested instanceof Map<?, ?> raw) {
                    Map<String, Object> reward = new LinkedHashMap<>();
                    raw.forEach((k, v) -> reward.put(String.valueOf(k), v));
                    return reward;
                }
                return map;
            }
        }
        return parsed.getLast();
    }

    private ItemStack createItem(Map<String, Object> reward) {
        Material material = Material.matchMaterial(String.valueOf(reward.getOrDefault("material", "PAPER")));
        if (!SafeItemPolicy.allowed(material)) {
            plugin.getLogger().warning("Blocked non-cosmetic or combat-related pass item: " + reward.get("material"));
            return null;
        }
        int amount = Math.max(1, Math.min(64, (int) number(reward.getOrDefault("amount", 1))));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (reward.containsKey("name")) meta.displayName(TextUtil.component(String.valueOf(reward.get("name"))));
            Object lore = reward.get("lore");
            if (lore instanceof List<?> lines) meta.lore(lines.stream().map(line -> TextUtil.component(String.valueOf(line))).toList());
            if (reward.containsKey("custom-model-data")) meta.setCustomModelData((int) number(reward.get("custom-model-data")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String claimKey(PlayerProfile profile, int level, boolean premiumTrack) {
        return profile.seasonId() + ":" + level + ":" + (premiumTrack ? "premium" : "free");
    }
    private static double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException ignored) { return 0; }
    }
    public Map<Integer, List<Map<String, Object>>> freeRewards() { return Collections.unmodifiableMap(free); }
    public Map<Integer, List<Map<String, Object>>> premiumRewards() { return Collections.unmodifiableMap(premium); }
}
