package net.octa.octapass.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OctaPassLevelUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int previousLevel;
    private final int newLevel;
    public OctaPassLevelUpEvent(Player player, int previousLevel, int newLevel) {
        this.player = player; this.previousLevel = previousLevel; this.newLevel = newLevel;
    }
    public Player getPlayer() { return player; }
    public int getPreviousLevel() { return previousLevel; }
    public int getNewLevel() { return newLevel; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
