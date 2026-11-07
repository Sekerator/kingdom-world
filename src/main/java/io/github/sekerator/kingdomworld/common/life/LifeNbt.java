package io.github.sekerator.kingdomworld.common.life;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.life.LifeState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writes the life of cities to NBT and reads it back.
 *
 * <pre>
 * root: formatVersion,
 *       sick: [ { citizen, since, progress } ], tavern: [ { citizen, day } ],
 *       festivals: [ { kingdom, called, day, treated, foundingYear, events: [ { kind, day, place } ] } ],
 *       fairs: [ { city, day } ], fairLots: [ { lot, host, visitor, day } ],
 *       rooms: [ { player, kingdom, bed: pos, until, previous: pos?, angle, forced } ], marks: [ { player, camps: [ long ] } ],
 *       playerBeds: [ { player, bed: pos } ]
 * pos: { dim, x, y, z }
 * </pre>
 *
 * A record that cannot be read is a problem: the data stays read-only, so that saving loses nothing.
 */
final class LifeNbt {
    private LifeNbt() {
    }

    static void write(LifeState state, CompoundTag root) {
        ListTag sick = new ListTag();
        state.allSick().forEach((id, value) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("citizen", id);
            tag.putLong("since", value.since());
            tag.putDouble("progress", value.progress());
            sick.add(tag);
        });
        root.put("sick", sick);
        ListTag tavern = new ListTag();
        for (UUID id : state.knownCitizens()) {
            long day = state.tavernDay(id);
            if (day != Long.MIN_VALUE) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("citizen", id);
                tag.putLong("day", day);
                tavern.add(tag);
            }
        }
        root.put("tavern", tavern);
        ListTag festivals = new ListTag();
        for (UUID kingdom : state.kingdoms()) {
            LifeState.Festival festival = state.festival(kingdom);
            CompoundTag tag = new CompoundTag();
            tag.putUUID("kingdom", kingdom);
            tag.putLong("called", festival.called());
            tag.putLong("day", festival.festivalDay());
            tag.putBoolean("treated", festival.treated());
            tag.putLong("foundingYear", festival.foundingYear());
            ListTag events = new ListTag();
            for (LifeState.Event event : state.events(kingdom)) {
                CompoundTag one = new CompoundTag();
                one.putString("kind", event.kind());
                one.putLong("day", event.day());
                one.putString("place", event.place());
                events.add(one);
            }
            tag.put("events", events);
            festivals.add(tag);
        }
        root.put("festivals", festivals);
        ListTag fairs = new ListTag();
        state.fairs().forEach((city, day) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("city", city);
            tag.putLong("day", day);
            fairs.add(tag);
        });
        root.put("fairs", fairs);
        ListTag lots = new ListTag();
        for (LifeState.FairLot lot : state.fairLots()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("lot", lot.lotId());
            tag.putUUID("host", lot.host());
            tag.putUUID("visitor", lot.visitor());
            tag.putLong("day", lot.day());
            lots.add(tag);
        }
        root.put("fairLots", lots);
        ListTag rooms = new ListTag();
        state.rooms().forEach((player, room) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player);
            tag.putUUID("kingdom", room.kingdomId());
            tag.put("bed", pos(room.bed()));
            tag.putLong("until", room.until());
            if (room.previous() != null) {
                tag.put("previous", pos(room.previous()));
            }
            tag.putFloat("angle", room.previousAngle());
            tag.putBoolean("forced", room.previousForced());
            rooms.add(tag);
        });
        root.put("rooms", rooms);
        ListTag marks = new ListTag();
        state.allMarks().forEach((player, camps) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player);
            ListTag list = new ListTag();
            for (long camp : camps) {
                list.add(LongTag.valueOf(camp));
            }
            tag.put("camps", list);
            marks.add(tag);
        });
        root.put("marks", marks);
        ListTag beds = new ListTag();
        state.playerBeds().forEach((player, bed) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("player", player);
            tag.put("bed", pos(bed));
            beds.add(tag);
        });
        root.put("playerBeds", beds);
    }

    private static CompoundTag pos(WorldPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", pos.dimension());
        tag.putInt("x", pos.x());
        tag.putInt("y", pos.y());
        tag.putInt("z", pos.z());
        return tag;
    }

    private static WorldPos readPos(CompoundTag tag) {
        if (!tag.contains("dim", Tag.TAG_STRING) || tag.getString("dim").isEmpty()) {
            throw new IllegalArgumentException("a position without a dimension");
        }
        return new WorldPos(tag.getString("dim"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("no " + key);
        }
        return tag.getUUID(key);
    }

    /**
     * Reads what can be read into the state.
     *
     * @return the parts that could not be read
     */
    static List<String> read(CompoundTag root, LifeState state) {
        List<String> problems = new ArrayList<>();
        for (Tag raw : root.getList("sick", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                double progress = tag.getDouble("progress");
                if (!(progress >= 0 && progress < 1)) {
                    throw new IllegalArgumentException("progress " + progress);
                }
                state.restoreSick(uuid(tag, "citizen"), new LifeState.Sick(tag.getLong("since"), progress));
            } catch (RuntimeException e) {
                problems.add("an ill citizen: " + e.getMessage());
            }
        }
        for (Tag raw : root.getList("tavern", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                state.restoreTavern(uuid(tag, "citizen"), tag.getLong("day"));
            } catch (RuntimeException e) {
                problems.add("an evening in a tavern: " + e.getMessage());
            }
        }
        for (Tag raw : root.getList("festivals", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                List<LifeState.Event> events = new ArrayList<>();
                for (Tag one : tag.getList("events", Tag.TAG_COMPOUND)) {
                    CompoundTag event = (CompoundTag) one;
                    String kind = event.getString("kind");
                    if (!List.of("festival", "founding", "fair").contains(kind)) {
                        throw new IllegalArgumentException("unknown event " + kind);
                    }
                    events.add(new LifeState.Event(kind, event.getLong("day"), event.getString("place")));
                }
                state.restoreFestival(uuid(tag, "kingdom"), new LifeState.Festival(tag.getLong("called"), tag.getLong("day"),
                        tag.getBoolean("treated"), tag.getLong("foundingYear")), events);
            } catch (RuntimeException e) {
                problems.add("the festivals of a kingdom: " + e.getMessage());
            }
        }
        for (Tag raw : root.getList("fairs", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (tag.getString("city").isEmpty()) {
                problems.add("a fair without a city");
            } else {
                state.restoreFair(tag.getString("city"), tag.getLong("day"));
            }
        }
        for (Tag raw : root.getList("fairLots", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                state.restoreFairLot(new LifeState.FairLot(tag.getLong("lot"), uuid(tag, "host"), uuid(tag, "visitor"), tag.getLong("day")));
            } catch (RuntimeException e) {
                problems.add("goods of a fair: " + e.getMessage());
            }
        }
        for (Tag raw : root.getList("rooms", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                WorldPos previous = tag.contains("previous", Tag.TAG_COMPOUND) ? readPos(tag.getCompound("previous")) : null;
                state.restoreRoom(uuid(tag, "player"), new LifeState.Room(uuid(tag, "kingdom"), readPos(tag.getCompound("bed")),
                        tag.getLong("until"), previous, tag.getFloat("angle"), tag.getBoolean("forced")));
            } catch (RuntimeException e) {
                problems.add("a room in a tavern: " + e.getMessage());
            }
        }
        for (Tag raw : root.getList("marks", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                Set<Long> camps = new LinkedHashSet<>();
                for (Tag one : tag.getList("camps", Tag.TAG_LONG)) {
                    camps.add(((LongTag) one).getAsLong());
                }
                state.restoreMarks(uuid(tag, "player"), camps);
            } catch (RuntimeException e) {
                problems.add("camps marked on an atlas: " + e.getMessage());
            }
        }
        for (Tag raw : root.getList("playerBeds", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                state.restorePlayerBed(uuid(tag, "player"), readPos(tag.getCompound("bed")));
            } catch (RuntimeException e) {
                problems.add("the bed of a player: " + e.getMessage());
            }
        }
        return problems;
    }

    static Map<String, Integer> counts(LifeState state) {
        return Map.of("sick", state.allSick().size(), "rooms", state.rooms().size(), "kingdoms", state.kingdoms().size());
    }
}
