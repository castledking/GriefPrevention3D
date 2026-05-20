package com.griefprevention.compat;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MaterialTagCompatTest {

    @Test
    void values_returnsEmptySetForMissingTags() {
        Set<Material> materials = MaterialTagCompat.values("NOT_A_REAL_TAG");

        assertNotNull(materials);
        assertEquals(0, materials.size());
    }

    @Test
    void values_returnsEmptySetForBlankTags() {
        Set<Material> materials = MaterialTagCompat.values(" ");

        assertNotNull(materials);
        assertEquals(0, materials.size());
    }
}
