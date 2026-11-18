package net.octa.octapass.api;

import net.octa.octapass.quest.action.QuestAction;
import org.bukkit.entity.Player;

import java.util.OptionalInt;
import java.util.UUID;

/** Public service contract for other plugins integrating with Advanced OctaPass. */
public interface OctaPassAPI {
    /** Adds season XP to an online player's cached profile. */
    void addXp(UUID playerId, long amount);
    /** Returns the player's current pass level when their profile is loaded. */
    OptionalInt getLevel(UUID playerId);
    /** Returns whether the player's profile has Premium Pass enabled. */
    boolean hasPremium(UUID playerId);
    /** Registers an optional callback invoked after a custom trigger has advanced matching quest progress. */
    void registerQuestAction(String id, QuestAction action);
    /** Advances active quests matching this custom action id; safe to call from an external plugin event. */
    void triggerCustom(Player player, String id, String variable, int amount);
    /** Marks a player as Premium Pass owner for the current season. */
    void setPremium(UUID playerId, boolean premium);
}
