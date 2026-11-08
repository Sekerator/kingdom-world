package io.github.sekerator.kingdomworld.core.nobility;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;

import java.util.Objects;
import java.util.UUID;

/**
 * A noble family of a kingdom: its name, its head, character and party, its wealth and how loyal it is to the crown. Houses
 * have no bodies; they live in the books of the court.
 */
public final class NobleHouse {
    public static final int MAX_LOYALTY = 100;

    private final long id;
    private UUID kingdomId;
    private final int familySeed;
    private int headSeed;
    private Gender headGender;
    private RulerCharacter character;
    private RulerCharacter.Trait trait;
    private Faction faction;
    private long wealth;
    private int loyalty;
    private int generation;
    private final long founded;
    /** Whether the crown raised the house for a title; such a house goes away with its last title. */
    private boolean raised;

    public NobleHouse(long id, UUID kingdomId, int familySeed, int headSeed, Gender headGender, RulerCharacter character,
                      RulerCharacter.Trait trait, Faction faction, long wealth, int loyalty, int generation, long founded) {
        this.id = id;
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
        this.familySeed = familySeed;
        this.headSeed = headSeed;
        this.headGender = Objects.requireNonNull(headGender, "headGender");
        this.character = Objects.requireNonNull(character, "character");
        this.trait = Objects.requireNonNull(trait, "trait");
        this.faction = Objects.requireNonNull(faction, "faction");
        this.wealth = Math.max(0, wealth);
        this.loyalty = clamp(loyalty);
        this.generation = Math.max(1, generation);
        this.founded = founded;
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(MAX_LOYALTY, value));
    }

    public long id() {
        return id;
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    void moveTo(UUID kingdom) {
        kingdomId = Objects.requireNonNull(kingdom, "kingdom");
    }

    public int familySeed() {
        return familySeed;
    }

    public String family(CitizenNames.Alphabet alphabet) {
        return HouseNames.family(familySeed, alphabet);
    }

    public int headSeed() {
        return headSeed;
    }

    public Gender headGender() {
        return headGender;
    }

    public String head(CitizenNames.Alphabet alphabet) {
        return CitizenNames.render(headSeed, headGender, alphabet);
    }

    public RulerCharacter character() {
        return character;
    }

    public RulerCharacter.Trait trait() {
        return trait;
    }

    public Faction faction() {
        return faction;
    }

    public long wealth() {
        return wealth;
    }

    void addWealth(long amount) {
        wealth = Math.max(0, Math.min(Long.MAX_VALUE / 4, wealth + amount));
    }

    public int loyalty() {
        return loyalty;
    }

    void changeLoyalty(int amount) {
        loyalty = clamp(loyalty + amount);
    }

    void setLoyalty(int value) {
        loyalty = clamp(value);
    }

    public int generation() {
        return generation;
    }

    public long founded() {
        return founded;
    }

    public boolean raised() {
        return raised;
    }

    public void setRaised(boolean value) {
        raised = value;
    }

    /**
     * The head died or was killed: the heir takes the house, with a character of their own.
     */
    void succeed(int seed, Gender gender, RulerCharacter newCharacter, RulerCharacter.Trait newTrait) {
        headSeed = seed;
        headGender = Objects.requireNonNull(gender, "gender");
        character = Objects.requireNonNull(newCharacter, "newCharacter");
        trait = Objects.requireNonNull(newTrait, "newTrait");
        faction = Faction.of(character, trait);
        generation++;
    }
}
