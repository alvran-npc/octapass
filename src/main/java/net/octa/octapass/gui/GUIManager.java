package net.octa.octapass.gui;

import net.kyori.adventure.text.Component;
import net.octa.octapass.AdvancedOctaPass;
import net.octa.octapass.core.LevelCurve;
import net.octa.octapass.core.PlayerProfile;
import net.octa.octapass.quest.QuestCategory;
import net.octa.octapass.quest.QuestDefinition;
import net.octa.octapass.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GUIManager implements Listener {
    private static final int[] REWARD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private final AdvancedOctaPass plugin;

    public GUIManager(AdvancedOctaPass plugin) { this.plugin = plugin; }

    public void openMain(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) { player.sendMessage(TextUtil.component("<yellow>Your pass data is still loading.</yellow>")); return; }
        int level = plugin.seasons().curve().levelForXp(profile.totalXp());
        MenuHolder holder = holder("main", 0, 27, "<dark_gray>✦ <light_purple>ADVANCED OCTAPASS</light_purple> ✦");
        fill(holder.getInventory(), Material.PURPLE_STAINED_GLASS_PANE);
        set(holder, 10, Material.WRITABLE_BOOK, "<gradient:#9d7cff:#d5c4ff><bold>Reward Track</bold></gradient>",
                "<gray>Level " + level + "/" + plugin.seasons().curve().maxLevel() + "</gray>", "<yellow>Click to view rewards</yellow>");
        set(holder, 12, Material.CLOCK, "<gradient:#65d8c5:#b2fff0><bold>Quest Journal</bold></gradient>",
                "<gray>Daily, Weekly and Event objectives</gray>", "<yellow>Click to view quests</yellow>");
        set(holder, 14, Material.GOLD_INGOT, "<gold><bold>Leaderboard</bold></gold>",
                "<gray>See seasonal rankings</gray>", "<yellow>Click to view</yellow>");
        set(holder, 16, Material.COMPARATOR, "<aqua><bold>Pass Settings</bold></aqua>",
                "<gray>Notification and progress display</gray>", "<yellow>Click to configure</yellow>");
        set(holder, 22, Material.NETHER_STAR, "<light_purple><bold>Premium Pass</bold></light_purple>",
                profile.premium() ? "<green>Premium unlocked</green>" : "<gray>Preview premium rewards</gray>",
                "<yellow>Click for information</yellow>");
        player.openInventory(holder.getInventory());
    }

    public void openRewards(Player player, int page) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) return;
        int pageSize = REWARD_SLOTS.length;
        int maxPages = Math.max(1, (plugin.seasons().curve().maxLevel() + pageSize - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(page, maxPages - 1));
        int level = plugin.seasons().curve().levelForXp(profile.totalXp());
        MenuHolder holder = holder("rewards", currentPage, 54,
                "<dark_gray>PASS TRACK <gray>•</gray> <light_purple>Lv." + level + "</light_purple> <gray>•</gray> " + (currentPage + 1) + "/" + maxPages);
        fill(holder.getInventory(), Material.BLACK_STAINED_GLASS_PANE);
        int start = currentPage * pageSize + 1;
        for (int index = 0; index < pageSize; index++) {
            int rewardLevel = start + index;
            if (rewardLevel > plugin.seasons().curve().maxLevel()) break;
            boolean unlocked = rewardLevel <= level;
            boolean freeClaimed = plugin.rewards().hasClaimed(profile, rewardLevel, false);
            boolean premClaimed = plugin.rewards().hasClaimed(profile, rewardLevel, true);
            Material icon = !unlocked ? Material.GRAY_DYE : (freeClaimed && (!profile.premium() || premClaimed) ? Material.LIME_DYE : Material.PAPER);
            String state = !unlocked ? "<red>🔒 Locked</red>" : freeClaimed ? "<green>✔ Free claimed</green>" : "<aqua>✦ Free claimable</aqua>";
            String premiumState = !unlocked ? "<dark_gray>Premium locked</dark_gray>" : premClaimed ? "<green>✔ Premium claimed</green>" : profile.premium() ? "<light_purple>✦ Premium claimable</light_purple>" : "<gray>✦ Premium preview</gray>";
            set(holder, REWARD_SLOTS[index], icon, "<gradient:#8d75ee:#e1d6ff><bold>Level " + rewardLevel + "</bold></gradient>", state, premiumState,
                    unlocked ? "<yellow>Click to claim available track rewards</yellow>" : "<gray>Earn more pass XP to unlock</gray>");
        }
        set(holder, 45, Material.ARROW, "<white>Previous Page</white>", "<gray>Go back one page</gray>");
        set(holder, 49, Material.HOPPER, "<gold><bold>Claim All</bold></gold>", "<gray>Claim every unlocked reward</gray>");
        set(holder, 53, Material.ARROW, "<white>Next Page</white>", "<gray>Go forward one page</gray>");
        set(holder, 4, Material.NETHER_STAR, "<light_purple><bold>Season Reward Track</bold></light_purple>",
                "<gray>Free and Premium tracks</gray>", "<gray>Premium rewards remain previewable</gray>");
        player.openInventory(holder.getInventory());
    }

    public void openQuests(Player player, QuestCategory category) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) return;
        MenuHolder holder = holder("quests_" + category.name().toLowerCase(), 0, 54,
                "<dark_gray>QUEST JOURNAL <gray>•</gray> <aqua>" + title(category) + "</aqua>");
        fill(holder.getInventory(), Material.CYAN_STAINED_GLASS_PANE);
        set(holder, 10, Material.SUNFLOWER, "<yellow><bold>Daily</bold></yellow>", "<gray>Resets in " + net.octa.octapass.util.TimeUtil.compact(plugin.quests().untilDailyReset()) + "</gray>");
        set(holder, 13, Material.CLOCK, "<aqua><bold>Weekly</bold></aqua>", "<gray>Resets in " + net.octa.octapass.util.TimeUtil.compact(plugin.quests().untilWeeklyReset()) + "</gray>");
        set(holder, 16, Material.FIREWORK_STAR, "<light_purple><bold>Events</bold></light_purple>", "<gray>Limited-time objectives</gray>");
        List<QuestDefinition> active = plugin.quests().byCategory(category, profile);
        int slot = 19;
        for (QuestDefinition quest : active) {
            if (slot >= 45) break;
            int progress = profile.questProgress().getOrDefault(quest.id(), 0);
            boolean completed = profile.completedQuestIds().contains(quest.id());
            Material material = Material.matchMaterial(quest.icon());
            if (!SafeMenuIcon.allowed(material)) material = Material.PAPER;
            int percent = Math.min(100, progress * 100 / quest.amount());
            List<String> lore = List.of(
                    "<gray>" + quest.description() + "</gray>",
                    "<gray>Progress: <aqua>" + progress + "/" + quest.amount() + "</aqua></gray>",
                    "<green>" + net.octa.octapass.quest.ProgressService.progressBar(percent, 12) + "</green>",
                    completed ? "<green>✔ Completed</green>" : "<yellow>Click to track this objective</yellow>",
                    "<light_purple>Reward: " + quest.xp() + " Pass XP</light_purple>"
            );
            set(holder, slot++, material, quest.displayName(), lore.toArray(String[]::new));
        }
        set(holder, 49, Material.BARRIER, "<red>Close</red>", "<gray>Return to the game</gray>");
        player.openInventory(holder.getInventory());
    }

    public void openLeaderboard(Player player, String metric) {
        MenuHolder holder = holder("leaderboard_" + metric, 0, 54, "<dark_gray>SEASON <gold>LEADERBOARD</gold>");
        fill(holder.getInventory(), Material.BLACK_STAINED_GLASS_PANE);
        set(holder, 22, Material.CLOCK, "<yellow>Loading ranking...</yellow>", "<gray>Reading saved player records</gray>");
        player.openInventory(holder.getInventory());
        plugin.database().leaderboard(metric, 10).whenComplete((entries, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder current) || !current.menu().equals("leaderboard_" + metric)) return;
            current.getInventory().clear();
            fill(current.getInventory(), Material.BLACK_STAINED_GLASS_PANE);
            if (error != null || entries == null || entries.isEmpty()) {
                set(current, 22, Material.BARRIER, "<red>No ranking data</red>", "<gray>Play the season to appear here</gray>");
            } else {
                Material[] medals = {Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT};
                int slot = 10;
                int position = 1;
                for (var entry : entries) {
                    if (slot > 43) break;
                    Material icon = position <= 3 ? medals[position - 1] : Material.PAPER;
                    set(current, slot, icon, "<gold>#" + position + "</gold> <white>" + entry.name() + "</white>",
                            "<gray>" + metricLabel(metric) + ": <aqua>" + entry.value() + "</aqua></gray>");
                    slot += slot % 9 == 7 ? 3 : 1;
                    position++;
                }
            }
            set(current, 49, Material.BARRIER, "<red>Close</red>");
        }));
    }

    public void openPremium(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        MenuHolder holder = holder("premium", 0, 27, "<dark_gray>PREMIUM <light_purple>PASS</light_purple>");
        fill(holder.getInventory(), Material.PURPLE_STAINED_GLASS_PANE);
        set(holder, 13, Material.NETHER_STAR, "<light_purple><bold>Premium Pass</bold></light_purple>",
                profile != null && profile.premium() ? "<green>Already unlocked</green>" : "<gray>Unlock the premium reward track</gray>",
                "<gray>All players can preview its reward schedule</gray>");
        if (profile == null || !profile.premium()) {
            double price = plugin.getConfig().getDouble("premium-purchase.price", 0);
            set(holder, 11, Material.EMERALD, "<green><bold>Purchase Premium</bold></green>",
                    "<gray>Price: " + price + "</gray>", "<yellow>Click to review and confirm</yellow>");
        }
        set(holder, 15, Material.ARROW, "<white>Back</white>");
        player.openInventory(holder.getInventory());
    }

    public void openSettings(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) return;
        MenuHolder holder = holder("settings", 0, 27, "<dark_gray>PASS <aqua>SETTINGS</aqua>");
        fill(holder.getInventory(), Material.BLACK_STAINED_GLASS_PANE);
        set(holder, 11, profile.notificationsEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<white>Quest Notifications</white>", "<gray>Status: " + (profile.notificationsEnabled() ? "<green>ON" : "<red>OFF") + "</gray>");
        set(holder, 13, profile.actionBarEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<white>Action Bar Tracker</white>", "<gray>Status: " + (profile.actionBarEnabled() ? "<green>ON" : "<red>OFF") + "</gray>");
        set(holder, 15, profile.bossBarEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<white>Boss Bar</white>", "<gray>Status: " + (profile.bossBarEnabled() ? "<green>ON" : "<red>OFF") + "</gray>");
        set(holder, 22, Material.ARROW, "<white>Back</white>");
        player.openInventory(holder.getInventory());
    }

    public void openConfirmPremium(Player player) {
        MenuHolder holder = holder("confirm_premium", 0, 27, "<dark_gray>CONFIRM <light_purple>PREMIUM</light_purple>");
        fill(holder.getInventory(), Material.BLACK_STAINED_GLASS_PANE);
        set(holder, 11, Material.LIME_CONCRETE, "<green><bold>Confirm Purchase</bold></green>", "<gray>Charge the configured Vault balance</gray>");
        set(holder, 15, Material.RED_CONCRETE, "<red>Cancel</red>");
        player.openInventory(holder.getInventory());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        switch (holder.menu()) {
            case "main" -> {
                if (slot == 10) openRewards(player, 0);
                else if (slot == 12) openQuests(player, QuestCategory.DAILY);
                else if (slot == 14) openLeaderboard(player, "xp");
                else if (slot == 16) openSettings(player);
                else if (slot == 22) openPremium(player);
            }
            case "rewards" -> {
                if (slot == 45) openRewards(player, holder.page() - 1);
                else if (slot == 53) openRewards(player, holder.page() + 1);
                else if (slot == 49) {
                    int count = plugin.rewards().claimAll(player);
                    player.sendMessage(TextUtil.component("<green>Claimed " + count + " reward track(s).</green>"));
                    openRewards(player, holder.page());
                } else {
                    int index = indexOf(REWARD_SLOTS, slot);
                    if (index >= 0) {
                        int level = holder.page() * REWARD_SLOTS.length + index + 1;
                        int claimed = 0;
                        if (plugin.rewards().claimTrack(player, level, false)) claimed++;
                        PlayerProfile profile = plugin.profiles().get(player);
                        if (profile != null && (profile.premium() || player.hasPermission("octapass.premium")) && plugin.rewards().claimTrack(player, level, true)) claimed++;
                        if (claimed > 0) player.sendMessage(TextUtil.component("<green>Rewards claimed: " + claimed + "</green>"));
                        openRewards(player, holder.page());
                    }
                }
            }
            case "quests_daily", "quests_weekly", "quests_event", "quests_season" -> {
                if (slot == 10) openQuests(player, QuestCategory.DAILY);
                else if (slot == 13) openQuests(player, QuestCategory.WEEKLY);
                else if (slot == 16) openQuests(player, QuestCategory.EVENT);
                else if (slot == 49) openMain(player);
                else {
                    ItemStack clicked = event.getCurrentItem();
                    if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                        String display = org.bukkit.ChatColor.stripColor(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(clicked.getItemMeta().displayName()));
                        for (QuestDefinition quest : plugin.quests().all()) {
                            String questDisplay = org.bukkit.ChatColor.stripColor(TextUtil.plain(quest.displayName()));
                            if (questDisplay.equalsIgnoreCase(display)) {
                                if (plugin.progress().track(player, quest.id())) player.sendMessage(TextUtil.component("<green>Tracking: " + quest.displayName() + "</green>"));
                                break;
                            }
                        }
                    }
                }
            }
            case "leaderboard_xp", "leaderboard_quests" -> { if (slot == 49) openMain(player); }
            case "premium" -> {
                if (slot == 11) openConfirmPremium(player);
                else if (slot == 15) openMain(player);
            }
            case "confirm_premium" -> {
                if (slot == 11) {
                    if (plugin.purchasePremium(player)) player.sendMessage(TextUtil.component("<green>Premium Pass unlocked.</green>"));
                    else player.sendMessage(TextUtil.component("<red>Purchase could not be completed.</red>"));
                    openPremium(player);
                } else if (slot == 15) openPremium(player);
            }
            case "settings" -> {
                PlayerProfile profile = plugin.profiles().get(player);
                if (profile == null) return;
                if (slot == 11) profile.notificationsEnabled(!profile.notificationsEnabled());
                else if (slot == 13) profile.actionBarEnabled(!profile.actionBarEnabled());
                else if (slot == 15) profile.bossBarEnabled(!profile.bossBarEnabled());
                else if (slot == 22) { openMain(player); return; }
                plugin.profiles().markDirty(profile);
                openSettings(player);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private MenuHolder holder(String menu, int page, int size, String title) {
        MenuHolder holder = new MenuHolder(menu, page);
        Inventory inventory = Bukkit.createInventory(holder, size, TextUtil.component(title));
        holder.inventory(inventory);
        return holder;
    }
    private void fill(Inventory inventory, Material material) {
        ItemStack pane = item(material, "<dark_gray> </dark_gray>", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }
    private void set(MenuHolder holder, int slot, Material material, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(TextUtil.component(line));
        holder.getInventory().setItem(slot, item(material, name, lines));
    }
    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(TextUtil.component(name)); if (!lore.isEmpty()) meta.lore(lore); item.setItemMeta(meta); }
        return item;
    }
    private int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) if (values[i] == target) return i;
        return -1;
    }
    private String title(QuestCategory category) {
        return switch (category) { case DAILY -> "Daily"; case WEEKLY -> "Weekly"; case EVENT -> "Event"; case SEASON -> "Season"; };
    }
    private String metricLabel(String metric) { return metric.equalsIgnoreCase("quests") ? "Quests completed" : "Pass XP"; }

    private static final class SafeMenuIcon {
        static boolean allowed(Material material) { return material != null && (material.isBlock() || material.isEdible() || material == Material.PAPER || material == Material.CLOCK || material == Material.COMPASS || material == Material.BOOK); }
    }
}
