package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What the army of a kingdom knows of the squads of others. A squad of another kingdom shows only while it is in sight of
 * an eye of the kingdom: one of its squads, a tower or a wall, an area its scouts watch. When it leaves the sight, a pale
 * mark stays where it was last seen, with the time, until it is seen again or the memory fades.
 */
public final class FogOfWar {
    /**
     * Something that sees: a squad, a tower, a wall, a scouted area.
     *
     * @param radius how far it sees, in blocks
     */
    public record Eye(String dimension, int x, int z, int radius) {
        public boolean sees(WorldPos pos) {
            if (!dimension.equals(pos.dimension())) {
                return false;
            }
            long dx = pos.x() - x;
            long dz = pos.z() - z;
            return dx * dx + dz * dz <= (long) radius * radius;
        }
    }

    /**
     * A squad somebody might see.
     *
     * @param size how many fight in it
     */
    public record Contact(long squadId, UUID kingdomId, WorldPos at, int size) {
        public Contact {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(at, "at");
        }
    }

    /**
     * Where a squad was last seen.
     *
     * @param seen game time of the sighting
     */
    public record Sighting(long squadId, UUID kingdomId, WorldPos at, int size, long seen) {
    }

    /**
     * What a kingdom sees now and remembers.
     *
     * @param visible   the squads of others in sight
     * @param lastSeen  pale marks of squads out of sight
     */
    public record View(List<Contact> visible, List<Sighting> lastSeen) {
    }

    /** Pale marks of every kingdom: kingdom, then squad. */
    private final Map<UUID, Map<Long, Sighting>> memory = new HashMap<>();

    /**
     * The squads of others an eye sees.
     *
     * @param always kingdoms whose squads are seen wherever they are (one's own, allies, kingdoms a spy reports)
     */
    public static List<Contact> visible(UUID viewer, Collection<Eye> eyes, Collection<Contact> contacts, Set<UUID> always) {
        List<Contact> seen = new ArrayList<>();
        for (Contact contact : contacts) {
            if (contact.kingdomId().equals(viewer) || always.contains(contact.kingdomId())) {
                seen.add(contact);
                continue;
            }
            for (Eye eye : eyes) {
                if (eye.sees(contact.at())) {
                    seen.add(contact);
                    break;
                }
            }
        }
        return seen;
    }

    /**
     * Looks again for a kingdom: squads in sight are remembered where they are now; squads that left the sight leave their
     * pale mark; marks older than {@code forget} ticks fade, and so do marks of squads that are gone.
     *
     * @param alive the squads that still exist
     */
    public View look(UUID viewer, Collection<Eye> eyes, Collection<Contact> contacts, Set<UUID> always, Set<Long> alive, long now, long forget) {
        List<Contact> visible = visible(viewer, eyes, contacts, always);
        Map<Long, Sighting> marks = memory.computeIfAbsent(viewer, key -> new LinkedHashMap<>());
        Set<Long> inSight = new HashSet<>();
        List<Contact> others = new ArrayList<>();
        for (Contact contact : visible) {
            if (contact.kingdomId().equals(viewer)) {
                continue;
            }
            others.add(contact);
            inSight.add(contact.squadId());
            marks.put(contact.squadId(), new Sighting(contact.squadId(), contact.kingdomId(), contact.at(), contact.size(), now));
        }
        marks.values().removeIf(mark -> !alive.contains(mark.squadId()) || now - mark.seen() > forget);
        List<Sighting> pale = new ArrayList<>();
        for (Sighting mark : marks.values()) {
            if (!inSight.contains(mark.squadId())) {
                pale.add(mark);
            }
        }
        if (marks.isEmpty()) {
            memory.remove(viewer);
        }
        return new View(others, pale);
    }

    /**
     * Forgets what a kingdom saw, for a kingdom that is gone.
     */
    public void forget(UUID viewer) {
        memory.remove(viewer);
    }

    public void clear() {
        memory.clear();
    }
}
