package io.github.sekerator.kingdomworld.core.siege;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything of the war in the world: the gates of city walls, sieges, places held by attackers, blocks broken in wars and
 * the journal of what peaces hand over.
 */
public final class SiegeState {
    /** Most places held at once; holds nobody keeps are forgotten first. */
    public static final int MAX_HOLDS = 1_024;
    /** Most sieges at once. */
    public static final int MAX_SIEGES = 64;

    private final Map<String, Gate> gates = new LinkedHashMap<>();
    private final Map<String, Hold> holds = new LinkedHashMap<>();
    private final Map<String, Siege> sieges = new LinkedHashMap<>();
    private final List<Conquest> conquests = new ArrayList<>();
    private final WarDamage damage = new WarDamage();
    /** "war/kingdom" of kings of players that were online in a war: their throne may be taken in it. */
    private final Set<String> kingsSeen = new LinkedHashSet<>();
    /** When an enemy was last near the cities of a kingdom. */
    private final Map<UUID, Long> enemyNear = new LinkedHashMap<>();
    private long nextConquest = 1;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {
        } : listener;
    }

    public void changed() {
        changeListener.run();
    }

    // ---------------------------------------------------------------- gates

    public Optional<Gate> gate(long wallId, int cell) {
        return Optional.ofNullable(gates.get(Gate.key(wallId, cell)));
    }

    public Collection<Gate> gates() {
        return Collections.unmodifiableCollection(gates.values());
    }

    public Gate putGate(Gate gate) {
        gates.put(gate.key(), gate);
        changed();
        return gate;
    }

    public void removeGatesOf(long wallId) {
        if (gates.values().removeIf(gate -> gate.wallId() == wallId)) {
            changed();
        }
    }

    // ---------------------------------------------------------------- holds

    public Optional<Hold> hold(String key) {
        return Optional.ofNullable(holds.get(key));
    }

    public Collection<Hold> holds() {
        return Collections.unmodifiableCollection(holds.values());
    }

    public List<Hold> holdsOf(long war) {
        List<Hold> result = new ArrayList<>();
        for (Hold hold : holds.values()) {
            if (hold.war() == war) {
                result.add(hold);
            }
        }
        return result;
    }

    /**
     * The hold of a place, made when it is first held.
     */
    public Hold holdAt(Hold.Kind kind, long war, UUID attacker, UUID defender, String dimension, long target, long now) {
        String key = Hold.key(kind, war, defender, dimension, target);
        Hold hold = holds.get(key);
        if (hold == null) {
            if (holds.size() >= MAX_HOLDS) {
                holds.values().removeIf(Hold::idle);
            }
            hold = new Hold(kind, war, attacker, defender, dimension, target, 0, false, now);
            holds.put(key, hold);
            changed();
        }
        return hold;
    }

    public void putHold(Hold hold) {
        holds.put(hold.key(), hold);
        changed();
    }

    /**
     * Forgets holds nobody keeps.
     */
    public void forgetIdleHolds() {
        if (holds.values().removeIf(Hold::idle)) {
            changed();
        }
    }

    /**
     * The chunks of a kingdom an enemy has taken, in any war.
     */
    public List<Hold> occupied(UUID defender) {
        List<Hold> result = new ArrayList<>();
        for (Hold hold : holds.values()) {
            if (hold.kind() == Hold.Kind.CHUNK && hold.taken() && hold.defender().equals(defender)) {
                result.add(hold);
            }
        }
        return result;
    }

    /**
     * Whether a chunk is taken by an enemy.
     */
    public Optional<Hold> occupation(String dimension, long chunk) {
        for (Hold hold : holds.values()) {
            if (hold.kind() == Hold.Kind.CHUNK && hold.taken() && hold.target() == chunk && hold.dimension().equals(dimension)) {
                return Optional.of(hold);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- sieges

    public Collection<Siege> sieges() {
        return Collections.unmodifiableCollection(sieges.values());
    }

    public Optional<Siege> siege(long war, UUID defender, long city) {
        return Optional.ofNullable(sieges.get(Siege.key(war, defender, city)));
    }

    public List<Siege> siegesBy(UUID attacker) {
        List<Siege> result = new ArrayList<>();
        for (Siege siege : sieges.values()) {
            if (siege.attacker().equals(attacker)) {
                result.add(siege);
            }
        }
        return result;
    }

    public List<Siege> siegesOf(UUID defender) {
        List<Siege> result = new ArrayList<>();
        for (Siege siege : sieges.values()) {
            if (siege.defender().equals(defender)) {
                result.add(siege);
            }
        }
        return result;
    }

    /**
     * Starts a siege.
     *
     * @return false when there are too many already, or a ruler of the world besieges another city already
     */
    public boolean startSiege(Siege siege) {
        if (sieges.containsKey(siege.key())) {
            sieges.put(siege.key(), siege);
            changed();
            return true;
        }
        if (sieges.size() >= MAX_SIEGES || (siege.npc() && !siegesBy(siege.attacker()).isEmpty())) {
            return false;
        }
        sieges.put(siege.key(), siege);
        changed();
        return true;
    }

    public Optional<Siege> stopSiege(long war, UUID defender, long city) {
        Siege removed = sieges.remove(Siege.key(war, defender, city));
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- the end of a war

    /**
     * Forgets the sieges and holds of a war that is over; what the peace hands over is in the journal already.
     */
    public void forgetWar(long war) {
        boolean any = sieges.values().removeIf(siege -> siege.war() == war);
        any |= holds.values().removeIf(hold -> hold.war() == war);
        any |= kingsSeen.removeIf(key -> key.startsWith(war + "/"));
        if (any) {
            changed();
        }
    }

    // ---------------------------------------------------------------- the journal of conquests

    public List<Conquest> conquests() {
        return Collections.unmodifiableList(conquests);
    }

    public Conquest addConquest(Conquest.Kind kind, long war, UUID winner, UUID loser, String dimension, List<Long> chunks, long city, long now) {
        Conquest conquest = new Conquest(nextConquest++, kind, war, winner, loser, dimension, chunks, city, Conquest.first(kind), now);
        conquests.add(conquest);
        changed();
        return conquest;
    }

    /**
     * Marks the step of a conquest done.
     */
    public Optional<Conquest> advance(long id) {
        for (int i = 0; i < conquests.size(); i++) {
            Conquest conquest = conquests.get(i);
            if (conquest.id() == id) {
                Conquest next = conquest.advance();
                if (next.done()) {
                    conquests.remove(i);
                } else {
                    conquests.set(i, next);
                }
                changed();
                return Optional.of(next);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- damage, kings, quiet

    public WarDamage damage() {
        return damage;
    }

    public void kingSeen(long war, UUID kingdom) {
        if (kingsSeen.add(war + "/" + kingdom)) {
            changed();
        }
    }

    public boolean kingWasSeen(long war, UUID kingdom) {
        return kingsSeen.contains(war + "/" + kingdom);
    }

    public Set<String> kingsSeen() {
        return Collections.unmodifiableSet(kingsSeen);
    }

    public void enemyNear(UUID kingdom, long now) {
        enemyNear.put(kingdom, now);
    }

    public long lastEnemyNear(UUID kingdom) {
        return enemyNear.getOrDefault(kingdom, Long.MIN_VALUE / 4);
    }

    public Map<UUID, Long> enemiesNear() {
        return Collections.unmodifiableMap(enemyNear);
    }

    /**
     * Forgets a kingdom that is gone.
     */
    public void forget(UUID kingdom) {
        gates.values().removeIf(gate -> gate.kingdomId().equals(kingdom));
        holds.values().removeIf(hold -> hold.attacker().equals(kingdom) || hold.defender().equals(kingdom));
        sieges.values().removeIf(siege -> siege.attacker().equals(kingdom) || siege.defender().equals(kingdom));
        kingsSeen.removeIf(key -> key.endsWith("/" + kingdom));
        enemyNear.remove(kingdom);
        damage.forget(kingdom);
        changed();
    }

    // ---------------------------------------------------------------- loading

    public long nextConquestId() {
        return nextConquest;
    }

    public void restoreGate(Gate gate) {
        gates.put(gate.key(), gate);
    }

    public void restoreHold(Hold hold) {
        holds.put(hold.key(), hold);
    }

    public void restoreSiege(Siege siege) {
        sieges.put(siege.key(), siege);
    }

    public void restoreConquest(Conquest conquest) {
        conquests.add(conquest);
        nextConquest = Math.max(nextConquest, conquest.id() + 1);
    }

    public void restoreKingSeen(String key) {
        kingsSeen.add(key);
    }

    public void restoreEnemyNear(UUID kingdom, long time) {
        enemyNear.put(kingdom, time);
    }

    public void restoreNextConquest(long value) {
        nextConquest = Math.max(nextConquest, value);
    }

    public void clear() {
        gates.clear();
        holds.clear();
        sieges.clear();
        conquests.clear();
        damage.clear();
        kingsSeen.clear();
        enemyNear.clear();
        nextConquest = 1;
    }
}
