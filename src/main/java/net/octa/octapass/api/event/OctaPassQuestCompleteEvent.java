package net.octa.octapass.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OctaPassQuestCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String questId;
    private final long xpAwarded;
    public OctaPassQuestCompleteEvent(Player player, String questId, long xpAwarded) {
        this.player = player; this.questId = questId; this.xpAwarded = xpAwarded;
    }
    public Player getPlayer() { return player; }
    public String getQuestId() { return questId; }
    public long getXpAwarded() { return xpAwarded; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
