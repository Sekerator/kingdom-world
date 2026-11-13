package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the "Citizens" tab of the throne screen shows to a member of the kingdom: the summary, the citizens and the
 * chronicle of deaths. Names travel as seeds; the client builds the name in the player's alphabet.
 *
 * @param canManage  whether the viewer may give professions
 * @param population living citizens; the list may be shorter than this
 * @param foodDays   whole days the storehouses feed everybody, {@link #UNLIMITED_FOOD} if citizens need no food
 * @param growth     why newcomers do or do not arrive now
 */
public record CitizenView(UUID kingdomId, boolean canManage, int population, int bedsUsed, int bedsTotal, int foodDays,
                          int mood, int storehouses, CitizenNeeds.GrowthCheck growth, List<Entry> citizens,
                          List<Death> chronicle, List<Production> production, List<RulerInfo.Decree> decrees, List<SettlementRow> settlements) {
    public static final int MAX_CITIZENS = 500;
    /** Settlements of a state of the world the tab lists at most. */
    public static final int MAX_SETTLEMENTS = 256;

    public CitizenView(UUID kingdomId, boolean canManage, int population, int bedsUsed, int bedsTotal, int foodDays, int mood, int storehouses,
                       CitizenNeeds.GrowthCheck growth, List<Entry> citizens, List<Death> chronicle, List<Production> production,
                       List<RulerInfo.Decree> decrees) {
        this(kingdomId, canManage, population, bedsUsed, bedsTotal, foodDays, mood, storehouses, growth, citizens, chronicle, production, decrees,
                List.of());
    }

    /**
     * A settlement of a state of the world that lives as numbers: its names, rank and numbers.
     *
     * @param food days of food in store
     * @param data whether it is not raised in the world yet
     */
    public record SettlementRow(String latin, String cyrillic, io.github.sekerator.kingdomworld.core.land.Tier tier, int population, int housing,
                                int food, int guards, int militia, boolean data, int named) {
        public SettlementRow {
            Objects.requireNonNull(latin, "latin");
            Objects.requireNonNull(cyrillic, "cyrillic");
            Objects.requireNonNull(tier, "tier");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(latin, MAX_SETTLEMENT_NAME);
            buf.writeUtf(cyrillic, MAX_SETTLEMENT_NAME);
            buf.writeEnum(tier);
            buf.writeVarInt(population);
            buf.writeVarInt(housing);
            buf.writeVarInt(food);
            buf.writeVarInt(guards);
            buf.writeVarInt(militia);
            buf.writeBoolean(data);
            buf.writeVarInt(named);
        }

        static SettlementRow decode(FriendlyByteBuf buf) {
            return new SettlementRow(buf.readUtf(MAX_SETTLEMENT_NAME), buf.readUtf(MAX_SETTLEMENT_NAME),
                    KingdomView.readEnum(buf, io.github.sekerator.kingdomworld.core.land.Tier.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
        }
    }

    private static final int MAX_SETTLEMENT_NAME = 64;
    public static final int MAX_PRODUCTION = 32;
    public static final int MAX_CHRONICLE = 50;
    /** Decrees of a ruler of the world the chronicle shows at most. */
    public static final int MAX_DECREES = 32;
    public static final int UNLIMITED_FOOD = -1;
    /** Food days are shown up to this number. */
    public static final int MAX_FOOD_DAYS = 9_999;
    private static final int MAX_KILLER = 64;
    private static final int MAX_ITEM_ID = 256;

    /**
     * One citizen.
     *
     * @param home      where its bed is, or {@code null} if it has none
     * @param workplace where it works, or {@code null} if it has no workplace
     * @param work      what its work looks like, and if it stands still, why
     */
    public record Entry(UUID id, int seed, boolean female, String profession, CitizenState state, @Nullable BlockPos home,
                        int health, int mood, @Nullable BlockPos workplace, WorkStatus work) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(work, "work");
        }
    }

    /**
     * What a profession produced today and the day before.
     *
     * @param top item id of what it produced most of today (or yesterday), empty if nothing
     */
    public record Production(String profession, long today, long yesterday, String top) {
        public Production {
            Objects.requireNonNull(profession, "profession");
            top = top == null ? "" : top;
        }
    }

    /**
     * One entry of the chronicle.
     *
     * @param day   game day of the death, counted from 1
     * @param place where the citizen died
     */
    public record Death(int seed, boolean female, String profession, CauseOfDeath cause, String killer, long day,
                        BlockPos place) {
        public Death {
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(place, "place");
            killer = killer == null ? "" : killer;
        }
    }

    public CitizenView {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(growth, "growth");
        citizens = List.copyOf(citizens);
        chronicle = List.copyOf(chronicle);
        production = List.copyOf(production);
        decrees = List.copyOf(decrees);
        settlements = List.copyOf(settlements);
        if (citizens.size() > MAX_CITIZENS || chronicle.size() > MAX_CHRONICLE || production.size() > MAX_PRODUCTION
                || decrees.size() > MAX_DECREES) {
            throw new IllegalArgumentException("Citizen view too large");
        }
    }

    public CitizenView(UUID kingdomId, boolean canManage, int population, int bedsUsed, int bedsTotal, int foodDays, int mood, int storehouses,
                       CitizenNeeds.GrowthCheck growth, List<Entry> citizens, List<Death> chronicle, List<Production> production) {
        this(kingdomId, canManage, population, bedsUsed, bedsTotal, foodDays, mood, storehouses, growth, citizens, chronicle, production,
                List.of());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(kingdomId);
        buf.writeBoolean(canManage);
        buf.writeVarInt(population);
        buf.writeVarInt(bedsUsed);
        buf.writeVarInt(bedsTotal);
        buf.writeVarInt(foodDays + 1);
        buf.writeVarInt(mood);
        buf.writeVarInt(storehouses);
        buf.writeEnum(growth);
        buf.writeVarInt(citizens.size());
        for (Entry entry : citizens) {
            buf.writeUUID(entry.id());
            buf.writeInt(entry.seed());
            buf.writeBoolean(entry.female());
            buf.writeUtf(entry.profession(), Profession.MAX_ID_LENGTH);
            buf.writeEnum(entry.state());
            buf.writeBoolean(entry.home() != null);
            if (entry.home() != null) {
                buf.writeBlockPos(entry.home());
            }
            buf.writeVarInt(entry.health());
            buf.writeVarInt(entry.mood());
            buf.writeBoolean(entry.workplace() != null);
            if (entry.workplace() != null) {
                buf.writeBlockPos(entry.workplace());
            }
            buf.writeEnum(entry.work());
        }
        buf.writeVarInt(chronicle.size());
        for (Death death : chronicle) {
            buf.writeInt(death.seed());
            buf.writeBoolean(death.female());
            buf.writeUtf(death.profession(), Profession.MAX_ID_LENGTH);
            buf.writeEnum(death.cause());
            buf.writeUtf(death.killer(), MAX_KILLER);
            buf.writeVarLong(death.day());
            buf.writeBlockPos(death.place());
        }
        buf.writeVarInt(production.size());
        for (Production entry : production) {
            buf.writeUtf(entry.profession(), Profession.MAX_ID_LENGTH);
            buf.writeVarLong(entry.today());
            buf.writeVarLong(entry.yesterday());
            buf.writeUtf(entry.top(), MAX_ITEM_ID);
        }
        buf.writeVarInt(decrees.size());
        for (RulerInfo.Decree decree : decrees) {
            RulerInfo.writeDecree(buf, decree);
        }
        int shown = Math.min(MAX_SETTLEMENTS, settlements.size());
        buf.writeVarInt(shown);
        for (int i = 0; i < shown; i++) {
            settlements.get(i).encode(buf);
        }
    }

    public static CitizenView decode(FriendlyByteBuf buf) {
        UUID kingdomId = buf.readUUID();
        boolean canManage = buf.readBoolean();
        int population = buf.readVarInt();
        int bedsUsed = buf.readVarInt();
        int bedsTotal = buf.readVarInt();
        int foodDays = buf.readVarInt() - 1;
        int mood = buf.readVarInt();
        int storehouses = buf.readVarInt();
        CitizenNeeds.GrowthCheck growth = KingdomView.readEnum(buf, CitizenNeeds.GrowthCheck.class);
        int size = KingdomView.readSize(buf, MAX_CITIZENS);
        List<Entry> citizens = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            int seed = buf.readInt();
            boolean female = buf.readBoolean();
            String profession = readProfession(buf);
            CitizenState state = KingdomView.readEnum(buf, CitizenState.class);
            BlockPos home = buf.readBoolean() ? buf.readBlockPos() : null;
            int citizenHealth = buf.readVarInt();
            int citizenMood = buf.readVarInt();
            BlockPos workplace = buf.readBoolean() ? buf.readBlockPos() : null;
            citizens.add(new Entry(id, seed, female, profession, state, home, citizenHealth, citizenMood, workplace,
                    KingdomView.readEnum(buf, WorkStatus.class)));
        }
        int deaths = KingdomView.readSize(buf, MAX_CHRONICLE);
        List<Death> chronicle = new ArrayList<>(deaths);
        for (int i = 0; i < deaths; i++) {
            chronicle.add(new Death(buf.readInt(), buf.readBoolean(), readProfession(buf), KingdomView.readEnum(buf, CauseOfDeath.class),
                    buf.readUtf(MAX_KILLER), buf.readVarLong(), buf.readBlockPos()));
        }
        int produced = KingdomView.readSize(buf, MAX_PRODUCTION);
        List<Production> production = new ArrayList<>(produced);
        for (int i = 0; i < produced; i++) {
            production.add(new Production(readProfession(buf), buf.readVarLong(), buf.readVarLong(), buf.readUtf(MAX_ITEM_ID)));
        }
        int decreeCount = KingdomView.readSize(buf, MAX_DECREES);
        List<RulerInfo.Decree> decrees = new ArrayList<>(decreeCount);
        for (int i = 0; i < decreeCount; i++) {
            decrees.add(RulerInfo.readDecree(buf));
        }
        int settlementCount = KingdomView.readSize(buf, MAX_SETTLEMENTS);
        List<SettlementRow> settlements = new ArrayList<>(settlementCount);
        for (int i = 0; i < settlementCount; i++) {
            settlements.add(SettlementRow.decode(buf));
        }
        return new CitizenView(kingdomId, canManage, population, bedsUsed, bedsTotal, foodDays, mood, storehouses, growth,
                citizens, chronicle, production, decrees, settlements);
    }

    static String readProfession(FriendlyByteBuf buf) {
        String profession = buf.readUtf(Profession.MAX_ID_LENGTH);
        if (!Profession.isValidId(profession)) {
            throw new DecoderException("Invalid profession id");
        }
        return profession;
    }
}
