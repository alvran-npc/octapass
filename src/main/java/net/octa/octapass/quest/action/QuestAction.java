package net.octa.octapass.quest.action;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface QuestAction {
    void trigger(Player player, String variable, int amount);
}
