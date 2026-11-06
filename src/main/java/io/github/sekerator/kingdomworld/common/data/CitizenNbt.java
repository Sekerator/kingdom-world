package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRegistry;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NBT form of the population of one kingdom (format 3), stored inside the kingdom's compound.
 *
 * <pre>
 * population: { lastArrival?, citizens: [ citizen ], chronicle: [ death ], sites: [ { kind, pos } ] }
 * citizen: { id, seed, gender, skin, born, health, state, profession, pos, home?, workplace?, food, mood, xp,
 *            workCapacity, workProduct, workProgress }
 * death:   { citizen, seed, gender, profession, cause, killer, time, pos }
 * pos:     { dimension, x, y, z }
 * </pre>
 *
 * <p>Reading is strict like {@link KingdomNbt}: what cannot be understood throws {@link IllegalArgumentException}.
 * Unknown causes of death become {@link CauseOfDeath#OTHER} and unknown profession ids are kept as they are, so
 * that data of a newer version or of a removed addon does not block loading.
 */
public final class CitizenNbt {
    static final String POPULATION = "population";
    private static final String LAST_ARRIVAL = "lastArrival";
    private static final String CITIZENS = "citizens";
    private static final String CHRONICLE = "chronicle";
    private static final String SITES = "sites";
    private static final String KIND = "kind";
    private static final String POS = "pos";
    private static final String ID = "id";
    private static final String SEED = "seed";
    private static final String GENDER = "gender";
    private static final String SKIN = "skin";
    private static final String BORN = "born";
    private static final String HEALTH = "health";
    private static final String STATE = "state";
    private static final String PROFESSION = "profession";
    private static final String HOME = "home";
    private static final String WORKPLACE = "workplace";
    private static final String FOOD = "food";
    private static final String MOOD = "mood";
    private static final String XP = "xp";
    private static final String WORK_CAPACITY = "workCapacity";
    private static final String WORK_PRODUCT = "workProduct";
    private static final String WORK_PROGRESS = "workProgress";
    private static final String CITIZEN = "citizen";
    private static final String CAUSE = "cause";
    private static final String KILLER = "killer";
    private static final String TIME = "time";
    private static final String DIMENSION = "dimension";

    /**
     * What was read for one kingdom, not yet restored.
     */
    public record Population(List<Site> sites, List<Citizen> citizens, long lastArrival, List<DeathRecord> chronicle) {
        public static final Population EMPTY = new Population(List.of(), List.of(), CitizenRegistry.NEVER, List.of());
    }

    private CitizenNbt() {
    }

    // ---------------------------------------------------------------- writing

    public static CompoundTag write(Citizens citizens, UUID kingdomId) {
        CompoundTag tag = new CompoundTag();
        long arrival = citizens.registry().lastArrival(kingdomId);
        if (arrival != CitizenRegistry.NEVER) {
            tag.putLong(LAST_ARRIVAL, arrival);
        }
        ListTag people = new ListTag();
        for (Citizen citizen : citizens.registry().citizensOf(kingdomId)) {
            people.add(writeCitizen(citizen));
        }
        tag.put(CITIZENS, people);
        ListTag chronicle = new ListTag();
        for (DeathRecord record : citizens.registry().chronicle(kingdomId)) {
            chronicle.add(writeDeath(record));
        }
        tag.put(CHRONICLE, chronicle);
        ListTag sites = new ListTag();
        for (Site site : citizens.sites().sitesOf(kingdomId)) {
            CompoundTag entry = new CompoundTag();
            entry.putString(KIND, site.kind());
            entry.put(POS, writePos(site.pos()));
            sites.add(entry);
        }
        tag.put(SITES, sites);
        return tag;
    }

    public static CompoundTag writeCitizen(Citizen citizen) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, citizen.id());
        tag.putInt(SEED, citizen.nameSeed());
        tag.putString(GENDER, citizen.gender().id());
        tag.putInt(SKIN, citizen.skin());
        tag.putLong(BORN, citizen.birthTick());
        tag.putFloat(HEALTH, citizen.health());
        tag.putString(STATE, citizen.state().id());
        tag.putString(PROFESSION, citizen.professionId());
        tag.put(POS, writePos(citizen.lastPos()));
        if (citizen.home() != null) {
            tag.put(HOME, writePos(citizen.home()));
        }
        if (citizen.workplace() != null) {
            tag.put(WORKPLACE, writePos(citizen.workplace()));
        }
        tag.putFloat(FOOD, citizen.food());
        tag.putInt(MOOD, citizen.mood());
        tag.putInt(XP, citizen.experience());
        tag.putInt(WORK_CAPACITY, citizen.workCapacity());
        tag.putString(WORK_PRODUCT, citizen.workProduct());
        tag.putDouble(WORK_PROGRESS, citizen.workProgress());
        return tag;
    }

    private static CompoundTag writeDeath(DeathRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(CITIZEN, record.citizenId());
        tag.putInt(SEED, record.nameSeed());
        tag.putString(GENDER, record.gender().id());
        tag.putString(PROFESSION, record.professionId());
        tag.putString(CAUSE, record.cause().id());
        tag.putString(KILLER, record.killer());
        tag.putLong(TIME, record.gameTime());
        tag.put(POS, writePos(record.place()));
        return tag;
    }

    public static CompoundTag writePos(WorldPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIMENSION, pos.dimension());
        tag.putInt("x", pos.x());
        tag.putInt("y", pos.y());
        tag.putInt("z", pos.z());
        return tag;
    }

    // ---------------------------------------------------------------- reading

    /**
     * Reads the population of a kingdom; an absent tag means an empty population.
     *
     * @throws IllegalArgumentException if the tag cannot be understood
     */
    public static Population read(CompoundTag kingdomTag, UUID kingdomId, String name) {
        if (!kingdomTag.contains(POPULATION)) {
            return Population.EMPTY;
        }
        if (!kingdomTag.contains(POPULATION, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Population of " + name + " is not a compound");
        }
        CompoundTag tag = kingdomTag.getCompound(POPULATION);
        long arrival = tag.contains(LAST_ARRIVAL, Tag.TAG_LONG) ? tag.getLong(LAST_ARRIVAL) : CitizenRegistry.NEVER;
        List<Citizen> citizens = new ArrayList<>();
        for (CompoundTag entry : compounds(tag, CITIZENS, name)) {
            citizens.add(readCitizen(entry, kingdomId, name));
        }
        List<DeathRecord> chronicle = new ArrayList<>();
        for (CompoundTag entry : compounds(tag, CHRONICLE, name)) {
            chronicle.add(readDeath(entry, name));
        }
        List<Site> sites = new ArrayList<>();
        for (CompoundTag entry : compounds(tag, SITES, name)) {
            String kind = entry.getString(KIND);
            if (kind.isEmpty()) {
                throw new IllegalArgumentException("Place of " + name + " without kind");
            }
            sites.add(new Site(readPos(entry, POS, name), kind, kingdomId, null));
        }
        return new Population(sites, citizens, arrival, chronicle);
    }

    public static Citizen readCitizen(CompoundTag tag, UUID kingdomId, String name) {
        if (!tag.hasUUID(ID)) {
            throw new IllegalArgumentException("Citizen of " + name + " without id");
        }
        UUID id = tag.getUUID(ID);
        Gender gender = Gender.byId(tag.getString(GENDER))
                .orElseThrow(() -> new IllegalArgumentException("Citizen " + id + " of " + name + " has an unknown gender"));
        CitizenState state = CitizenState.byId(tag.getString(STATE)).filter(s -> s != CitizenState.DEAD)
                .orElseThrow(() -> new IllegalArgumentException("Citizen " + id + " of " + name + " has an invalid state '"
                        + tag.getString(STATE) + "'"));
        String profession = tag.getString(PROFESSION);
        if (!Profession.isValidId(profession)) {
            throw new IllegalArgumentException("Citizen " + id + " of " + name + " has an invalid profession '" + profession + "'");
        }
        Citizen citizen = new Citizen(id, kingdomId, tag.getInt(SEED), gender, tag.getInt(SKIN), tag.getLong(BORN),
                readPos(tag, POS, name));
        WorldPos home = tag.contains(HOME) ? readPos(tag, HOME, name) : null;
        WorldPos workplace = tag.contains(WORKPLACE) ? readPos(tag, WORKPLACE, name) : null;
        citizen.restore(tag.getFloat(HEALTH), state, profession, home, workplace, tag.getFloat(FOOD), tag.getInt(MOOD),
                tag.getInt(XP), tag.getInt(WORK_CAPACITY), tag.getString(WORK_PRODUCT), tag.getDouble(WORK_PROGRESS));
        return citizen;
    }

    private static DeathRecord readDeath(CompoundTag tag, String name) {
        if (!tag.hasUUID(CITIZEN)) {
            throw new IllegalArgumentException("Chronicle entry of " + name + " without citizen id");
        }
        Gender gender = Gender.byId(tag.getString(GENDER))
                .orElseThrow(() -> new IllegalArgumentException("Chronicle entry of " + name + " has an unknown gender"));
        String profession = tag.getString(PROFESSION);
        if (!Profession.isValidId(profession)) {
            throw new IllegalArgumentException("Chronicle entry of " + name + " has an invalid profession");
        }
        return new DeathRecord(tag.getUUID(CITIZEN), tag.getInt(SEED), gender, profession, CauseOfDeath.byId(tag.getString(CAUSE)),
                tag.getString(KILLER), tag.getLong(TIME), readPos(tag, POS, name));
    }

    public static WorldPos readPos(CompoundTag parent, String key, String name) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Position '" + key + "' in " + name + " is missing or not a compound");
        }
        CompoundTag tag = parent.getCompound(key);
        if (!tag.contains(DIMENSION, Tag.TAG_STRING) || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT)
                || !tag.contains("z", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Position '" + key + "' in " + name + " is incomplete");
        }
        return new WorldPos(tag.getString(DIMENSION), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    private static List<CompoundTag> compounds(CompoundTag tag, String key, String name) {
        if (!tag.contains(key)) {
            return List.of();
        }
        if (!tag.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException(key + " of " + name + " is not a list");
        }
        ListTag raw = (ListTag) tag.get(key);
        if (!raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " of " + name + " does not hold compounds");
        }
        List<CompoundTag> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            result.add(raw.getCompound(i));
        }
        return result;
    }
}
