package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.combat.Formation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A squad of the army: soldiers of one kingdom under a commander, or a hired band of mercenaries, with its order.
 *
 * @param members   the soldiers; empty for a band of mercenaries
 * @param commander the soldier in command, one of the members; {@code null} for a band (its captain commands)
 * @param band      the hired band of mercenaries this squad is, 0 for a squad of soldiers
 * @param formation how the squad marches and fights
 */
public record ArmySquad(long id, UUID kingdomId, List<UUID> members, UUID commander, long band, Order order, Formation formation) {
    /** Most soldiers in a squad. */
    public static final int MAX_MEMBERS = 32;

    public ArmySquad(long id, UUID kingdomId, List<UUID> members, UUID commander, long band, Order order) {
        this(id, kingdomId, members, commander, band, order, Formation.LINE);
    }

    public ArmySquad {
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(order, "order");
        if (formation == null) {
            formation = Formation.LINE;
        }
        members = List.copyOf(members);
        if (id < 1 || band < 0 || members.size() > MAX_MEMBERS || (band == 0) == members.isEmpty()) {
            throw new IllegalArgumentException("Squad out of range: #" + id);
        }
        if (band == 0 && (commander == null || !members.contains(commander))) {
            commander = members.get(0);
        }
        if (band != 0) {
            commander = null;
        }
    }

    public boolean mercenaries() {
        return band != 0;
    }

    public ArmySquad withOrder(Order value) {
        return new ArmySquad(id, kingdomId, members, commander, band, value, formation);
    }

    public ArmySquad withMembers(List<UUID> value, UUID newCommander) {
        return new ArmySquad(id, kingdomId, value, newCommander, band, order, formation);
    }

    public ArmySquad withFormation(Formation value) {
        return new ArmySquad(id, kingdomId, members, commander, band, order, value);
    }
}
