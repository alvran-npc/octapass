package net.octa.octapass.quest.action;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Extensible registry for server-side quest triggers. */
public final class QuestActionRegistry {
    private final Map<String, QuestAction> actions = new ConcurrentHashMap<>();
    public void register(String id, QuestAction action) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Action id cannot be blank");
        String normalized = id.toLowerCase(Locale.ROOT);
        if (isRestrictedType(normalized)) {
            throw new IllegalArgumentException("This quest action is not enabled in this non-combat pass implementation");
        }
        actions.put(normalized, Objects.requireNonNull(action));
    }
    public static boolean isRestrictedType(String id) {
        if (id == null) return false;
        return Set.of("kill-mob", "kill-player", "damage-player", "throw-projectile", "item-break",
                "enchant", "enchant-anvil", "enchant-all", "smith", "advancedenchantments_enchant",
                "mmoitems_craft", "mmoitems_use", "mmoitems_upgrade", "octammo_upgrade").contains(id.toLowerCase(Locale.ROOT));
    }
    public QuestAction get(String id) { return id == null ? null : actions.get(id.toLowerCase(Locale.ROOT)); }
    public boolean contains(String id) { return get(id) != null; }
    public Set<String> ids() { return Collections.unmodifiableSet(actions.keySet()); }
}
