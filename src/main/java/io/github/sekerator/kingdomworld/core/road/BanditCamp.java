package io.github.sekerator.kingdomworld.core.road;

import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.trade.Goods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A camp of robbers in the wild: how many there are, whether their leader lives, what they robbed and who knows of it.
 * Near players the camp stands in the world (a fire, tents and a chest) and its robbers walk about; then the robbed goods
 * are in the chest, and here only what was robbed while the chest was not loaded. A camp whose robbers are all dead is
 * cleared: it robs nobody, and goes away once its goods are taken.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class BanditCamp {
    /** Most entries of goods a camp keeps; more robbed goods are lost to the wild. */
    public static final int MAX_LOOT = 256;

    private final long id;
    private final String dimension;
    private final int x;
    private final int z;
    private final long founded;
    private int y;
    private int bandits;
    private boolean leader;
    private long lastGrowth;
    private final List<Goods> loot = new ArrayList<>();
    private final Set<UUID> known = new LinkedHashSet<>();
    private boolean built;
    private final List<Long> structure = new ArrayList<>();
    private long chest;
    private boolean hasChest;
    private int raids;
    private long lastRaid;

    public BanditCamp(long id, String dimension, int x, int y, int z, int bandits, boolean leader, long founded) {
        this.id = id;
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.x = x;
        this.y = y;
        this.z = z;
        this.bandits = Math.max(0, bandits);
        this.leader = leader;
        this.founded = founded;
        this.lastGrowth = founded;
    }

    public long id() {
        return id;
    }

    public String dimension() {
        return dimension;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public void setY(int value) {
        y = value;
    }

    public long founded() {
        return founded;
    }

    /**
     * Robbers besides the leader.
     */
    public int bandits() {
        return bandits;
    }

    public boolean leader() {
        return leader;
    }

    /**
     * Everybody of the camp, the leader included.
     */
    public int size() {
        return bandits + (leader ? 1 : 0);
    }

    public boolean cleared() {
        return size() == 0;
    }

    public long lastGrowth() {
        return lastGrowth;
    }

    public void setLastGrowth(long day) {
        lastGrowth = day;
    }

    public void grow(int max) {
        if (!cleared() && bandits < max) {
            bandits++;
        }
    }

    /**
     * A robber of the camp died.
     */
    public void lose(boolean wasLeader) {
        if (wasLeader) {
            leader = false;
        } else if (bandits > 0) {
            bandits--;
        }
    }

    public void lose(int count) {
        bandits = Math.max(0, bandits - Math.max(0, count));
    }

    public void killLeader() {
        leader = false;
    }

    public List<Goods> loot() {
        return Collections.unmodifiableList(loot);
    }

    public long lootItems() {
        long sum = 0;
        for (Goods goods : loot) {
            sum += goods.count();
        }
        return sum;
    }

    /**
     * Adds robbed goods.
     *
     * @return the goods that did not fit and are lost
     */
    public List<Goods> addLoot(List<Goods> goods) {
        List<Goods> lost = new ArrayList<>();
        for (Goods entry : goods) {
            boolean merged = false;
            for (int i = 0; i < loot.size(); i++) {
                Goods held = loot.get(i);
                if (held.item().equals(entry.item()) && held.data().equals(entry.data()) && (long) held.count() + entry.count() <= Goods.MAX_COUNT) {
                    loot.set(i, held.withCount(held.count() + entry.count()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                if (loot.size() < MAX_LOOT) {
                    loot.add(entry);
                } else {
                    lost.add(entry);
                }
            }
        }
        return lost;
    }

    /**
     * Takes all the goods, to move them into the chest or to whoever won them.
     */
    public List<Goods> takeLoot() {
        List<Goods> all = List.copyOf(loot);
        loot.clear();
        return all;
    }

    public Set<UUID> known() {
        return Collections.unmodifiableSet(known);
    }

    /**
     * A kingdom learns of the camp.
     *
     * @return whether it did not know of it before
     */
    public boolean learn(UUID kingdomId) {
        return known.add(kingdomId);
    }

    public void forget(UUID kingdomId) {
        known.remove(kingdomId);
    }

    public boolean knownTo(UUID kingdomId) {
        return known.contains(kingdomId);
    }

    /**
     * Whether its fire, tents and chest stand in the world.
     */
    public boolean built() {
        return built;
    }

    public void setBuilt(boolean value) {
        built = value;
    }

    /**
     * Blocks of the camp standing in the world, as packed positions; they drop nothing when broken.
     */
    public List<Long> structure() {
        return Collections.unmodifiableList(structure);
    }

    public void setStructure(List<Long> positions) {
        structure.clear();
        structure.addAll(positions);
    }

    public boolean removeStructure(long position) {
        return structure.remove(Long.valueOf(position));
    }

    /**
     * The chest of the camp in the world, as a packed position, if it has one.
     */
    public java.util.OptionalLong chest() {
        return hasChest ? java.util.OptionalLong.of(chest) : java.util.OptionalLong.empty();
    }

    public void setChest(Long position) {
        hasChest = position != null;
        chest = position == null ? 0 : position;
    }

    public int raids() {
        return raids;
    }

    public long lastRaid() {
        return lastRaid;
    }

    public void raided(long gameTime) {
        raids = (int) Math.min(Integer.MAX_VALUE, (long) raids + 1);
        lastRaid = gameTime;
    }

    /**
     * The robbers of the camp as a side of a battle, at most {@code party} of them besides the leader.
     */
    public Side side(BanditRules rules, int party) {
        int units = Math.min(bandits, Math.max(0, party));
        double strength = units * rules.banditStrength() + (leader ? rules.leaderStrength() : 0.0);
        return Side.crowd(Side.Kind.BANDITS, strength, units + (leader ? 1 : 0));
    }

    public double distanceSqr(int otherX, int otherZ) {
        double dx = x - otherX;
        double dz = z - otherZ;
        return dx * dx + dz * dz;
    }

    /**
     * Puts back the state read from saved data.
     */
    public void restore(long lastGrowthValue, List<Goods> lootValue, Set<UUID> knownValue, boolean builtValue, int raidsValue, long lastRaidValue) {
        lastGrowth = lastGrowthValue;
        loot.clear();
        addLoot(lootValue);
        known.clear();
        known.addAll(knownValue);
        built = builtValue;
        raids = Math.max(0, raidsValue);
        lastRaid = lastRaidValue;
    }
}
