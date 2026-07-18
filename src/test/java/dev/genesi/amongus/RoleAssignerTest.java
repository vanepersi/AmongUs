package dev.genesi.amongus;

import dev.genesi.amongus.util.RoleAssigner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAssignerTest {

    @Test
    void impostorCountScalesWithLobby() {
        assertEquals(1, RoleAssigner.impostorCount(4, 5, 3));
        assertEquals(2, RoleAssigner.impostorCount(10, 5, 3));
        assertEquals(3, RoleAssigner.impostorCount(15, 5, 3));
        assertEquals(1, RoleAssigner.impostorCount(2, 5, 3));
    }

    @Test
    void assignMaskHasExactImpostorCount() {
        List<Boolean> mask = RoleAssigner.assignMask(8, 2);
        assertEquals(8, mask.size());
        assertEquals(2, mask.stream().filter(b -> b).count());
        assertTrue(mask.contains(true));
        assertTrue(mask.contains(false));
    }
}
