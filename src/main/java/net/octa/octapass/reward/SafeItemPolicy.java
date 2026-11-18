package net.octa.octapass.reward;

import org.bukkit.Material;

import java.util.Set;

/** Restricts pass item grants to ordinary building, farming, food and cosmetic items. */
public final class SafeItemPolicy {
    private static final Set<Material> DENIED = Set.of(
            Material.TNT, Material.TNT_MINECART, Material.RESPAWN_ANCHOR, Material.END_CRYSTAL,
            Material.FIREWORK_ROCKET, Material.FIRE_CHARGE, Material.FLINT_AND_STEEL,
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.TIPPED_ARROW,
            Material.ROTTEN_FLESH, Material.SPIDER_EYE, Material.FERMENTED_SPIDER_EYE,
            Material.POISONOUS_POTATO, Material.PUFFERFISH,
            Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID,
            Material.JIGSAW, Material.SPAWNER, Material.TRIAL_SPAWNER, Material.VAULT,
            Material.END_PORTAL_FRAME, Material.LIGHT, Material.KNOWLEDGE_BOOK, Material.DEBUG_STICK
    );
    private SafeItemPolicy() {}
    public static boolean allowed(Material material) {
        if (material == null || material.isAir() || DENIED.contains(material)) return false;
        String name = material.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") ||
                name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_BOW") ||
                name.endsWith("_CROSSBOW") || name.endsWith("_TRIDENT") || name.equals("MACE") ||
                name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") ||
                name.endsWith("_BOOTS") || name.equals("SHIELD") || name.endsWith("_ARROW")) return false;
        return material.isBlock() || material.isEdible() || switch (material) {
            case PAPER, MAP, FILLED_MAP, NAME_TAG, PLAYER_HEAD, COMPASS, CLOCK, BOOK, WRITABLE_BOOK,
                    WRITTEN_BOOK, PAINTING, ITEM_FRAME, GLOW_ITEM_FRAME, FLOWER_POT -> true;
            default -> false;
        };
    }
}
