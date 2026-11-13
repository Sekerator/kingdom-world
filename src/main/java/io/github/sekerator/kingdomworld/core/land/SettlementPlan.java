package io.github.sekerator.kingdomworld.core.land;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The settlements of all states of a world: their ranks, places and the settlements they belong to. Immutable.
 */
public final class SettlementPlan {
    private final ProvincePlan provinces;
    private final SettlementRules rules;
    private final List<SettlementSlot> slots;
    private final int[][] wanted;
    private final int[] seats;

    SettlementPlan(ProvincePlan provinces, SettlementRules rules, List<SettlementSlot> slots, int[][] wanted) {
        this.provinces = provinces;
        this.rules = rules;
        this.slots = List.copyOf(slots);
        this.wanted = new int[wanted.length][];
        for (int s = 0; s < wanted.length; s++) {
            this.wanted[s] = wanted[s].clone();
        }
        this.seats = new int[provinces.provinces().size() + 1];
        Arrays.fill(seats, -1);
        for (SettlementSlot slot : this.slots) {
            int seat = seats[slot.province()];
            if (seat < 0 || slot.tier().above(this.slots.get(seat).tier())) {
                seats[slot.province()] = slot.index();
            }
        }
        seats[0] = -1;
    }

    public SurveyRaster raster() {
        return provinces.raster();
    }

    public ProvincePlan provinces() {
        return provinces;
    }

    public SettlementRules rules() {
        return rules;
    }

    /**
     * All settlements, by their numbers: every state's capital first, then its cities, towns, villages and hamlets.
     */
    public List<SettlementSlot> slots() {
        return slots;
    }

    public SettlementSlot slot(int index) {
        return slots.get(index);
    }

    public List<SettlementSlot> slotsOf(int state) {
        List<SettlementSlot> list = new ArrayList<>();
        for (SettlementSlot slot : slots) {
            if (slot.state() == state) {
                list.add(slot);
            }
        }
        return list;
    }

    /**
     * Settlements of a rank a state has.
     */
    public int count(int state, Tier tier) {
        int count = 0;
        for (SettlementSlot slot : slots) {
            if (slot.state() == state && slot.tier() == tier) {
                count++;
            }
        }
        return count;
    }

    /**
     * Settlements of a rank the seed of the world gave a state; fewer stand when the land has no room for them.
     */
    public int wanted(int state, Tier tier) {
        return wanted[state][tier.ordinal()];
    }

    /**
     * The plan with the places the world chose: for every settlement -1 leaves it out (the settlements that belonged to it go to
     * the nearest one above it that stays), 0 keeps its place and {@code k} takes its {@code k}-th alternate. The settlements are
     * numbered anew in the same order.
     *
     * @throws IllegalArgumentException if a choice is not one of the places of its settlement
     */
    /** Places tried near the place of a settlement when it and its alternates are turned down: two rings of eight around it. */
    public static final int NUDGES = 16;
    /** Blocks between the rings of the places tried near a place. */
    public static final int NUDGE_STEP = 48;

    /**
     * How many places a settlement may take: its own, its alternates and the places near its own.
     */
    public int choices(int index) {
        return 1 + slots.get(index).alternates().size() + NUDGES;
    }

    /**
     * The place of a settlement for a choice: 0 its own, 1 to the number of its alternates an alternate, then the places near
     * its own in two rings of eight; null for a place near that is off the survey, on land no settlement stands on, or in
     * another province.
     */
    public SettlementSlot.Spot spot(int index, int choice) {
        SettlementSlot slot = slots.get(index);
        if (choice == 0) {
            return new SettlementSlot.Spot(slot.x(), slot.z(), slot.cell(), slot.province());
        }
        int alternates = slot.alternates().size();
        if (choice <= alternates) {
            return slot.alternates().get(choice - 1);
        }
        int k = choice - alternates - 1;
        if (k >= NUDGES || choice < 0) {
            throw new IllegalArgumentException("No place " + choice + " for settlement " + index);
        }
        double angle = (k % 8) * Math.PI / 4 + (k >= 8 ? Math.PI / 8 : 0);
        int distance = NUDGE_STEP * (1 + k / 8);
        int x = slot.x() + (int) Math.round(Math.cos(angle) * distance);
        int z = slot.z() + (int) Math.round(Math.sin(angle) * distance);
        SurveyRaster raster = raster();
        int cell = raster.cellAt(x, z);
        if (cell < 0 || !raster.terrain(cell).passable() || provinces.provinceAt(cell) != slot.province()) {
            return null;
        }
        return new SettlementSlot.Spot(x, z, cell, slot.province());
    }

    public SettlementPlan placed(int[] choices) {
        if (choices.length != slots.size()) {
            throw new IllegalArgumentException("A choice for every settlement");
        }
        int[] number = new int[slots.size()];
        int kept = 0;
        for (int i = 0; i < slots.size(); i++) {
            int choice = choices[i];
            if (choice >= choices(i) || choice >= 0 && spot(i, choice) == null) {
                throw new IllegalArgumentException("No place " + choice + " for settlement " + i);
            }
            number[i] = choice < 0 ? -1 : kept++;
        }
        List<SettlementSlot> placed = new ArrayList<>(kept);
        for (int i = 0; i < slots.size(); i++) {
            if (number[i] < 0) {
                continue;
            }
            SettlementSlot slot = slots.get(i);
            SettlementSlot.Spot spot = spot(i, choices[i]);
            int parent = slot.parent();
            while (parent >= 0 && number[parent] < 0) {
                parent = slots.get(parent).parent();
            }
            placed.add(new SettlementSlot(number[i], slot.state(), slot.tier(), spot.x(), spot.z(), spot.cell(), spot.province(),
                    parent < 0 ? -1 : number[parent], List.of()));
        }
        return new SettlementPlan(provinces, rules, placed, wanted);
    }

    /**
     * The main settlement of a province (its highest rank, the first on a tie), or -1 for a province without settlements.
     */
    public int seat(int province) {
        return province <= 0 || province >= seats.length ? -1 : seats[province];
    }
}
