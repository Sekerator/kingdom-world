package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.DeathCause;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The chronicle keeps who killed and the kind of damage; entries saved before they were kept read as they were.
 */
class ChronicleNbtTest {
    private static final WorldPos HOME = new WorldPos("minecraft:overworld", 10, 64, -5);

    private static CompoundTag kingdomTag(Citizens citizens, UUID realm) {
        CompoundTag kingdom = new CompoundTag();
        kingdom.put(CitizenNbt.POPULATION, CitizenNbt.write(citizens, realm));
        return kingdom;
    }

    @Test
    void theWholeCauseIsSavedAndRead() {
        Citizens citizens = new Citizens();
        UUID realm = UUID.randomUUID();
        Citizen anna = new Citizen(UUID.randomUUID(), realm, 7, Gender.FEMALE, 0, 0L, HOME);
        Citizen boris = new Citizen(UUID.randomUUID(), realm, 8, Gender.MALE, 0, 0L, HOME);
        citizens.add(anna);
        citizens.add(boris);
        citizens.die(anna.id(), new DeathCause(CauseOfDeath.ANIMAL, "Rex", "minecraft:wolf", "mob"), 100L, HOME);
        citizens.die(boris.id(), new DeathCause(CauseOfDeath.OTHER, "", "", "inWall"), 200L, HOME);

        List<DeathRecord> read = CitizenNbt.read(kingdomTag(citizens, realm), realm, "test").chronicle();

        assertEquals(citizens.registry().chronicle(realm), read);
        assertEquals("inWall", read.get(0).damage());
        assertEquals("minecraft:wolf", read.get(1).killerType());
        assertEquals("Rex", read.get(1).killer());
    }

    @Test
    void entriesOfOlderVersionsReadAsTheyWere() {
        Citizens citizens = new Citizens();
        UUID realm = UUID.randomUUID();
        Citizen anna = new Citizen(UUID.randomUUID(), realm, 7, Gender.FEMALE, 0, 0L, HOME);
        citizens.add(anna);
        citizens.die(anna.id(), CauseOfDeath.MONSTER, "", 100L, HOME);
        CompoundTag kingdom = kingdomTag(citizens, realm);
        ListTag chronicle = kingdom.getCompound(CitizenNbt.POPULATION).getList("chronicle", 10);
        CompoundTag entry = chronicle.getCompound(0);
        assertFalse(entry.contains("killerType"), "nothing new is written for a cause without a creature");
        assertFalse(entry.contains("damage"));
        // An entry of an older version, with a cause this version does not know either.
        entry.putString("cause", "eaten_by_grue");

        DeathRecord read = CitizenNbt.read(kingdom, realm, "test").chronicle().get(0);

        assertEquals(CauseOfDeath.OTHER, read.cause());
        assertEquals("", read.killerType());
        assertEquals("", read.damage());
        assertEquals(DeathCause.Telling.CAUSE, read.deathCause().telling(), "an old entry just died");
        assertEquals(HOME, read.place());
    }
}
