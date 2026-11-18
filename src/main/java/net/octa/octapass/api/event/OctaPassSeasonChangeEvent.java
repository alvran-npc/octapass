package net.octa.octapass.api.event;

import net.octa.octapass.core.Season;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OctaPassSeasonChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Season previousSeason;
    private final Season newSeason;
    public OctaPassSeasonChangeEvent(Season previousSeason, Season newSeason) {
        this.previousSeason = previousSeason; this.newSeason = newSeason;
    }
    public Season getPreviousSeason() { return previousSeason; }
    public Season getNewSeason() { return newSeason; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
