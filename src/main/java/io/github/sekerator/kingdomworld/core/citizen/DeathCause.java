package io.github.sekerator.kingdomworld.core.citizen;

import java.util.Map;
import java.util.Objects;

/**
 * Why somebody died, as precisely as the chronicle tells it: the kind of cause, who killed (a player by name, a creature by its
 * kind and its own name if it has one, a citizen or a soldier by its kingdom) and the kind of damage that killed.
 *
 * @param killer     the name of who killed: a player, a creature with a name of its own, the kingdom of a citizen or soldier;
 *                   empty if nobody or unknown
 * @param killerType the id of the kind of creature that killed, for example {@code minecraft:zombie}; empty if none
 * @param damage     the id of the kind of damage that killed (the {@code msgId} of the game, {@code inWall} for example); empty if
 *                   unknown
 */
public record DeathCause(CauseOfDeath cause, String killer, String killerType, String damage) {
    /** Longest kind of creature or damage kept. */
    public static final int MAX_ID = 128;
    /** Longest name of a killer kept. */
    public static final int MAX_NAME = 64;

    public DeathCause {
        Objects.requireNonNull(cause, "cause");
        killer = clip(killer, MAX_NAME);
        killerType = clip(killerType, MAX_ID);
        damage = clip(damage, MAX_ID);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    /**
     * A cause without a creature or a kind of damage, as the work far from players and older chronicles give it.
     */
    public static DeathCause of(CauseOfDeath cause, String killer) {
        return new DeathCause(cause, killer, "", "");
    }

    public static DeathCause of(CauseOfDeath cause) {
        return of(cause, "");
    }

    /**
     * Who dealt the blow that killed, as far as it matters to the chronicle.
     */
    public enum Attacker {
        NONE,
        PLAYER,
        /** A citizen of a kingdom who is no soldier. */
        CITIZEN,
        SOLDIER,
        /** A robber of the roads. */
        ROBBER,
        MONSTER,
        GOLEM,
        ANIMAL,
        /** Any other creature. */
        CREATURE
    }

    /**
     * What the damage was, for a death without an attacker.
     */
    public enum Hurt {
        STARVATION,
        LAVA,
        FIRE,
        FALL,
        DROWNING,
        OTHER
    }

    /**
     * The cause of a death: an attacker first, then the kinds of damage the chronicle always knew, then any other kind of damage by
     * its id.
     *
     * @param name the name of the attacker: a player's name, a creature's own name, the name of the kingdom of a citizen or soldier
     * @param type the id of the attacker's kind of creature
     */
    public static DeathCause classify(Attacker attacker, String name, String type, Hurt hurt, String damage) {
        return switch (attacker) {
            case PLAYER -> new DeathCause(CauseOfDeath.PLAYER, name, "", damage);
            case CITIZEN -> new DeathCause(CauseOfDeath.CITIZEN, name, type, damage);
            case SOLDIER -> new DeathCause(CauseOfDeath.SOLDIER, name, type, damage);
            case ROBBER -> new DeathCause(CauseOfDeath.ROBBERS, name, type, damage);
            case MONSTER -> new DeathCause(CauseOfDeath.MONSTER, name, type, damage);
            case GOLEM -> new DeathCause(CauseOfDeath.GOLEM, name, type, damage);
            case ANIMAL -> new DeathCause(CauseOfDeath.ANIMAL, name, type, damage);
            case CREATURE -> new DeathCause(CauseOfDeath.CREATURE, name, type, damage);
            case NONE -> new DeathCause(switch (hurt) {
                case STARVATION -> CauseOfDeath.STARVATION;
                case LAVA -> CauseOfDeath.LAVA;
                case FIRE -> CauseOfDeath.FIRE;
                case FALL -> CauseOfDeath.FALL;
                case DROWNING -> CauseOfDeath.DROWNING;
                case OTHER -> CauseOfDeath.OTHER;
            }, "", "", damage);
        };
    }

    /**
     * Kinds of damage of the game the chronicle tells in words of its own, by the word they are told with. Every other kind is
     * told by the death message of the game for it, so that the damage of other mods reads right too.
     */
    private static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("inWall", "suffocation"),
            Map.entry("cramming", "cramming"),
            Map.entry("explosion", "explosion"),
            Map.entry("explosion.player", "explosion"),
            Map.entry("badRespawnPoint", "explosion"),
            Map.entry("fireworks", "fireworks"),
            Map.entry("cactus", "cactus"),
            Map.entry("sweetBerryBush", "berry_bush"),
            Map.entry("magic", "magic"),
            Map.entry("indirectMagic", "magic"),
            Map.entry("wither", "wither"),
            Map.entry("witherSkull", "wither"),
            Map.entry("freeze", "freeze"),
            Map.entry("lightningBolt", "lightning"),
            Map.entry("fallingBlock", "falling_block"),
            Map.entry("anvil", "anvil"),
            Map.entry("fallingStalactite", "stalactite"),
            Map.entry("stalagmite", "stalagmite"),
            Map.entry("outOfWorld", "void"),
            Map.entry("outsideBorder", "border"),
            Map.entry("dragonBreath", "dragon_breath"),
            Map.entry("flyIntoWall", "wall"),
            Map.entry("thorns", "thorns"),
            Map.entry("sonic_boom", "sonic_boom"),
            Map.entry("arrow", "arrow"),
            Map.entry("trident", "trident"),
            Map.entry("thrown", "thrown"),
            Map.entry("dryout", "dry_out"));

    /** Kinds of damage that say no more than "died". */
    private static final java.util.Set<String> PLAIN = java.util.Set.of("generic", "genericKill");

    /**
     * The words of the chronicle a kind of damage is told with, empty if it has none of its own.
     */
    public static String word(String damage) {
        return damage == null ? "" : WORDS.getOrDefault(damage, "");
    }

    /**
     * Every word of the chronicle for a kind of damage, for the check of the translations.
     */
    public static java.util.Collection<String> words() {
        return new java.util.TreeSet<>(WORDS.values());
    }

    /**
     * How the chronicle tells a death without an attacker by the kind of damage: in words of its own, by the death message of the
     * game, or just as "died".
     */
    public enum Telling {
        CAUSE,
        WORD,
        GAME
    }

    /**
     * How this death is told.
     */
    public Telling telling() {
        if (cause != CauseOfDeath.OTHER || damage.isEmpty() || PLAIN.contains(damage)) {
            return Telling.CAUSE;
        }
        return word(damage).isEmpty() ? Telling.GAME : Telling.WORD;
    }
}
