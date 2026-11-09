package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.Mercenary;
import io.github.sekerator.kingdomworld.core.army.MercenaryBand;
import io.github.sekerator.kingdomworld.core.army.March;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.combat.Formation;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The army in saved data. Every entry is read on its own: an entry that cannot be read is described and left out, and the
 * caller keeps the whole data read-only.
 *
 * <pre>
 * soldiers: [ { citizen, kingdom, experience, kills, battles, recruited } ]
 * squads: [ { id, kingdom, members: [uuid], commander?, band, formation, order: { kind, points: [ { dim, x, y, z } ], radius, player?, deal,
 *            via: [ { dim, x, y, z } ], target } } ]
 * marches: [ { squad, at: { dim, x, y, z }, path: [ { dim, x, y, z } ], faceX, faceZ } ]
 * bands: [ { id, kingdom, price, day, paidUntil, hired, fighters: [ { id, seed, female, level, weapon, damage, speed, ranged, armor,
 *            toughness, experience, health } ] } ]
 * barracks: [ { kingdom, dim, x, y, z } ]
 * offers: [ { kingdom, day } ]
 * nextSquad, nextBand
 * </pre>
 */
final class ArmyNbt {
    static final String SOLDIERS = "soldiers";
    static final String SQUADS = "squads";
    static final String BANDS = "bands";
    static final String BARRACKS = "barracks";
    static final String OFFERS = "offers";
    static final String MARCHES = "marches";
    static final String NEXT_SQUAD = "nextSquad";
    static final String NEXT_BAND = "nextBand";

    private ArmyNbt() {
    }

    static void write(ArmyState army, CompoundTag tag) {
        ListTag soldiers = new ListTag();
        for (Soldier soldier : army.soldiers()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("citizen", soldier.citizenId());
            entry.putUUID("kingdom", soldier.kingdomId());
            entry.putLong("experience", soldier.experience());
            entry.putInt("kills", soldier.kills());
            entry.putInt("battles", soldier.battles());
            entry.putLong("recruited", soldier.recruited());
            soldiers.add(entry);
        }
        tag.put(SOLDIERS, soldiers);
        ListTag squads = new ListTag();
        for (ArmySquad squad : army.squads()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", squad.id());
            entry.putUUID("kingdom", squad.kingdomId());
            ListTag members = new ListTag();
            for (UUID member : squad.members()) {
                members.add(NbtUtils.createUUID(member));
            }
            entry.put("members", members);
            if (squad.commander() != null) {
                entry.putUUID("commander", squad.commander());
            }
            entry.putLong("band", squad.band());
            entry.put("order", writeOrder(squad.order()));
            entry.putString("formation", squad.formation().id());
            squads.add(entry);
        }
        tag.put(SQUADS, squads);
        ListTag marches = new ListTag();
        for (Map.Entry<Long, March> entry : army.marches().entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong("squad", entry.getKey());
            one.put("at", writePos(entry.getValue().at()));
            one.put("path", writePoints(entry.getValue().path()));
            one.putDouble("faceX", entry.getValue().faceX());
            one.putDouble("faceZ", entry.getValue().faceZ());
            marches.add(one);
        }
        tag.put(MARCHES, marches);
        ListTag bands = new ListTag();
        for (MercenaryBand band : army.bands()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", band.id());
            entry.putUUID("kingdom", band.kingdomId());
            entry.putLong("price", band.dailyPrice());
            entry.putLong("day", band.day());
            entry.putBoolean("hired", band.hired());
            entry.putLong("paidUntil", band.hired() ? band.paidUntil() : 0);
            ListTag fighters = new ListTag();
            for (Mercenary fighter : band.fighters()) {
                CompoundTag one = new CompoundTag();
                one.putUUID("id", fighter.id());
                one.putInt("seed", fighter.nameSeed());
                one.putBoolean("female", fighter.female());
                one.putInt("level", fighter.level());
                one.putString("weapon", fighter.weapon());
                one.putDouble("damage", fighter.damage());
                one.putDouble("speed", fighter.attackSpeed());
                one.putBoolean("ranged", fighter.ranged());
                one.putFloat("armor", fighter.armor());
                one.putFloat("toughness", fighter.toughness());
                one.putInt("experience", fighter.experience());
                one.putDouble("health", fighter.health());
                fighters.add(one);
            }
            entry.put("fighters", fighters);
            bands.add(entry);
        }
        tag.put(BANDS, bands);
        ListTag barracks = new ListTag();
        for (Map.Entry<UUID, Set<WorldPos>> entry : army.barracks().entrySet()) {
            for (WorldPos pos : entry.getValue()) {
                CompoundTag one = writePos(pos);
                one.putUUID("kingdom", entry.getKey());
                barracks.add(one);
            }
        }
        tag.put(BARRACKS, barracks);
        ListTag offers = new ListTag();
        for (Map.Entry<UUID, Long> entry : army.offersDays().entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("kingdom", entry.getKey());
            one.putLong("day", entry.getValue());
            offers.add(one);
        }
        tag.put(OFFERS, offers);
        tag.putLong(NEXT_SQUAD, army.nextSquadId());
        tag.putLong(NEXT_BAND, army.nextBandId());
    }

    static CompoundTag writeOrder(Order order) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", order.kind().id());
        ListTag points = new ListTag();
        for (WorldPos point : order.points()) {
            points.add(writePos(point));
        }
        tag.put("points", points);
        tag.putInt("radius", order.radius());
        if (order.player() != null) {
            tag.putUUID("player", order.player());
        }
        tag.putLong("deal", order.deal());
        if (!order.via().isEmpty()) {
            tag.put("via", writePoints(order.via()));
        }
        if (order.target() != 0) {
            tag.putLong("target", order.target());
        }
        return tag;
    }

    private static ListTag writePoints(List<WorldPos> points) {
        ListTag list = new ListTag();
        for (WorldPos point : points) {
            list.add(writePos(point));
        }
        return list;
    }

    private static List<WorldPos> readPoints(CompoundTag tag, String key) {
        List<WorldPos> points = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            points.add(readPos(list.getCompound(i)));
        }
        return points;
    }

    private static CompoundTag writePos(WorldPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", pos.dimension());
        tag.putInt("x", pos.x());
        tag.putInt("y", pos.y());
        tag.putInt("z", pos.z());
        return tag;
    }

    private static WorldPos readPos(CompoundTag tag) {
        return new WorldPos(tag.getString("dim"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    static Order readOrder(CompoundTag tag) {
        Order.Kind kind = Order.Kind.byId(tag.getString("kind"))
                .orElseThrow(() -> new IllegalArgumentException("unknown order '" + tag.getString("kind") + "'"));
        List<WorldPos> points = new ArrayList<>();
        ListTag list = tag.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            points.add(readPos(list.getCompound(i)));
        }
        return new Order(kind, points, tag.getInt("radius"), tag.hasUUID("player") ? tag.getUUID("player") : null, tag.getLong("deal"),
                readPoints(tag, "via"), tag.getLong("target"));
    }

    /**
     * Reads everything that can be read into an empty army.
     *
     * @return what could not be read
     */
    static List<String> read(CompoundTag tag, ArmyState army) {
        List<String> problems = new ArrayList<>();
        for (CompoundTag entry : list(tag, SOLDIERS, problems)) {
            try {
                army.restoreSoldier(new Soldier(uuid(entry, "citizen"), uuid(entry, "kingdom"), entry.getLong("experience"),
                        entry.getInt("kills"), entry.getInt("battles"), entry.getLong("recruited")));
            } catch (RuntimeException e) {
                problems.add("soldier: " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, BANDS, problems)) {
            try {
                List<Mercenary> fighters = new ArrayList<>();
                ListTag ones = entry.getList("fighters", Tag.TAG_COMPOUND);
                for (int i = 0; i < ones.size(); i++) {
                    CompoundTag one = ones.getCompound(i);
                    fighters.add(new Mercenary(uuid(one, "id"), one.getInt("seed"), one.getBoolean("female"), one.getInt("level"),
                            one.getString("weapon"), one.getDouble("damage"), one.getDouble("speed"), one.getBoolean("ranged"),
                            one.getFloat("armor"), one.getFloat("toughness"), one.getInt("experience"), one.getDouble("health")));
                }
                army.restoreBand(new MercenaryBand(entry.getLong("id"), uuid(entry, "kingdom"), fighters, entry.getLong("price"),
                        entry.getLong("day"), entry.getBoolean("hired") ? entry.getLong("paidUntil") : MercenaryBand.OFFER));
            } catch (RuntimeException e) {
                problems.add("band #" + entry.getLong("id") + ": " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, SQUADS, problems)) {
            try {
                List<UUID> members = new ArrayList<>();
                for (Tag id : entry.getList("members", Tag.TAG_INT_ARRAY)) {
                    members.add(NbtUtils.loadUUID(id));
                }
                army.restoreSquad(new ArmySquad(entry.getLong("id"), uuid(entry, "kingdom"), members,
                        entry.hasUUID("commander") ? entry.getUUID("commander") : null, entry.getLong("band"),
                        readOrder(entry.getCompound("order")), entry.contains("formation")
                        ? Formation.byId(entry.getString("formation")).orElseThrow(() -> new IllegalArgumentException("unknown formation"))
                        : Formation.LINE));
            } catch (RuntimeException e) {
                problems.add("squad #" + entry.getLong("id") + ": " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, BARRACKS, problems)) {
            try {
                army.restoreBarracks(uuid(entry, "kingdom"), readPos(entry));
            } catch (RuntimeException e) {
                problems.add("barracks: " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, OFFERS, problems)) {
            try {
                army.restoreOffersDay(uuid(entry, "kingdom"), entry.getLong("day"));
            } catch (RuntimeException e) {
                problems.add("offers: " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, MARCHES, problems)) {
            try {
                army.restoreMarch(entry.getLong("squad"), new March(readPos(entry.getCompound("at")), readPoints(entry, "path"),
                        entry.getDouble("faceX"), entry.getDouble("faceZ")));
            } catch (RuntimeException e) {
                problems.add("march of #" + entry.getLong("squad") + ": " + e.getMessage());
            }
        }
        army.restoreNextIds(tag.getLong(NEXT_SQUAD), tag.getLong(NEXT_BAND));
        return problems;
    }

    private static List<CompoundTag> list(CompoundTag tag, String key, List<String> problems) {
        if (!tag.contains(key)) {
            return List.of();
        }
        if (!tag.contains(key, Tag.TAG_LIST)) {
            problems.add("'" + key + "' is not a list");
            return List.of();
        }
        ListTag list = (ListTag) tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            problems.add("'" + key + "' does not hold compounds");
            return List.of();
        }
        List<CompoundTag> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("no '" + key + "'");
        }
        return tag.getUUID(key);
    }
}
