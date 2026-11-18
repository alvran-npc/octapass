package net.octa.octapass.reward;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeItemPolicyTest {
    @Test
    void permitsOrdinaryBuildingFoodAndCosmeticItems() {
        assertTrue(SafeItemPolicy.allowed("OAK_PLANKS", true, false));
        assertTrue(SafeItemPolicy.allowed("BREAD", false, true));
        assertTrue(SafeItemPolicy.allowed("PAINTING", false, false));
    }

    @Test
    void rejectsEquipmentExplosivesAndAdministrativeBlocks() {
        assertFalse(SafeItemPolicy.allowed("DIAMOND_SWORD", false, false));
        assertFalse(SafeItemPolicy.allowed("NETHERITE_CHESTPLATE", false, false));
        assertFalse(SafeItemPolicy.allowed("TNT", true, false));
        assertFalse(SafeItemPolicy.allowed("COMMAND_BLOCK", true, false));
        assertFalse(SafeItemPolicy.allowed("SPAWNER", true, false));
    }

    @Test
    void rejectsAirAndNullNames() {
        assertFalse(SafeItemPolicy.allowed("AIR", true, false));
        assertFalse(SafeItemPolicy.allowed("CAVE_AIR", true, false));
        assertFalse(SafeItemPolicy.allowed(null, false, false));
    }
}
