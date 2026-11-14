package io.github.sekerator.kingdomworld.core.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cause of a death by what dealt the blow: who killed comes first, then the kinds of damage the chronicle always knew, then
 * any other kind of damage by its id, told in words of the chronicle where it has them and by the death message of the game
 * where it has not.
 */
class DeathCauseTest {

    private static DeathCause none(DeathCause.Hurt hurt, String damage) {
        return DeathCause.classify(DeathCause.Attacker.NONE, "", "", hurt, damage);
    }

    @Test
    void whoKilledComesFirst() {
        DeathCause player = DeathCause.classify(DeathCause.Attacker.PLAYER, "Steve", "minecraft:player", DeathCause.Hurt.FALL, "player");
        assertEquals(CauseOfDeath.PLAYER, player.cause());
        assertEquals("Steve", player.killer());
        assertEquals("", player.killerType(), "a player is told by name");

        DeathCause zombie = DeathCause.classify(DeathCause.Attacker.MONSTER, "", "minecraft:zombie", DeathCause.Hurt.OTHER, "mob");
        assertEquals(CauseOfDeath.MONSTER, zombie.cause());
        assertEquals("minecraft:zombie", zombie.killerType());

        DeathCause wolf = DeathCause.classify(DeathCause.Attacker.ANIMAL, "Rex", "minecraft:wolf", DeathCause.Hurt.OTHER, "mob");
        assertEquals(CauseOfDeath.ANIMAL, wolf.cause(), "a wolf is no monster");
        assertEquals("Rex", wolf.killer(), "a creature keeps its own name");

        DeathCause golem = DeathCause.classify(DeathCause.Attacker.GOLEM, "", "minecraft:iron_golem", DeathCause.Hurt.OTHER, "mob");
        assertEquals(CauseOfDeath.GOLEM, golem.cause(), "a golem is no monster");

        DeathCause soldier = DeathCause.classify(DeathCause.Attacker.SOLDIER, "North", "kingdomworld:citizen", DeathCause.Hurt.OTHER, "mob");
        assertEquals(CauseOfDeath.SOLDIER, soldier.cause());
        assertEquals("North", soldier.killer(), "a soldier is told by its kingdom");
        assertEquals(CauseOfDeath.CITIZEN,
                DeathCause.classify(DeathCause.Attacker.CITIZEN, "North", "kingdomworld:citizen", DeathCause.Hurt.OTHER, "mob").cause());
        assertEquals(CauseOfDeath.ROBBERS,
                DeathCause.classify(DeathCause.Attacker.ROBBER, "", "kingdomworld:bandit", DeathCause.Hurt.OTHER, "mob").cause());
        assertEquals(CauseOfDeath.CREATURE,
                DeathCause.classify(DeathCause.Attacker.CREATURE, "", "othermod:ent", DeathCause.Hurt.OTHER, "mob").cause());
        // A burning arrow of a skeleton is the doing of the skeleton, not of the fire.
        assertEquals(CauseOfDeath.MONSTER,
                DeathCause.classify(DeathCause.Attacker.MONSTER, "", "minecraft:skeleton", DeathCause.Hurt.FIRE, "arrow").cause());
    }

    @Test
    void withoutAnAttackerTheKindOfDamageTells() {
        assertEquals(CauseOfDeath.STARVATION, none(DeathCause.Hurt.STARVATION, "starve").cause());
        assertEquals(CauseOfDeath.LAVA, none(DeathCause.Hurt.LAVA, "lava").cause());
        assertEquals(CauseOfDeath.FIRE, none(DeathCause.Hurt.FIRE, "hotFloor").cause());
        assertEquals(CauseOfDeath.FALL, none(DeathCause.Hurt.FALL, "fall").cause());
        assertEquals(CauseOfDeath.DROWNING, none(DeathCause.Hurt.DROWNING, "drown").cause());
        for (DeathCause known : new DeathCause[]{none(DeathCause.Hurt.FIRE, "hotFloor"), none(DeathCause.Hurt.FALL, "fall")}) {
            assertEquals(DeathCause.Telling.CAUSE, known.telling(), "the causes the chronicle always knew keep their words");
        }

        DeathCause suffocated = none(DeathCause.Hurt.OTHER, "inWall");
        assertEquals(CauseOfDeath.OTHER, suffocated.cause());
        assertEquals("inWall", suffocated.damage());
        assertEquals(DeathCause.Telling.WORD, suffocated.telling());
        assertEquals("suffocation", DeathCause.word("inWall"));
        // Kinds of damage of the game that read the same.
        assertEquals("explosion", DeathCause.word("explosion.player"));
        assertEquals("magic", DeathCause.word("indirectMagic"));
        assertEquals("wither", DeathCause.word("witherSkull"));
        for (String damage : new String[]{"cactus", "sweetBerryBush", "freeze", "lightningBolt", "fallingBlock", "outOfWorld"}) {
            assertEquals(DeathCause.Telling.WORD, none(DeathCause.Hurt.OTHER, damage).telling(), damage);
        }

        DeathCause modded = none(DeathCause.Hurt.OTHER, "somemod.acid");
        assertEquals(DeathCause.Telling.GAME, modded.telling(), "damage of other mods is told by the death message of the game");
        assertEquals(DeathCause.Telling.CAUSE, none(DeathCause.Hurt.OTHER, "generic").telling(), "plain damage just died");
        assertEquals(DeathCause.Telling.CAUSE, none(DeathCause.Hurt.OTHER, "").telling(), "an unknown kind just died");
        assertTrue(DeathCause.words().contains("suffocation"));
    }

    @Test
    void longNamesAndIdsAreCut() {
        DeathCause cause = new DeathCause(CauseOfDeath.MONSTER, "n".repeat(500), "t".repeat(500), null);
        assertEquals(DeathCause.MAX_NAME, cause.killer().length());
        assertEquals(DeathCause.MAX_ID, cause.killerType().length());
        assertEquals("", cause.damage());
    }

    @Test
    void theChronicleKeepsTheWholeCause() {
        Citizens citizens = new Citizens();
        java.util.UUID realm = java.util.UUID.randomUUID();
        Citizen anna = new Citizen(java.util.UUID.randomUUID(), realm, 42, Gender.FEMALE, 1, 0L, new WorldPos("minecraft:overworld", 0, 64, 0));
        citizens.add(anna);
        DeathCause cause = new DeathCause(CauseOfDeath.OTHER, "", "", "inWall");
        DeathRecord record = citizens.die(anna.id(), cause, 48_000L, null).orElseThrow();
        assertEquals(cause, record.deathCause());
        assertEquals(record, citizens.registry().chronicle(realm).get(0));
        // The work far from players gives its causes as before.
        DeathRecord old = new DeathRecord(anna.id(), 42, Gender.FEMALE, "miner", CauseOfDeath.MINE, "", 0L, anna.lastPos());
        assertEquals("", old.killerType());
        assertEquals("", old.damage());
    }
}
