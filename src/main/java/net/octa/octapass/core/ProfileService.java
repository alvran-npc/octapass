package net.octa.octapass.core;

import net.octa.octapass.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ProfileService {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final SeasonService seasons;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private volatile Consumer<Player> readyCallback = player -> {};

    public ProfileService(JavaPlugin plugin, DatabaseManager database, SeasonService seasons) {
        this.plugin = plugin; this.database = database; this.seasons = seasons;
    }

    public void load(Player player) {
        UUID id = player.getUniqueId();
        if (cache.containsKey(id) || !loading.add(id)) return;
        database.load(id, seasons.season().id()).whenComplete((profile, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            loading.remove(id);
            if (error != null) {
                plugin.getLogger().severe("Could not load data for " + player.getName() + ": " + error.getMessage());
                return;
            }
            profile.lastName(player.getName());
            cache.put(id, profile);
            dirty.add(id);
            if (player.isOnline()) {
                player.sendMessage(net.octa.octapass.util.TextUtil.component("<dark_gray>[<light_purple>OctaPass</light_purple>]</dark_gray> <gray>Profile loaded.</gray>"));
                readyCallback.accept(player);
            }
        }));
    }

    public void onReady(Consumer<Player> callback) { this.readyCallback = callback == null ? player -> {} : callback; }

    public PlayerProfile get(UUID id) { return cache.get(id); }
    public PlayerProfile get(Player player) { return get(player.getUniqueId()); }
    public Collection<PlayerProfile> profiles() { return Collections.unmodifiableCollection(cache.values()); }
    public void markDirty(PlayerProfile profile) { dirty.add(profile.uniqueId()); }
    public boolean isLoaded(UUID id) { return cache.containsKey(id); }
    public void unload(UUID id) { dirty.add(id); }

    public CompletableFuture<Void> saveDirty() {
        Set<UUID> pending = new HashSet<>(dirty);
        dirty.removeAll(pending);
        List<PlayerProfile> snapshots = new ArrayList<>();
        for (UUID id : pending) {
            PlayerProfile profile = cache.get(id);
            if (profile != null) snapshots.add(snapshot(profile));
        }
        if (snapshots.isEmpty()) return CompletableFuture.completedFuture(null);
        return database.saveAll(snapshots).whenComplete((ignored, error) -> {
            if (error != null) {
                dirty.addAll(pending);
                plugin.getLogger().severe("Auto-save failed: " + error.getMessage());
            }
        });
    }

    public CompletableFuture<Void> saveAll() {
        List<PlayerProfile> snapshots = cache.values().stream().map(this::snapshot).toList();
        return database.saveAll(snapshots);
    }

    public void resetAllInMemory() { cache.clear(); dirty.clear(); }

    public PlayerProfile reset(UUID id) {
        PlayerProfile profile = new PlayerProfile(id, seasons.season().id());
        cache.put(id, profile);
        dirty.add(id);
        return profile;
    }

    private PlayerProfile snapshot(PlayerProfile source) {
        return PlayerProfile.fromMap(source.uniqueId(), source.seasonId(), source.toMap());
    }
}
