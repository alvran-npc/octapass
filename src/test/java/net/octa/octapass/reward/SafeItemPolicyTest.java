package net.octa.octapass.reward;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeItemPolicyTest {
    @Test
    void permitsOrdinaryBuildingFoodAndCosmeticItems() {
        assertTrue(SafeItemPolicy.allowed(Material.OAK_PLANKS));
        assertTrue(SafeItemPolicy.allowed(Material.BREAD));
        assertTrue(SafeItemPolicy.allowed(Material.PAINTING));
    }

    @Test
    void rejectsEquipmentExplosivesAndAdministrativeBlocks() {
        assertFalse(SafeItemPolicy.allowed(Material.DIAMOND_SWORD));
        assertFalse(SafeItemPolicy.allowed(Material.NETHERITE_CHESTPLATE));
        assertFalse(SafeItemPolicy.allowed(Material.TNT));
        assertFalse(SafeItemPolicy.allowed(Material.COMMAND_BLOCK));
        assertFalse(SafeItemPolicy.allowed(Material.SPAWNER));
    }
}
