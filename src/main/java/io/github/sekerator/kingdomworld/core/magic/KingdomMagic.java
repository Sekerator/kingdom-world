package io.github.sekerator.kingdomworld.core.magic;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The magic of one kingdom: its store of energy with the journal, the research of its tower, the spells switched on and
 * what they were paid for. Changed only through {@link MagicState}, which checks first and then changes everything or
 * nothing.
 */
public final class KingdomMagic {
    /**
     * A chunk under the ward.
     */
    public record WardChunk(String dimension, long chunk) {
        public WardChunk {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    private final UUID kingdomId;
    long energy;
    final EnergyJournal journal = new EnergyJournal();
    final EnumSet<Research> done = EnumSet.noneOf(Research.class);
    Research current;
    double progress;
    final EnumSet<KingdomSpell> enabled = EnumSet.noneOf(KingdomSpell.class);
    final EnumMap<KingdomSpell, Long> paidDay = new EnumMap<>(KingdomSpell.class);
    final Set<WardChunk> ward = new LinkedHashSet<>();
    final Set<UUID> healed = new HashSet<>();
    final Set<Long> windCaravans = new HashSet<>();

    KingdomMagic(UUID kingdomId) {
        this.kingdomId = Objects.requireNonNull(kingdomId, "kingdomId");
    }

    public UUID kingdomId() {
        return kingdomId;
    }

    public long energy() {
        return energy;
    }

    public EnergyJournal journal() {
        return journal;
    }

    public Set<Research> done() {
        return Collections.unmodifiableSet(done);
    }

    public boolean researched(Research research) {
        return done.contains(research);
    }

    /**
     * The level of the tower: how many researches are finished.
     */
    public int towerLevel() {
        return done.size();
    }

    public Optional<Research> current() {
        return Optional.ofNullable(current);
    }

    /**
     * Ticks of work of a mage of usual speed already put into the current research.
     */
    public double progress() {
        return progress;
    }

    public Set<KingdomSpell> enabled() {
        return Collections.unmodifiableSet(enabled);
    }

    public boolean isOn(KingdomSpell spell) {
        return enabled.contains(spell);
    }

    /**
     * The last game day a spell paid by the day was paid for.
     */
    public Map<KingdomSpell, Long> paidDays() {
        return Collections.unmodifiableMap(paidDay);
    }

    public Set<WardChunk> ward() {
        return Collections.unmodifiableSet(ward);
    }

    public Set<UUID> healed() {
        return Collections.unmodifiableSet(healed);
    }

    public Set<Long> windCaravans() {
        return Collections.unmodifiableSet(windCaravans);
    }

    boolean isEmpty() {
        return energy == 0 && done.isEmpty() && current == null && enabled.isEmpty() && ward.isEmpty() && journal.days().isEmpty();
    }
}
