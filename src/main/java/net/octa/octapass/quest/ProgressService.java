package net.octa.octapass.quest;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.octa.octapass.AdvancedOctaPass;
import net.octa.octapass.api.event.OctaPassLevelUpEvent;
import net.octa.octapass.api.event.OctaPassQuestCompleteEvent;
import net.octa.octapass.core.LevelCurve;
import net.octa.octapass.core.PlayerProfile;
import net.octa.octapass.core.ProfileService;
import net.octa.octapass.core.SeasonService;
import net.octa.octapass.quest.action.QuestActionRegistry;
import net.octa.octapass.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressService {
    private final AdvancedOctaPass plugin;
    private final ProfileService profiles;
    private final QuestService quests;
    private final SeasonService seasons;
    private final QuestActionRegistry registry;
    private final Map<UUID, String> trackers = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public ProgressService(AdvancedOctaPass plugin, ProfileService profiles, QuestService quests, SeasonService seasons, QuestActionRegistry registry) {
        this.plugin = plugin; this.profiles = profiles; this.quests = quests; this.seasons = seasons; this.registry = registry;
    }

    public void signal(Player player, String type, String variable, int amount) {
        if (player == null || amount <= 0 || !player.isOnline()) return;
        if (QuestActionRegistry.isRestrictedType(type)) return;
        PlayerProfile profile = profiles.get(player);
        if (profile == null) return;
        quests.refreshAssignments(profile);
        for (QuestDefinition quest : quests.activeFor(profile)) {
            if (!quest.type().equalsIgnoreCase(type)) continue;
            if (!quest.worlds().isEmpty() && !quest.worlds().contains(player.getWorld().getName())) continue;
            if (!quest.permission().isBlank() && !player.hasPermission(quest.permission())) continue;
            if (quest.premiumOnly() && !profile.premium()) continue;
            if (profile.completedQuestIds().contains(quest.id())) continue;
            if (!matches(quest.variable(), variable)) continue;
            int current = profile.questProgress().getOrDefault(quest.id(), 0);
            int next = Math.min(quest.amount(), current + amount);
            profile.questProgress().put(quest.id(), next);
            if (next >= quest.amount()) complete(player, profile, quest);
        }
        profiles.markDirty(profile);
        updateTracker(player, profile);
    }

    public void signal(Player player, String type, String variable) { signal(player, type, variable, 1); }

    public void triggerCustom(Player player, String actionId, String variable, int amount) {
        if (player == null || actionId == null || amount <= 0) return;
        signal(player, actionId, variable, amount);
        var observer = registry.get(actionId);
        if (observer != null) observer.trigger(player, variable, amount);
    }

    private boolean matches(String filter, String value) {
        if (filter == null || filter.isBlank() || filter.equals("*")) return true;
        if (value == null) return false;
        if (filter.startsWith("regex:")) {
            try { return value.matches(filter.substring(6)); }
            catch (RuntimeException ignored) { return false; }
        }
        return Arrays.stream(filter.split(",")).map(String::trim).anyMatch(token -> token.equalsIgnoreCase(value));
    }

    private void complete(Player player, PlayerProfile profile, QuestDefinition quest) {
        profile.completedQuestIds().add(quest.id());
        profile.questCompleted();
        long award = Math.round(quest.xp() * plugin.boosters().multiplier(player.getUniqueId()));
        int oldLevel = seasons.curve().levelForXp(profile.totalXp());
        profile.addXp(award);
        int newLevel = seasons.curve().levelForXp(profile.totalXp());
        profiles.markDirty(profile);
        Bukkit.getPluginManager().callEvent(new OctaPassQuestCompleteEvent(player, quest.id(), award));
        if (profile.notificationsEnabled()) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    TextUtil.component("<gradient:#55efc4:#a6ffcb><bold>OBJECTIVE COMPLETE</bold></gradient>"),
                    TextUtil.component("<gray>" + quest.displayName() + " <dark_gray>•</dark_gray> <white>+" + award + " XP</white>")));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.45f);
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 12, .4, .5, .4, .02);
        }
        if (newLevel > oldLevel) {
            Bukkit.getPluginManager().callEvent(new OctaPassLevelUpEvent(player, oldLevel, newLevel));
            player.sendMessage(TextUtil.component("<gradient:#f7c873:#fff1b8><bold>PASS LEVEL UP!</bold></gradient> <white>Level " + newLevel + "</white>"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    private void updateTracker(Player player, PlayerProfile profile) {
        String tracked = trackers.get(player.getUniqueId());
        QuestDefinition quest = tracked == null ? null : quests.get(tracked);
        if (quest == null || profile.completedQuestIds().contains(quest.id())) {
            quest = quests.activeFor(profile).stream().filter(q -> !profile.completedQuestIds().contains(q.id())).findFirst().orElse(null);
        }
        if (quest == null) return;
        int progress = profile.questProgress().getOrDefault(quest.id(), 0);
        int percent = Math.min(100, progress * 100 / quest.amount());
        String bar = progressBar(percent, 12);
        Component actionText = TextUtil.component("<gray>" + quest.displayName() + " <dark_gray>|</dark_gray> <aqua>" + progress + "/" + quest.amount() + "</aqua> <dark_gray>[</dark_gray><green>" + bar + "</green><dark_gray>]</dark_gray>");
        if (profile.actionBarEnabled() && plugin.getConfig().getBoolean("progress.actionbar", true)) player.sendActionBar(actionText);
        BossBar bossBar = bossBars.computeIfAbsent(player.getUniqueId(), ignored -> BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS));
        if (profile.bossBarEnabled() && plugin.getConfig().getBoolean("progress.bossbar", false)) {
            bossBar.name(actionText);
            bossBar.progress(Math.max(0f, Math.min(1f, (float) progress / quest.amount())));
            player.showBossBar(bossBar);
        } else {
            player.hideBossBar(bossBar);
        }
    }

    public boolean track(Player player, String questId) {
        QuestDefinition quest = quests.get(questId);
        if (quest == null) return false;
        PlayerProfile profile = profiles.get(player);
        if (profile == null || quests.activeFor(profile).stream().noneMatch(q -> q.id().equalsIgnoreCase(questId))) return false;
        trackers.put(player.getUniqueId(), quest.id());
        updateTracker(player, profile);
        return true;
    }

    public String tracked(UUID id) { return trackers.get(id); }
    public void clearPlayer(UUID id) {
        trackers.remove(id);
        BossBar bar = bossBars.remove(id);
        if (bar != null) for (Player player : Bukkit.getOnlinePlayers()) if (player.getUniqueId().equals(id)) player.hideBossBar(bar);
    }

    public void refreshOnlineTrackers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = profiles.get(player);
            if (profile != null) updateTracker(player, profile);
        }
    }

    public static String progressBar(int percent, int length) {
        int filled = Math.max(0, Math.min(length, percent * length / 100));
        return "▰".repeat(filled) + "▱".repeat(Math.max(0, length - filled));
    }

    public QuestActionRegistry registry() { return registry; }
}
