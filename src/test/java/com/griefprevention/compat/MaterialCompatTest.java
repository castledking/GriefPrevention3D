package com.griefprevention.compat;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialCompatTest {

    @Test
    void get_resolvesExistingMaterialsByName() {
        assertEquals(Material.AIR, MaterialCompat.get("AIR"));
        assertEquals(Material.AIR, MaterialCompat.get(" air "));
    }

    @Test
    void get_returnsNullForMissingMaterials() {
        assertEquals(null, MaterialCompat.get("NOT_A_REAL_MATERIAL"));
        assertEquals(null, MaterialCompat.get(null));
    }

    @Test
    void availableSet_skipsMissingMaterials() {
        Set<Material> materials = MaterialCompat.availableSet("AIR", "NOT_A_REAL_MATERIAL");

        assertTrue(materials.contains(Material.AIR));
        assertEquals(1, materials.size());
    }
}
