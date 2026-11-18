package net.octa.octapass;

import net.octa.octapass.api.OctaPassAPI;
import net.octa.octapass.api.event.OctaPassLevelUpEvent;
import net.octa.octapass.core.*;
import net.octa.octapass.gui.GUIManager;
import net.octa.octapass.hook.PlaceholderQuestHook;
import net.octa.octapass.hook.VaultHook;
import net.octa.octapass.hook.octagens.OctaGensHook;
import net.octa.octapass.hook.octammo.OctaMMOHook;
import net.octa.octapass.placeholder.OctaPassExpansion;
import net.octa.octapass.quest.*;
import net.octa.octapass.quest.action.QuestActionRegistry;
import net.octa.octapass.reward.RewardService;
import net.octa.octapass.storage.DatabaseManager;
import net.octa.octapass.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class AdvancedOctaPass extends JavaPlugin {
    private final AtomicLong startupNanos = new AtomicLong();
    private ExecutorService databaseExecutor;
    private DatabaseManager database;
    private SeasonService seasons;
    private QuestService quests;
    private ProfileService profiles;
    private QuestActionRegistry actionRegistry;
    private ProgressService progress;
    private RewardService rewards;
    private BoosterService boosters;
    private VaultHook vault;
    private GUIManager gui;
    private OctaGensHook octaGensHook;
    private OctaMMOHook octaMMOHook;
    private OctaPassExpansion expansion;
    private OctaPassAPI api;
    private volatile List<DatabaseManager.LeaderboardEntry> topLeaderboard = List.of();
    private boolean placeholderApiReady;

    @Override
    public void onEnable() {
        startupNanos.set(System.nanoTime());
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create plugin data folder.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        saveIfMissing("messages.yml");
        saveIfMissing("season.yml");
        saveIfMissing("rewards/free.yml");
        saveIfMissing("rewards/premium.yml");
        saveIfMissing("quests/daily.yml");
        saveIfMissing("quests/weekly.yml");
        saveIfMissing("quests/season.yml");
        saveIfMissing("quests/events/christmas.yml");
        saveIfMissing("quests/events/ramadan.yml");
        saveIfMissing("gui/main.yml");
        saveIfMissing("gui/rewards.yml");
        saveIfMissing("gui/quests.yml");
        saveIfMissing("lang/id.yml");
        saveIfMissing("lang/en.yml");

        seasons = new SeasonService(this);
        seasons.load();
        quests = new QuestService(this);
        quests.load();
        boosters = new BoosterService();
        vault = new VaultHook(this);
        boolean vaultAvailable = vault.setup();
        rewards = null;
        databaseExecutor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "AdvancedOctaPass-Database");
            thread.setDaemon(false);
            return thread;
        });
        database = new DatabaseManager(this, databaseExecutor);
        profiles = new ProfileService(this, database, seasons);
        actionRegistry = new QuestActionRegistry();
        progress = new ProgressService(this, profiles, quests, seasons, actionRegistry);
        rewards = new RewardService(this, profiles, seasons);
        rewards.load();
        gui = new GUIManager(this);
        octaGensHook = new OctaGensHook(this);
        octaGensHook.setup();
        octaMMOHook = new OctaMMOHook(this);
        octaMMOHook.setup();
        profiles.onReady(player -> progress.signal(player, "login", "JOIN", 1));

        QuestListener questListener = new QuestListener(this);
        getServer().getPluginManager().registerEvents(questListener, this);
        questListener.startPlaytimeCounter();
        getServer().getPluginManager().registerEvents(gui, this);
        registerCommand();
        registerApi();
        registerPlaceholderApi();
        printStartupBanner(vaultAvailable);

        database.initialize(getConfig()).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (!isEnabled()) return;
            if (error != null) {
                getLogger().severe("Database initialization failed: " + rootMessage(error));
                getLogger().severe("OctaPass remains loaded, but player progression is paused until a restart after the database issue is fixed.");
                return;
            }
            getLogger().info("Database ready. Loading online profiles...");
            for (Player player : Bukkit.getOnlinePlayers()) profiles.load(player);
            refreshTopLeaderboard();
            getLogger().info("Loaded " + quests.size() + " quest definitions and " + rewards.rewardCount() + " reward entries in " + elapsedMillis() + " ms.");
        }));

        if (placeholderApiReady) new PlaceholderQuestHook(this).start();
        long autosaveTicks = Math.max(20L, getConfig().getLong("autosave.interval-seconds", 60) * 20L);
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (database.isReady()) profiles.saveDirty();
        }, autosaveTicks, autosaveTicks);
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::refreshTopLeaderboard, 1200L, 1200L);
    }

    private void saveIfMissing(String resource) {
        File file = new File(getDataFolder(), resource);
        if (file.exists()) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            getLogger().warning("Could not create directory for " + resource);
            return;
        }
        saveResource(resource, false);
    }

    private void registerCommand() {
        var command = getCommand("octapass");
        if (command == null) {
            getLogger().severe("octapass command is missing from plugin.yml");
            return;
        }
        net.octa.octapass.command.OctaPassCommand executor = new net.octa.octapass.command.OctaPassCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }


    private void registerApi() {
        api = new OctaPassAPI() {
            @Override public void addXp(UUID playerId, long amount) {
                runOnMain(() -> {
                    PlayerProfile profile = profiles.get(playerId);
                    Player player = Bukkit.getPlayer(playerId);
                    if (profile == null || amount <= 0) return;
                    int oldLevel = seasons.curve().levelForXp(profile.totalXp());
                    profile.addXp(amount);
                    int newLevel = seasons.curve().levelForXp(profile.totalXp());
                    profiles.markDirty(profile);
                    if (player != null && newLevel > oldLevel) Bukkit.getPluginManager().callEvent(new OctaPassLevelUpEvent(player, oldLevel, newLevel));
                });
            }
            @Override public OptionalInt getLevel(UUID playerId) {
                PlayerProfile profile = profiles.get(playerId);
                return profile == null ? OptionalInt.empty() : OptionalInt.of(seasons.curve().levelForXp(profile.totalXp()));
            }
            @Override public boolean hasPremium(UUID playerId) {
                PlayerProfile profile = profiles.get(playerId);
                Player player = Bukkit.getPlayer(playerId);
                return (profile != null && profile.premium()) || (player != null && player.hasPermission("octapass.premium"));
            }
            @Override public void registerQuestAction(String id, net.octa.octapass.quest.action.QuestAction action) { actionRegistry.register(id, action); }
            @Override public void triggerCustom(Player player, String id, String variable, int amount) {
                runOnMain(() -> progress.triggerCustom(player, id, variable, amount));
            }
            @Override public void setPremium(UUID playerId, boolean value) {
                runOnMain(() -> {
                    PlayerProfile profile = profiles.get(playerId);
                    if (profile != null) { profile.premium(value); profiles.markDirty(profile); }
                });
            }
        };
        getServer().getServicesManager().register(OctaPassAPI.class, api, this, ServicePriority.Normal);
    }

    private void registerPlaceholderApi() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            expansion = new net.octa.octapass.placeholder.OctaPassExpansion(this);
            placeholderApiReady = expansion.register();
            if (placeholderApiReady) getLogger().info("PlaceholderAPI expansion registered: %octapass_...%");
        } catch (LinkageError error) {
            getLogger().warning("PlaceholderAPI found, but expansion could not be loaded: " + error.getMessage());
            placeholderApiReady = false;
        }
    }

    private void printStartupBanner(boolean vaultAvailable) {
        if (!getConfig().getBoolean("startup-banner", true)) return;
        ConsoleCommandSender console = getServer().getConsoleSender();
        console.sendMessage(TextUtil.component("<dark_purple> ██████╗  ██████╗████████╗ █████╗ ██████╗  █████╗ ███████╗███████╗</dark_purple>"));
        console.sendMessage(TextUtil.component("<light_purple>██╔═══██╗██╔════╝╚══██╔══╝██╔══██╗██╔══██╗██╔══██╗██╔════╝██╔════╝</light_purple>"));
        console.sendMessage(TextUtil.component("<light_purple>██║   ██║██║        ██║   ███████║██████╔╝███████║███████╗███████╗</light_purple>"));
        console.sendMessage(TextUtil.component("<light_purple>██║   ██║██║        ██║   ██╔══██║██╔═══╝ ██╔══██║╚════██║╚════██║</light_purple>"));
        console.sendMessage(TextUtil.component("<dark_purple>╚██████╔╝╚██████╗   ██║   ██║  ██║██║     ██║  ██║███████║███████║</dark_purple>"));
        console.sendMessage(TextUtil.component("<dark_purple> ╚═════╝  ╚═════╝   ╚═╝   ╚═╝  ╚═╝╚═╝     ╚═╝  ╚═╝╚══════╝╚══════╝</dark_purple>"));
        console.sendMessage(TextUtil.component("<gray>Advanced OctaPass <white>" + getDescription().getVersion() + "</white> <dark_gray>|</dark_gray> Author: <white>Octa Contributors</white></gray>"));
        console.sendMessage(TextUtil.component("<gray>Server: <white>" + Bukkit.getVersion() + "</white> <dark_gray>|</dark_gray> Storage: <white>" + getConfig().getString("storage.type", "sqlite") + "</white></gray>"));
        dependencyLine("Vault", vaultAvailable);
        dependencyLine("ExcellentEconomy", getServer().getPluginManager().getPlugin("ExcellentEconomy") != null);
        dependencyLine("PlaceholderAPI", getServer().getPluginManager().getPlugin("PlaceholderAPI") != null);
        dependencyLine("MMOItems", getServer().getPluginManager().getPlugin("MMOItems") != null);
        dependencyLine("AdvancedEnchantments", getServer().getPluginManager().getPlugin("AdvancedEnchantments") != null);
        dependencyLine("OctaMMO", getServer().getPluginManager().getPlugin("OctaMMO") != null);
        dependencyLine("OctaGens", getServer().getPluginManager().getPlugin("OctaGens") != null);
        dependencyLine("Floodgate", getServer().getPluginManager().getPlugin("floodgate") != null);
    }

    private void dependencyLine(String name, boolean present) {
        getServer().getConsoleSender().sendMessage(TextUtil.component((present ? "<green>✔ " : "<red>✘ ") + name + (present ? " <gray>detected</gray>" : " <gray>not detected; related optional hooks are inactive</gray>")));
    }

    private void refreshTopLeaderboard() {
        if (database == null || !database.isReady()) return;
        database.leaderboard("xp", 10).thenAccept(entries -> topLeaderboard = List.copyOf(entries))
                .exceptionally(error -> { getLogger().warning("Leaderboard refresh failed: " + rootMessage(error)); return null; });
    }

    public boolean purchasePremium(Player player) {
        PlayerProfile profile = profiles.get(player);
        if (profile == null) return false;
        if (profile.premium() || player.hasPermission("octapass.premium")) return true;
        if (!getConfig().getBoolean("premium-purchase.enabled", false) || !vault.available()) return false;
        double price = getConfig().getDouble("premium-purchase.price", 0);
        if (price <= 0) return false;
        var response = vault.economy().withdrawPlayer(player, price);
        if (!response.transactionSuccess()) return false;
        profile.premium(true);
        profiles.markDirty(profile);
        return true;
    }

    public int purchaseLevels(Player player, int requested) {
        PlayerProfile profile = profiles.get(player);
        if (profile == null || !getConfig().getBoolean("level-purchase.enabled", false) || !vault.available()) return 0;
        int amount = Math.max(1, Math.min(getConfig().getInt("level-purchase.max-per-transaction", 10), requested));
        int currentLevel = seasons.curve().levelForXp(profile.totalXp());
        int targetLevel = Math.min(seasons.curve().maxLevel(), currentLevel + amount);
        int levels = targetLevel - currentLevel;
        if (levels <= 0) return 0;
        double cost = levels * getConfig().getDouble("level-purchase.price-per-level", 1000);
        var response = vault.economy().withdrawPlayer(player, cost);
        if (!response.transactionSuccess()) return 0;
        long targetXp = seasons.curve().xpTotalToReach(targetLevel);
        long oldXp = profile.totalXp();
        int oldLevel = seasons.curve().levelForXp(oldXp);
        profile.totalXp(Math.max(oldXp, targetXp));
        int newLevel = seasons.curve().levelForXp(profile.totalXp());
        profiles.markDirty(profile);
        if (newLevel > oldLevel) Bukkit.getPluginManager().callEvent(new OctaPassLevelUpEvent(player, oldLevel, newLevel));
        return newLevel - oldLevel;
    }

    public void reloadAll() {
        var previousSeason = seasons.season();
        var previousCurve = seasons.curve();
        List<PlayerProfile> previousProfiles = profiles.profiles().stream()
                .map(profile -> PlayerProfile.fromMap(profile.uniqueId(), profile.seasonId(), profile.toMap())).toList();
        reloadConfig();
        seasons.load();
        quests.load();
        rewards.load();
        if (!previousSeason.id().equals(seasons.season().id())) {
            database.archiveSeason(previousSeason.id(), previousProfiles, previousCurve)
                    .exceptionally(error -> { getLogger().warning("Season archive could not be written: " + rootMessage(error)); return null; });
            profiles.resetAllInMemory();
            Bukkit.getOnlinePlayers().forEach(profiles::load);
            Bukkit.getPluginManager().callEvent(new net.octa.octapass.api.event.OctaPassSeasonChangeEvent(previousSeason, seasons.season()));
        }
        getLogger().info("Configuration reloaded: " + quests.size() + " quests, " + rewards.rewardCount() + " reward entries.");
        getLogger().warning("Database connection settings are not hot-swapped; restart the server to change storage backend or credentials.");
    }

    private void runOnMain(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) runnable.run();
        else Bukkit.getScheduler().runTask(this, runnable);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
    private long elapsedMillis() { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startupNanos.get()); }

    @Override
    public void onDisable() {
        getLogger().info("Advanced OctaPass is shutting down; scheduling final asynchronous save...");
        if (getServer().getServicesManager().isProvidedFor(OctaPassAPI.class)) getServer().getServicesManager().unregister(OctaPassAPI.class, api);
        if (expansion != null) expansion.unregister();
        if (database != null && database.isReady()) {
            database.closeAsync(profiles == null ? List.of() : profiles.profiles()).whenComplete((ignored, error) -> {
                if (databaseExecutor != null) databaseExecutor.shutdown();
            });
        } else if (databaseExecutor != null) {
            databaseExecutor.shutdown();
        }
    }

    public DatabaseManager database() { return database; }
    public SeasonService seasons() { return seasons; }
    public QuestService quests() { return quests; }
    public ProfileService profiles() { return profiles; }
    public ProgressService progress() { return progress; }
    public RewardService rewards() { return rewards; }
    public BoosterService boosters() { return boosters; }
    public VaultHook vault() { return vault; }
    public GUIManager gui() { return gui; }
    public List<DatabaseManager.LeaderboardEntry> topLeaderboard() { return topLeaderboard; }
    public OctaPassAPI api() { return api; }
    public boolean placeholderApiReady() { return placeholderApiReady; }
}
