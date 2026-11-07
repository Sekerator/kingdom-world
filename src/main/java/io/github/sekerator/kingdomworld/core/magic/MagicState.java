package io.github.sekerator.kingdomworld.core.magic;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.economy.Money;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * The magic of one world: the energy of every kingdom with its journal, the research of the mage towers, the spells of the
 * kingdoms, the blocks of magic on their land and the spells players learned.
 *
 * <p>Energy only comes from crystals dissolved by mages (and from operators). It is a whole number that never goes below
 * zero, and the store of a kingdom takes no more than its capacity. Every operation checks first and then changes everything
 * it changes, or refuses with a reason and changes nothing; nothing is ever owed.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class MagicState {
    private final Map<UUID, KingdomMagic> kingdoms = new LinkedHashMap<>();
    private final Map<WorldPos, MagicDevice> devices = new LinkedHashMap<>();
    private final Map<UUID, EnumSet<PlayerSpell>> learned = new LinkedHashMap<>();
    private long nextDevice = 1;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
    }

    private void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- reading

    public Optional<KingdomMagic> find(UUID kingdomId) {
        return Optional.ofNullable(kingdoms.get(kingdomId));
    }

    public Collection<KingdomMagic> kingdoms() {
        return Collections.unmodifiableCollection(kingdoms.values());
    }

    private KingdomMagic of(UUID kingdomId) {
        return kingdoms.computeIfAbsent(kingdomId, KingdomMagic::new);
    }

    public long energy(UUID kingdomId) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        return magic == null ? 0 : magic.energy;
    }

    public int towerLevel(UUID kingdomId) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        return magic == null ? 0 : magic.towerLevel();
    }

    public boolean isOn(UUID kingdomId, KingdomSpell spell) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        return magic != null && magic.isOn(spell);
    }

    public boolean researched(UUID kingdomId, Research research) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        return magic != null && magic.researched(research);
    }

    /**
     * How much energy a kingdom may hold: the base of the rules and the room of its accumulators.
     */
    public long capacity(UUID kingdomId, MagicRules rules) {
        return Money.addCapped(rules.baseCapacity, Money.multiplyCapped(devicesOf(kingdomId, MagicDevice.Kind.ACCUMULATOR).size(),
                rules.accumulatorCapacity));
    }

    /**
     * Room left in the store of a kingdom; 0 when it holds more than its capacity (after an accumulator was taken away).
     */
    public long room(UUID kingdomId, MagicRules rules) {
        return Math.max(0, capacity(kingdomId, rules) - energy(kingdomId));
    }

    // ---------------------------------------------------------------- energy

    /**
     * Whether a kingdom's store has room for {@code amount}.
     */
    public Outcome checkAdd(UUID kingdomId, long amount, MagicRules rules) {
        if (amount <= 0 || amount > Money.MAX) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        return room(kingdomId, rules) >= amount ? Outcome.DONE : Outcome.fail(KingdomError.ENERGY_FULL);
    }

    /**
     * Energy comes into a kingdom's store, within its capacity.
     */
    public Outcome add(UUID kingdomId, long amount, EnergyCategory category, long day, MagicRules rules) {
        Outcome check = checkAdd(kingdomId, amount, rules);
        if (!check.ok()) {
            return check;
        }
        KingdomMagic magic = of(kingdomId);
        magic.energy += amount;
        magic.journal.record(day, category, amount, 0);
        changed();
        return Outcome.DONE;
    }

    public Outcome checkSpend(UUID kingdomId, long amount) {
        if (amount < 0) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        return energy(kingdomId) >= amount ? Outcome.DONE : Outcome.fail(KingdomError.NOT_ENOUGH_ENERGY);
    }

    /**
     * Energy leaves a kingdom's store; refused without enough of it.
     */
    public Outcome spend(UUID kingdomId, long amount, EnergyCategory category, long day) {
        Outcome check = checkSpend(kingdomId, amount);
        if (!check.ok() || amount == 0) {
            return check;
        }
        KingdomMagic magic = of(kingdomId);
        magic.energy -= amount;
        magic.journal.record(day, category, 0, amount);
        changed();
        return Outcome.DONE;
    }

    /**
     * An operator sets the energy of a kingdom; capacity does not apply. The difference goes into the journal.
     */
    public void adminSet(UUID kingdomId, long amount, long day) {
        long value = Math.max(0, Math.min(Money.MAX, amount));
        KingdomMagic magic = of(kingdomId);
        long before = magic.energy;
        magic.energy = value;
        if (value > before) {
            magic.journal.record(day, EnergyCategory.ADMIN, value - before, 0);
        } else if (value < before) {
            magic.journal.record(day, EnergyCategory.ADMIN, 0, before - value);
        }
        changed();
    }

    // ---------------------------------------------------------------- research

    private Outcome checkManager(UUID actor, UUID kingdomId, KingdomWorldState world) {
        if (world.kingdoms().get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !world.membership().hasPermission(actor, kingdomId, Permission.MANAGE_MAGIC)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    /**
     * Whether a research can start now: nothing else under way, not finished before, what it needs finished, and energy for
     * its cost.
     *
     * @param actor a member with {@link Permission#MANAGE_MAGIC}; {@code null} for an operator
     */
    public Outcome checkResearch(UUID actor, UUID kingdomId, Research research, MagicRules rules, KingdomWorldState world) {
        Outcome check = checkManager(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        KingdomMagic magic = kingdoms.get(kingdomId);
        if (magic != null && magic.current != null) {
            return Outcome.fail(KingdomError.RESEARCH_UNDER_WAY);
        }
        if (magic != null && magic.done.contains(research)) {
            return Outcome.fail(KingdomError.ALREADY_RESEARCHED);
        }
        if (research.requires().isPresent() && (magic == null || !magic.done.contains(research.requires().get()))) {
            return Outcome.fail(KingdomError.RESEARCH_NEEDED);
        }
        return checkSpend(kingdomId, rules.researchCost(research));
    }

    /**
     * Starts a research: its cost leaves the store now, the mages of the tower then work on it.
     */
    public Outcome startResearch(UUID actor, UUID kingdomId, Research research, long day, MagicRules rules, KingdomWorldState world) {
        Outcome check = checkResearch(actor, kingdomId, research, rules, world);
        if (!check.ok()) {
            return check;
        }
        Outcome paid = spend(kingdomId, rules.researchCost(research), EnergyCategory.RESEARCH, day);
        if (!paid.ok()) {
            return paid;
        }
        KingdomMagic magic = of(kingdomId);
        magic.current = research;
        magic.progress = 0.0;
        changed();
        return Outcome.DONE;
    }

    /**
     * Mages put work into the current research of a kingdom.
     *
     * @param work ticks of work of mages of usual speed
     * @return the research, if this work finished it
     */
    public Optional<Research> advanceResearch(UUID kingdomId, double work, MagicRules rules) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        if (magic == null || magic.current == null || !(work > 0.0) || !Double.isFinite(work)) {
            return Optional.empty();
        }
        magic.progress += work;
        Research research = magic.current;
        if (magic.progress >= rules.researchTicks(research)) {
            magic.done.add(research);
            magic.current = null;
            magic.progress = 0.0;
            changed();
            return Optional.of(research);
        }
        changed();
        return Optional.empty();
    }

    /**
     * An operator finishes a research at once, or takes a finished one back.
     */
    public void adminResearch(UUID kingdomId, Research research, boolean finished) {
        KingdomMagic magic = of(kingdomId);
        if (finished) {
            magic.done.add(research);
            if (magic.current == research) {
                magic.current = null;
                magic.progress = 0.0;
            }
        } else {
            magic.done.remove(research);
            for (KingdomSpell spell : KingdomSpell.values()) {
                if (spell.research() == research) {
                    magic.enabled.remove(spell);
                }
            }
        }
        changed();
    }

    // ---------------------------------------------------------------- spells of kingdoms

    /**
     * Switches a spell of a kingdom on or off. A spell paid by the day pays for today as it is switched on.
     *
     * @param units the chunks under the ward or the posts fertility reaches, for the price of today
     */
    public Outcome setSpell(UUID actor, UUID kingdomId, KingdomSpell spell, boolean on, long units, long day, MagicRules rules,
                            KingdomWorldState world) {
        Outcome check = checkManager(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        KingdomMagic magic = kingdoms.get(kingdomId);
        boolean now = magic != null && magic.enabled.contains(spell);
        if (now == on) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        if (!on) {
            magic.enabled.remove(spell);
            changed();
            return Outcome.DONE;
        }
        if (magic == null || !magic.done.contains(spell.research())) {
            return Outcome.fail(KingdomError.RESEARCH_NEEDED);
        }
        if (spell.daily() && !Objects.equals(magic.paidDay.get(spell), day)) {
            long cost = Money.multiplyCapped(Math.max(0, units), rules.spellPrice(spell));
            Outcome paid = spend(kingdomId, cost, EnergyCategory.SPELLS, day);
            if (!paid.ok()) {
                return paid;
            }
            magic.paidDay.put(spell, day);
        }
        magic.enabled.add(spell);
        changed();
        return Outcome.DONE;
    }

    /**
     * The spells of a kingdom paid by the day pay for a new day; a spell without enough energy switches off.
     *
     * @param units the units each spell is paid for: chunks under the ward, posts fertility reaches
     * @return the spells that switched off
     */
    public List<KingdomSpell> chargeDay(UUID kingdomId, long day, MagicRules rules, ToLongFunction<KingdomSpell> units) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        List<KingdomSpell> off = new ArrayList<>();
        if (magic == null) {
            return off;
        }
        for (KingdomSpell spell : KingdomSpell.values()) {
            if (!spell.daily() || !magic.enabled.contains(spell) || Objects.equals(magic.paidDay.get(spell), day)) {
                continue;
            }
            long cost = Money.multiplyCapped(Math.max(0, units.applyAsLong(spell)), rules.spellPrice(spell));
            if (spend(kingdomId, cost, EnergyCategory.SPELLS, day).ok()) {
                magic.paidDay.put(spell, day);
            } else {
                magic.enabled.remove(spell);
                off.add(spell);
            }
            changed();
        }
        return off;
    }

    /**
     * A spell paid per use is used {@code uses} times: it pays, or switches off without enough energy.
     */
    public Outcome payPerUse(UUID kingdomId, KingdomSpell spell, long uses, long day, MagicRules rules) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        if (magic == null || !magic.enabled.contains(spell)) {
            return Outcome.fail(KingdomError.SPELL_OFF);
        }
        long cost = Money.multiplyCapped(Math.max(0, uses), rules.spellPrice(spell));
        Outcome paid = spend(kingdomId, cost, spell == KingdomSpell.GATES ? EnergyCategory.GATES : EnergyCategory.SPELLS, day);
        if (!paid.ok() && spell != KingdomSpell.GATES) {
            magic.enabled.remove(spell);
            changed();
        }
        return paid;
    }

    /**
     * Puts a chunk of the kingdom's own land under the ward, or takes it out.
     *
     * @param ownLand whether the chunk is land of the kingdom
     * @return the outcome, and whether the chunk is now under the ward
     */
    public Outcome toggleWard(UUID actor, UUID kingdomId, String dimension, long chunk, boolean ownLand, MagicRules rules,
                              KingdomWorldState world) {
        Outcome check = checkManager(actor, kingdomId, world);
        if (!check.ok()) {
            return check;
        }
        KingdomMagic magic = of(kingdomId);
        KingdomMagic.WardChunk entry = new KingdomMagic.WardChunk(dimension, chunk);
        if (magic.ward.remove(entry)) {
            changed();
            return Outcome.DONE;
        }
        if (!ownLand) {
            return Outcome.fail(KingdomError.NOT_OWNED);
        }
        if (magic.ward.size() >= rules.wardMaxChunks) {
            return Outcome.fail(KingdomError.LIMIT_REACHED);
        }
        magic.ward.add(entry);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether a chunk is under a ward that works now.
     */
    public boolean warded(String dimension, long chunk) {
        KingdomMagic.WardChunk entry = new KingdomMagic.WardChunk(dimension, chunk);
        for (KingdomMagic magic : kingdoms.values()) {
            if (magic.enabled.contains(KingdomSpell.WARD) && magic.ward.contains(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forgets ward chunks a kingdom no longer owns.
     */
    public void keepWard(UUID kingdomId, Predicate<KingdomMagic.WardChunk> owned) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        if (magic != null && magic.ward.removeIf(owned.negate())) {
            changed();
        }
    }

    /**
     * Healing pays for a wounded citizen the first time it treats it; returns whether the citizen was paid for just now
     * or before.
     */
    public boolean healWounded(UUID kingdomId, UUID citizen, long day, MagicRules rules) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        if (magic == null || !magic.enabled.contains(KingdomSpell.HEALING)) {
            return false;
        }
        if (magic.healed.contains(citizen)) {
            return true;
        }
        if (!payPerUse(kingdomId, KingdomSpell.HEALING, 1, day, rules).ok()) {
            return false;
        }
        magic.healed.add(citizen);
        changed();
        return true;
    }

    /**
     * Forgets the paid wounds of citizens who are no longer wounded.
     */
    public void keepHealed(UUID kingdomId, Predicate<UUID> stillWounded) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        if (magic != null && magic.healed.removeIf(stillWounded.negate())) {
            changed();
        }
    }

    /**
     * The fair wind pays for a caravan once; returns whether it was paid just now (and the caravan should be sped up).
     */
    public boolean speedCaravan(UUID kingdomId, long caravan, long day, MagicRules rules) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        if (magic == null || !magic.enabled.contains(KingdomSpell.TAILWIND) || magic.windCaravans.contains(caravan)) {
            return false;
        }
        if (!payPerUse(kingdomId, KingdomSpell.TAILWIND, 1, day, rules).ok()) {
            return false;
        }
        magic.windCaravans.add(caravan);
        changed();
        return true;
    }

    public boolean caravanSpedUp(long caravan) {
        for (KingdomMagic magic : kingdoms.values()) {
            if (magic.windCaravans.contains(caravan)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forgets caravans that arrived.
     */
    public void keepCaravans(UUID kingdomId, LongPredicate onTheWay) {
        KingdomMagic magic = kingdoms.get(kingdomId);
        if (magic != null && magic.windCaravans.removeIf(id -> !onTheWay.test(id))) {
            changed();
        }
    }

    // ---------------------------------------------------------------- devices

    public Optional<MagicDevice> device(WorldPos pos) {
        return Optional.ofNullable(devices.get(pos));
    }

    public Collection<MagicDevice> devices() {
        return Collections.unmodifiableCollection(devices.values());
    }

    public List<MagicDevice> devicesOf(UUID kingdomId, MagicDevice.Kind kind) {
        List<MagicDevice> result = new ArrayList<>();
        for (MagicDevice device : devices.values()) {
            if (device.kingdomId().equals(kingdomId) && device.kind() == kind) {
                result.add(device);
            }
        }
        return result;
    }

    public Optional<MagicDevice> deviceById(long id) {
        for (MagicDevice device : devices.values()) {
            if (device.id() == id) {
                return Optional.of(device);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a device may stand on the land of a kingdom: gates within the limit of the rules.
     */
    public Outcome checkDevice(MagicDevice.Kind kind, UUID kingdomId, MagicRules rules) {
        if (kind == MagicDevice.Kind.GATE && devicesOf(kingdomId, kind).size() >= rules.maxGates) {
            return Outcome.fail(KingdomError.LIMIT_REACHED);
        }
        return Outcome.DONE;
    }

    /**
     * A device was placed on the land of a kingdom (or now belongs to another kingdom); a device already known at the place
     * keeps its number.
     */
    public MagicDevice putDevice(MagicDevice.Kind kind, UUID kingdomId, WorldPos pos) {
        MagicDevice old = devices.get(pos);
        MagicDevice device = new MagicDevice(old != null && old.kind() == kind ? old.id() : nextDevice++, kind, kingdomId, pos);
        devices.put(pos, device);
        changed();
        return device;
    }

    public Optional<MagicDevice> removeDevice(WorldPos pos) {
        MagicDevice removed = devices.remove(pos);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- spells of players

    public Set<PlayerSpell> learned(UUID player) {
        EnumSet<PlayerSpell> spells = learned.get(player);
        return spells == null ? Set.of() : Collections.unmodifiableSet(spells);
    }

    public Map<UUID, Set<PlayerSpell>> allLearned() {
        Map<UUID, Set<PlayerSpell>> copy = new LinkedHashMap<>();
        learned.forEach((player, spells) -> copy.put(player, Collections.unmodifiableSet(EnumSet.copyOf(spells))));
        return Collections.unmodifiableMap(copy);
    }

    public boolean knows(UUID player, PlayerSpell spell) {
        EnumSet<PlayerSpell> spells = learned.get(player);
        return spells != null && spells.contains(spell);
    }

    /**
     * Whether a player may learn a spell at a focus of a kingdom: a member, the tower high enough, the guild rank high
     * enough, not known yet, and money on the account for the lesson.
     *
     * @param rank the player's rank in the guild, F outside it
     */
    public Outcome checkLearn(UUID player, UUID kingdomId, PlayerSpell spell, Rank rank, MagicRules rules, KingdomWorldState world) {
        if (world.kingdoms().get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!world.membership().isMember(player, kingdomId)) {
            return Outcome.fail(KingdomError.NOT_A_MEMBER);
        }
        if (knows(player, spell)) {
            return Outcome.fail(KingdomError.ALREADY_LEARNED);
        }
        if (towerLevel(kingdomId) < rules.towerLevel(spell)) {
            return Outcome.fail(KingdomError.TOWER_TOO_LOW);
        }
        if (!rank.atLeast(rules.minRank(spell))) {
            return Outcome.fail(KingdomError.RANK_TOO_LOW);
        }
        long price = rules.lessonPrice(spell);
        return price <= 0 ? Outcome.DONE : world.economy().checkPayTreasury(player, kingdomId, price);
    }

    /**
     * A player learns a spell and pays the lesson to the treasury of the kingdom.
     */
    public Outcome learn(UUID player, UUID kingdomId, PlayerSpell spell, Rank rank, long day, MagicRules rules, KingdomWorldState world) {
        Outcome check = checkLearn(player, kingdomId, spell, rank, rules, world);
        if (!check.ok()) {
            return check;
        }
        long price = rules.lessonPrice(spell);
        if (price > 0) {
            Outcome paid = world.economy().payTreasury(player, kingdomId, price, LedgerCategory.MAGIC, day);
            if (!paid.ok()) {
                return paid;
            }
        }
        learned.computeIfAbsent(player, p -> EnumSet.noneOf(PlayerSpell.class)).add(spell);
        changed();
        return Outcome.DONE;
    }

    /**
     * An operator teaches a spell or takes it away.
     */
    public void adminLearn(UUID player, PlayerSpell spell, boolean known) {
        if (known) {
            learned.computeIfAbsent(player, p -> EnumSet.noneOf(PlayerSpell.class)).add(spell);
        } else {
            EnumSet<PlayerSpell> spells = learned.get(player);
            if (spells != null) {
                spells.remove(spell);
                if (spells.isEmpty()) {
                    learned.remove(player);
                }
            }
        }
        changed();
    }

    // ---------------------------------------------------------------- kingdoms that are gone

    /**
     * A kingdom was dissolved: its energy, research, spells and devices are gone. Players keep what they learned.
     */
    public void forgetKingdom(UUID kingdomId) {
        boolean removed = kingdoms.remove(kingdomId) != null;
        removed |= devices.values().removeIf(device -> device.kingdomId().equals(kingdomId));
        if (removed) {
            changed();
        }
    }

    // ---------------------------------------------------------------- saved data

    /**
     * Restores the magic of a kingdom from saved data.
     */
    public void restoreKingdom(UUID kingdomId, long energy, Set<Research> done, Research current, double progress, Set<KingdomSpell> enabled,
                               Map<KingdomSpell, Long> paidDays, Collection<KingdomMagic.WardChunk> ward, Collection<UUID> healed,
                               Collection<Long> windCaravans) {
        if (kingdoms.containsKey(kingdomId)) {
            throw new IllegalArgumentException("Two stores of energy for " + kingdomId);
        }
        if (energy < 0 || energy > Money.MAX || !(progress >= 0.0) || !Double.isFinite(progress)) {
            throw new IllegalArgumentException("Energy out of range for " + kingdomId + ": " + energy);
        }
        KingdomMagic magic = new KingdomMagic(kingdomId);
        magic.energy = energy;
        magic.done.addAll(done);
        magic.current = current;
        magic.progress = current == null ? 0.0 : progress;
        magic.enabled.addAll(enabled);
        magic.paidDay.putAll(paidDays);
        magic.ward.addAll(ward);
        magic.healed.addAll(healed);
        magic.windCaravans.addAll(windCaravans);
        kingdoms.put(kingdomId, magic);
    }

    public void restoreJournal(UUID kingdomId, long day, Map<EnergyCategory, EnergyJournal.Totals> entries) {
        of(kingdomId).journal.restore(day, entries);
    }

    public void restoreDevice(MagicDevice device) {
        if (devices.putIfAbsent(device.pos(), device) != null) {
            throw new IllegalArgumentException("Two devices at " + device.pos());
        }
        for (MagicDevice other : devices.values()) {
            if (other != device && other.id() == device.id()) {
                throw new IllegalArgumentException("Two devices #" + device.id());
            }
        }
        nextDevice = Math.max(nextDevice, device.id() + 1);
    }

    public void restoreLearned(UUID player, Set<PlayerSpell> spells) {
        if (!spells.isEmpty()) {
            learned.computeIfAbsent(player, p -> EnumSet.noneOf(PlayerSpell.class)).addAll(spells);
        }
    }

    public void restoreNextDevice(long next) {
        nextDevice = Math.max(nextDevice, next);
    }

    public long nextDeviceId() {
        return nextDevice;
    }

    /**
     * Forgets everything, for data that could not be read.
     */
    public void clear() {
        kingdoms.clear();
        devices.clear();
        learned.clear();
        nextDevice = 1;
    }
}
