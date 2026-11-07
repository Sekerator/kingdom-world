package io.github.sekerator.kingdomworld.core.membership;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionsTest {

    @Test
    void kingHoldsEveryRight() {
        RolePermissions table = new RolePermissions();
        assertEquals(EnumSet.allOf(Permission.class), table.effective(Role.KING));
    }

    @Test
    void appointingBelongsToTheKingAlone() {
        RolePermissions table = RolePermissions.defaults();
        for (Role role : Role.values()) {
            assertEquals(role == Role.KING, table.has(role, Permission.APPOINT), role.toString());
            assertFalse(RolePermissions.isEditable(role, Permission.APPOINT));
        }
        assertThrows(IllegalArgumentException.class, () -> table.set(Role.MARSHAL, Permission.APPOINT, true));
        assertThrows(IllegalArgumentException.class, () -> table.set(Role.KING, Permission.BUILD, false));
    }

    @Test
    void defaultsAreSensible() {
        RolePermissions table = RolePermissions.defaults();
        assertEquals(EnumSet.of(Permission.BUILD, Permission.INTERACT), table.effective(Role.SUBJECT));
        assertTrue(table.has(Role.MARSHAL, Permission.RECRUIT));
        assertTrue(table.has(Role.MARSHAL, Permission.TERRITORY));
        assertTrue(table.has(Role.MARSHAL, Permission.MANAGE_ARMY));
        assertFalse(table.has(Role.STEWARD, Permission.MANAGE_ARMY));
        assertTrue(table.has(Role.STEWARD, Permission.TERRITORY));
        assertTrue(table.has(Role.STEWARD, Permission.MANAGE_CITIZENS));
        assertFalse(table.has(Role.MARSHAL, Permission.MANAGE_CITIZENS));
        assertTrue(table.has(Role.KING, Permission.MANAGE_CITIZENS));
        assertFalse(table.has(Role.TREASURER, Permission.TERRITORY));
        assertTrue(table.has(Role.CHANCELLOR, Permission.RECRUIT));
        for (Role role : Role.values()) {
            if (role != Role.SUBJECT) {
                assertTrue(table.has(role, Permission.CONTAINERS), role.toString());
            }
        }
    }

    @Test
    void rightsCanBeChangedAndCopiesAreIndependent() {
        RolePermissions table = RolePermissions.defaults();
        RolePermissions copy = table.copy();
        assertEquals(table, copy);

        assertTrue(table.set(Role.SUBJECT, Permission.CONTAINERS, true));
        assertFalse(table.set(Role.SUBJECT, Permission.CONTAINERS, true), "unchanged value reported as a change");
        assertTrue(table.has(Role.SUBJECT, Permission.CONTAINERS));
        assertFalse(copy.has(Role.SUBJECT, Permission.CONTAINERS));
        assertNotEquals(table, copy);

        assertTrue(table.set(Role.MARSHAL, Permission.BUILD, false));
        assertFalse(table.has(Role.MARSHAL, Permission.BUILD));
    }

    @Test
    void roleCycleSkipsTheCrown() {
        assertEquals(Role.MARSHAL, Role.SUBJECT.nextAssignable());
        assertEquals(Role.TREASURER, Role.MARSHAL.nextAssignable());
        for (Role role : Role.values()) {
            assertNotEquals(Role.KING, role.nextAssignable());
            assertEquals(role, Role.byId(role.id()).orElseThrow());
        }
        assertTrue(Role.byId("emperor").isEmpty());
        assertEquals(Permission.TERRITORY, Permission.byId("territory").orElseThrow());
    }
}
