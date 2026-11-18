package net.octa.octapass.reward;

import org.bukkit.Material;

import java.util.Set;

/**
 * Restricts pass item grants to ordinary building, farming, food and cosmetic items.
 *
 * <p>The string-based classifier is deliberately independent of Bukkit's runtime
 * registries, which allows it to be unit-tested without bootstrapping a Paper server.
 * The Material adapter is used only at runtime when the plugin is running on Paper.</p>
 */
public final class SafeItemPolicy {
    private static final Set<String> DENIED = Set.of(
            "TNT", "TNT_MINECART", "RESPAWN_ANCHOR", "END_CRYSTAL",
            "FIREWORK_ROCKET", "FIRE_CHARGE", "FLINT_AND_STEEL",
            "POTION", "SPLASH_POTION", "LINGERING_POTION", "TIPPED_ARROW",
            "ROTTEN_FLESH", "SPIDER_EYE", "FERMENTED_SPIDER_EYE",
            "POISONOUS_POTATO", "PUFFERFISH",
            "BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "STRUCTURE_VOID",
            "JIGSAW", "SPAWNER", "TRIAL_SPAWNER", "VAULT",
            "END_PORTAL_FRAME", "LIGHT", "KNOWLEDGE_BOOK", "DEBUG_STICK"
    );

    private static final Set<String> COSMETIC = Set.of(
            "PAPER", "MAP", "FILLED_MAP", "NAME_TAG", "PLAYER_HEAD", "COMPASS", "CLOCK",
            "BOOK", "WRITABLE_BOOK", "WRITTEN_BOOK", "PAINTING", "ITEM_FRAME",
            "GLOW_ITEM_FRAME", "FLOWER_POT"
    );

    private SafeItemPolicy() {
    }

    /**
     * Checks a Bukkit material. Call this method on the server where Paper's material
     * registries are initialized; pure unit tests should use the string classifier below.
     */
    public static boolean allowed(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        if (name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR")) {
            return false;
        }
        return allowed(name, material.isBlock(), material.isEdible());
    }

    /** Registry-free classifier, package-visible so it can be tested without a Paper server. */
    static boolean allowed(String materialName, boolean block, boolean edible) {
        if (materialName == null) {
            return false;
        }
        String name = materialName.toUpperCase(java.util.Locale.ROOT);
        if (name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR") || DENIED.contains(name)) {
            return false;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") ||
                name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_BOW") ||
                name.endsWith("_CROSSBOW") || name.endsWith("_TRIDENT") || name.equals("MACE") ||
                name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") ||
                name.endsWith("_BOOTS") || name.equals("SHIELD") || name.endsWith("_ARROW")) {
            return false;
        }
        return block || edible || COSMETIC.contains(name);
    }
}
