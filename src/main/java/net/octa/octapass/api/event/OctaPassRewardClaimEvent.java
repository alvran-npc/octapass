package net.octa.octapass.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OctaPassRewardClaimEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String rewardKey;
    private boolean cancelled;
    public OctaPassRewardClaimEvent(Player player, String rewardKey) { this.player = player; this.rewardKey = rewardKey; }
    public Player getPlayer() { return player; }
    public String getRewardKey() { return rewardKey; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean value) { cancelled = value; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
