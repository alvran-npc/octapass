package net.octa.octapass.command;

import net.octa.octapass.AdvancedOctaPass;
import net.octa.octapass.api.OctaPassAPI;
import net.octa.octapass.core.PlayerProfile;
import net.octa.octapass.quest.QuestCategory;
import net.octa.octapass.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;

public final class OctaPassCommand implements CommandExecutor, TabCompleter {
    private final AdvancedOctaPass plugin;
    public OctaPassCommand(AdvancedOctaPass plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !player.hasPermission("octapass.use") && !player.hasPermission("octapass.admin")) {
            player.sendMessage(TextUtil.component("<red>You do not have permission to use OctaPass.</red>"));
            return true;
        }
        if (args.length == 0) return open(sender);
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open" -> { return open(sender); }
            case "quests" -> {
                if (!(sender instanceof Player player)) return playerOnly(sender);
                plugin.gui().openQuests(player, QuestCategory.DAILY); return true;
            }
            case "rewards" -> {
                if (!(sender instanceof Player player)) return playerOnly(sender);
                plugin.gui().openRewards(player, 0); return true;
            }
            case "claim" -> {
                if (!(sender instanceof Player player)) return playerOnly(sender);
                int claimed = plugin.rewards().claimAll(player);
                player.sendMessage(TextUtil.component("<green>Claimed " + claimed + " reward track(s).</green>")); return true;
            }
            case "buylevel" -> {
                if (!(sender instanceof Player player)) return playerOnly(sender);
                int amount = args.length >= 2 ? parseInt(args[1], 1) : 1;
                int bought = plugin.purchaseLevels(player, amount);
                if (bought > 0) player.sendMessage(TextUtil.component("<green>Purchased " + bought + " pass level(s).</green>"));
                else player.sendMessage(TextUtil.component("<red>Level purchase is unavailable or the transaction failed.</red>"));
                return true;
            }
            case "premium" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("give")) return premiumGive(sender, args);
                if (args.length >= 2 && args[1].equalsIgnoreCase("buy")) {
                    if (!(sender instanceof Player player)) return playerOnly(sender);
                    plugin.gui().openConfirmPremium(player); return true;
                }
                if (!(sender instanceof Player player)) return playerOnly(sender);
                plugin.gui().openPremium(player); return true;
            }
            case "reroll" -> { return reroll(sender); }
            case "top" -> {
                if (!(sender instanceof Player player)) return playerOnly(sender);
                String metric = args.length > 1 && args[1].equalsIgnoreCase("quests") ? "quests" : "xp";
                plugin.gui().openLeaderboard(player, metric); return true;
            }
            case "settings" -> {
                if (!(sender instanceof Player player)) return playerOnly(sender);
                plugin.gui().openSettings(player); return true;
            }
            case "help" -> { help(sender); return true; }
            case "reload" -> {
                if (!admin(sender, "octapass.admin.reload")) return true;
                plugin.reloadAll(); sender.sendMessage(TextUtil.component("<green>Advanced OctaPass configuration reloaded.</green>")); return true;
            }
            case "give" -> { return give(sender, args); }
            case "set" -> { return set(sender, args); }
            case "reset" -> { return reset(sender, args); }
            case "season" -> { return season(sender, args); }
            case "event" -> { return event(sender, args); }
            case "booster" -> { return booster(sender, args); }
            case "debug" -> {
                if (!admin(sender, "octapass.admin.debug")) return true;
                sender.sendMessage(TextUtil.component("<gray>DB ready: <white>" + plugin.database().isReady() + "</white> | Quests: <white>" + plugin.quests().size() + "</white> | Cached profiles: <white>" + plugin.profiles().profiles().size() + "</white></gray>"));
                return true;
            }
            default -> { help(sender); return true; }
        }
    }

    private boolean open(CommandSender sender) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        plugin.gui().openMain(player); return true;
    }

    private boolean reroll(CommandSender sender) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) { player.sendMessage(TextUtil.component("<yellow>Your profile is still loading.</yellow>")); return true; }
        plugin.quests().refreshAssignments(profile);
        int freePerDay = Math.max(0, plugin.getConfig().getInt("quest-reroll.free-per-day", 1));
        if (profile.dailyRerollsUsed() >= freePerDay) {
            player.sendMessage(TextUtil.component("<red>You have used today's free reroll.</red>")); return true;
        }
        if (!plugin.quests().rerollDaily(profile)) {
            player.sendMessage(TextUtil.component("<red>No replacement daily objective is available.</red>")); return true;
        }
        profile.dailyRerollsUsed(profile.dailyRerollsUsed() + 1);
        plugin.profiles().markDirty(profile);
        player.sendMessage(TextUtil.component("<green>A daily objective has been rerolled.</green>"));
        plugin.gui().openQuests(player, QuestCategory.DAILY);
        return true;
    }

    private boolean premiumGive(CommandSender sender, String[] args) {
        if (!admin(sender, "octapass.admin.premium")) return true;
        if (args.length < 3) { sender.sendMessage(TextUtil.component("<yellow>Usage: /octapass premium give <player></yellow>")); return true; }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { sender.sendMessage(TextUtil.component("<red>Player must be online.</red>")); return true; }
        PlayerProfile profile = plugin.profiles().get(target);
        if (profile == null) { sender.sendMessage(TextUtil.component("<red>Player data is not loaded yet.</red>")); return true; }
        profile.premium(true); plugin.profiles().markDirty(profile);
        sender.sendMessage(TextUtil.component("<green>Premium Pass granted to " + target.getName() + ".</green>")); return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!admin(sender, "octapass.admin.give")) return true;
        if (args.length < 3) { sender.sendMessage(TextUtil.component("<yellow>Usage: /octapass give <xp|level|premium> <player> [amount]</yellow>")); return true; }
        String type = args[1].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { sender.sendMessage(TextUtil.component("<red>Player must be online.</red>")); return true; }
        PlayerProfile profile = plugin.profiles().get(target);
        if (profile == null) return dataNotLoaded(sender);
        int amount = args.length >= 4 ? Math.max(1, parseInt(args[3], 1)) : 1;
        switch (type) {
            case "xp" -> plugin.api().addXp(target.getUniqueId(), amount);
            case "level" -> {
                int newLevel = Math.min(plugin.seasons().curve().maxLevel(), plugin.seasons().curve().levelForXp(profile.totalXp()) + amount);
                profile.totalXp(plugin.seasons().curve().xpTotalToReach(newLevel));
                plugin.profiles().markDirty(profile);
            }
            case "premium" -> { profile.premium(true); plugin.profiles().markDirty(profile); }
            default -> { sender.sendMessage(TextUtil.component("<red>Unknown give type.</red>")); return true; }
        }
        sender.sendMessage(TextUtil.component("<green>Applied " + type + " to " + target.getName() + ".</green>")); return true;
    }

    private boolean set(CommandSender sender, String[] args) {
        if (!admin(sender, "octapass.admin.set")) return true;
        if (args.length < 4 || !args[1].equalsIgnoreCase("level")) {
            sender.sendMessage(TextUtil.component("<yellow>Usage: /octapass set level <player> <level></yellow>")); return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) { sender.sendMessage(TextUtil.component("<red>Player must be online.</red>")); return true; }
        PlayerProfile profile = plugin.profiles().get(target);
        if (profile == null) return dataNotLoaded(sender);
        int level = Math.max(1, Math.min(plugin.seasons().curve().maxLevel(), parseInt(args[3], 1)));
        profile.totalXp(plugin.seasons().curve().xpTotalToReach(level));
        plugin.profiles().markDirty(profile);
        sender.sendMessage(TextUtil.component("<green>Set " + target.getName() + " to pass level " + level + ".</green>")); return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!admin(sender, "octapass.admin.reset")) return true;
        if (args.length < 2) { sender.sendMessage(TextUtil.component("<yellow>Usage: /octapass reset <player|all></yellow>")); return true; }
        if (args[1].equalsIgnoreCase("all")) {
            plugin.database().resetAll().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) sender.sendMessage(TextUtil.component("<red>Database reset failed.</red>"));
                else {
                    plugin.profiles().resetAllInMemory();
                    Bukkit.getOnlinePlayers().forEach(plugin.profiles()::load);
                    sender.sendMessage(TextUtil.component("<green>All season profiles have been reset.</green>"));
                }
            }));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(TextUtil.component("<red>Player must be online.</red>")); return true; }
        PlayerProfile profile = plugin.profiles().reset(target.getUniqueId());
        profile.lastName(target.getName());
        plugin.database().saveAll(List.of(profile)).thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                () -> sender.sendMessage(TextUtil.component("<green>Reset pass progress for " + target.getName() + ".</green>"))));
        return true;
    }

    private boolean season(CommandSender sender, String[] args) {
        if (!admin(sender, "octapass.admin.season")) return true;
        if (args.length < 2) { sender.sendMessage(TextUtil.component("<yellow>Usage: /octapass season <start|end|status></yellow>")); return true; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> { plugin.seasons().forceActive(true); sender.sendMessage(TextUtil.component("<green>Season manually activated for this runtime.</green>")); }
            case "end" -> { plugin.seasons().forceActive(false); sender.sendMessage(TextUtil.component("<yellow>Season manually paused for this runtime.</yellow>")); }
            case "status" -> sender.sendMessage(TextUtil.component("<gray>Season: <white>" + plugin.seasons().season().name() + "</white> | Active: <white>" + plugin.seasons().active() + "</white></gray>"));
            default -> sender.sendMessage(TextUtil.component("<yellow>Usage: /octapass season <start|end|status></yellow>"));
        }
        return true;
    }

    private boolean event(CommandSender sender, String[] args) {
        if (!admin(sender, "octapass.admin.event")) return true;
        if (args.length < 3) { sender.sendMessage(TextUtil.component("<yellow>Usage: /octapass event <start|stop> <id></yellow>")); return true; }
        boolean changed = args[1].equalsIgnoreCase("start") ? plugin.quests().startEvent(args[2])
                : args[1].equalsIgnoreCase("stop") && plugin.quests().stopEvent(args[2]);
        sender.sendMessage(TextUtil.component(changed ? "<green>Event updated: " + args[2] + "</green>" : "<red>Event not found or action invalid.</red>"));
        return true;
    }

    private boolean booster(CommandSender sender, String[] args) {
        if (!admin(sender, "octapass.admin.booster")) return true;
        if (args.length < 5 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(TextUtil.component("<yellow>Usage: /octapass booster give <player|global> <multiplier> <seconds></yellow>")); return true;
        }
        double multiplier = parseDouble(args[3], 1.5);
        long seconds = Math.max(1, parseLong(args[4], 3600));
        if (args[2].equalsIgnoreCase("global")) plugin.boosters().giveGlobal(multiplier, Duration.ofSeconds(seconds));
        else {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) { sender.sendMessage(TextUtil.component("<red>Player must be online.</red>")); return true; }
            plugin.boosters().give(target.getUniqueId(), multiplier, Duration.ofSeconds(seconds));
        }
        sender.sendMessage(TextUtil.component("<green>Booster activated: " + multiplier + "x for " + seconds + " seconds.</green>")); return true;
    }

    private boolean admin(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("octapass.admin")) return true;
        sender.sendMessage(TextUtil.component("<red>You do not have permission.</red>")); return false;
    }
    private boolean playerOnly(CommandSender sender) { sender.sendMessage(TextUtil.component("<red>This command can only be used in-game.</red>")); return true; }
    private boolean dataNotLoaded(CommandSender sender) { sender.sendMessage(TextUtil.component("<red>Player data has not loaded yet.</red>")); return true; }
    private int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; } }
    private long parseLong(String value, long fallback) { try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return fallback; } }
    private double parseDouble(String value, double fallback) { try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { return fallback; } }

    private void help(CommandSender sender) {
        sender.sendMessage(TextUtil.component("<light_purple><bold>Advanced OctaPass</bold></light_purple> <gray>Commands</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass open</white> <gray>Open pass menu</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass quests</white> <gray>Open quest journal</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass rewards</white> <gray>Open reward track</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass claim</white> <gray>Claim unlocked rewards</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass buylevel [amount]</white> <gray>Purchase pass levels</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass reroll</white> <gray>Reroll a daily objective</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass top [xp|quests]</white> <gray>Show leaderboard</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass premium</white> <gray>Premium pass information</gray>"));
        sender.sendMessage(TextUtil.component("<white>/octapass settings</white> <gray>Player preferences</gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("open", "quests", "rewards", "claim", "buylevel", "reroll", "top", "premium", "settings", "help", "reload", "give", "set", "reset", "season", "event", "booster", "debug"), args[0]);
        if (args.length == 2) return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "top" -> filter(List.of("xp", "quests"), args[1]);
            case "give" -> filter(List.of("xp", "level", "premium"), args[1]);
            case "set" -> filter(List.of("level"), args[1]);
            case "reset" -> filter(List.of("all"), args[1]);
            case "season" -> filter(List.of("start", "end", "status"), args[1]);
            case "event" -> filter(List.of("start", "stop"), args[1]);
            case "booster" -> filter(List.of("give"), args[1]);
            case "premium" -> filter(List.of("buy", "give"), args[1]);
            default -> List.of();
        };
        if (args.length == 3 && Set.of("give", "set", "reset", "premium", "event", "booster").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }
}
