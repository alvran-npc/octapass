package net.octa.octapass.quest;

import net.octa.octapass.AdvancedOctaPass;
import net.octa.octapass.core.PlayerProfile;
import net.octa.octapass.reward.SafeItemPolicy;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Beehive;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestListener implements Listener {
    private final AdvancedOctaPass plugin;
    private final Set<String> playerPlacedBlocks = ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, Long> chatCooldown = new ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> commandCooldown = new ConcurrentHashMap<>();

    public QuestListener(AdvancedOctaPass plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (plugin.getConfig().getBoolean("anti-exploit.block-tracking", true)) playerPlacedBlocks.add(key(block));
        plugin.progress().signal(event.getPlayer(), "block-place", block.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = key(block);
        boolean placedByPlayer = plugin.getConfig().getBoolean("anti-exploit.ignore-player-placed-blocks", true) && playerPlacedBlocks.remove(key);
        if (placedByPlayer) return;
        Material material = block.getType();
        plugin.progress().signal(event.getPlayer(), "block-break", material.name());
        if (isCrop(material) && isMature(block)) plugin.progress().signal(event.getPlayer(), "harvest-crops", material.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        plugin.progress().signal(event.getPlayer(), "bucket-place", event.getBucket().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || event.getCaught() == null) return;
        plugin.progress().signal(event.getPlayer(), "fish", event.getCaught().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Material material = event.getRecipe().getResult().getType();
        if (SafeItemPolicy.allowed(material)) plugin.progress().signal(player, "craft", material.name(), event.getRecipe().getResult().getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (player != null && SafeItemPolicy.allowed(event.getItemType())) {
            plugin.progress().signal(player, "smelt", event.getItemType().name(), event.getItemAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType().isEdible()) plugin.progress().signal(event.getPlayer(), "consume", event.getItem().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) plugin.progress().signal(player, "breed", event.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) plugin.progress().signal(player, "tame", event.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        plugin.progress().signal(event.getPlayer(), "shear", event.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMilk(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Cow)) return;
        ItemStack item = event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        if (item.getType() == Material.BUCKET) plugin.progress().signal(event.getPlayer(), "milk", "COW");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExperience(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0) plugin.progress().signal(event.getPlayer(), "gain-experience", "XP", event.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        plugin.progress().signal(player, "move", "BLOCK", 1);
        if (player.isInWater()) plugin.progress().signal(player, "swim", "WATER", 1);
        if (player.isGliding()) plugin.progress().signal(player, "glide", "ELYTRA", 1);
        if (player.isFlying()) plugin.progress().signal(player, "fly", "FLY", 1);
        if (player.isSprinting()) plugin.progress().signal(player, "sprint", "SPRINT", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting()) plugin.progress().signal(event.getPlayer(), "sprint", "SPRINT", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) plugin.progress().signal(event.getPlayer(), "sneak", "SNEAK", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying()) plugin.progress().signal(event.getPlayer(), "fly", "FLY", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            plugin.progress().signal(player, "right-click", player.getInventory().getItemInMainHand().getType().name());
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                Material block = event.getClickedBlock().getType();
                plugin.progress().signal(player, "right-click-block", block.name());
                if ((block == Material.BEEHIVE || block == Material.BEE_NEST)
                        && event.getClickedBlock().getBlockData() instanceof Beehive hive && hive.getHoneyLevel() >= 5) {
                    Material hand = (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                            ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand()).getType();
                    if (hand == Material.GLASS_BOTTLE) plugin.progress().signal(player, "honey-extract", block.name());
                    if (hand == Material.SHEARS) plugin.progress().signal(player, "honey-comb-extract", block.name());
                }
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            plugin.progress().signal(player, "left-click", player.getInventory().getItemInMainHand().getType().name());
            if (action == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
                plugin.progress().signal(player, "left-click-block", event.getClickedBlock().getType().name());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().split(" ", 2)[0].replaceFirst("^/", "").toLowerCase();
        long now = System.currentTimeMillis();
        long previous = commandCooldown.getOrDefault(event.getPlayer().getUniqueId(), 0L);
        if (now - previous < plugin.getConfig().getLong("anti-exploit.command-cooldown-ms", 500L)) return;
        commandCooldown.put(event.getPlayer().getUniqueId(), now);
        plugin.progress().signal(event.getPlayer(), "execute-command", command);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long previous = chatCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < plugin.getConfig().getLong("anti-exploit.chat-cooldown-ms", 1500L)) return;
        chatCooldown.put(player.getUniqueId(), now);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.progress().signal(player, "chat", "CHAT"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.database().isReady()) plugin.profiles().load(event.getPlayer());
        // If the database is still starting, the enable callback loads all online players once it is ready.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerProfile profile = plugin.profiles().get(event.getPlayer());
        if (profile != null) plugin.profiles().markDirty(profile);
        plugin.progress().clearPlayer(event.getPlayer().getUniqueId());
    }

    public void startPlaytimeCounter() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (plugin.profiles().isLoaded(player.getUniqueId())) plugin.progress().signal(player, "playtime", "MINUTE", 1);
            }
        }, 1200L, 1200L);
    }

    private boolean isCrop(Material material) {
        return switch (material) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, COCOA, SWEET_BERRY_BUSH, MELON, PUMPKIN -> true;
            default -> false;
        };
    }
    private boolean isMature(Block block) {
        if (block.getBlockData() instanceof Ageable ageable) return ageable.getAge() >= ageable.getMaximumAge();
        return block.getType() == Material.MELON || block.getType() == Material.PUMPKIN;
    }
    private String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
