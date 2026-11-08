package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.nobility.Court;
import io.github.sekerator.kingdomworld.core.nobility.Demand;
import io.github.sekerator.kingdomworld.core.nobility.Faction;
import io.github.sekerator.kingdomworld.core.nobility.Fief;
import io.github.sekerator.kingdomworld.core.nobility.Grant;
import io.github.sekerator.kingdomworld.core.nobility.NobilityState;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.nobility.Note;
import io.github.sekerator.kingdomworld.core.nobility.Plot;
import io.github.sekerator.kingdomworld.core.nobility.Spy;
import io.github.sekerator.kingdomworld.core.nobility.Stability;
import io.github.sekerator.kingdomworld.core.nobility.Title;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The nobility in NBT. Reading collects what it does not understand instead of throwing, so that the data goes read-only and
 * nothing is overwritten.
 */
final class NobilityNbt {
    private NobilityNbt() {
    }

    // ---------------------------------------------------------------- writing

    static CompoundTag write(NobilityState state) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("nextId", state.peekNextId());
        ListTag houses = new ListTag();
        for (NobleHouse house : state.houses()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", house.id());
            entry.putUUID("kingdom", house.kingdomId());
            entry.putInt("family", house.familySeed());
            entry.putInt("head", house.headSeed());
            entry.putString("gender", house.headGender().name());
            entry.putString("character", house.character().id());
            entry.putString("trait", house.trait().id());
            entry.putString("faction", house.faction().id());
            entry.putLong("wealth", house.wealth());
            entry.putInt("loyalty", house.loyalty());
            entry.putInt("generation", house.generation());
            entry.putLong("founded", house.founded());
            houses.add(entry);
        }
        tag.put("houses", houses);
        ListTag grants = new ListTag();
        for (Grant grant : state.grants()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", grant.id());
            entry.putUUID("kingdom", grant.kingdomId());
            entry.putString("title", grant.title().id());
            entry.putString("fief", grant.fief().code());
            entry.putLong("house", grant.house());
            if (grant.player() != null) {
                entry.putUUID("player", grant.player());
            }
            entry.putLong("since", grant.since());
            grants.add(entry);
        }
        tag.put("grants", grants);
        ListTag courts = new ListTag();
        for (Court court : state.courts()) {
            courts.add(writeCourt(court));
        }
        tag.put("courts", courts);
        ListTag demands = new ListTag();
        for (Demand demand : state.demands()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", demand.id());
            entry.putUUID("kingdom", demand.kingdomId());
            entry.putString("faction", demand.faction().id());
            entry.putLong("house", demand.house());
            entry.putString("kind", demand.kind().id());
            if (demand.target() != null) {
                entry.putUUID("target", demand.target());
            }
            entry.putLong("value", demand.value());
            entry.putLong("made", demand.made());
            entry.putLong("due", demand.due());
            demands.add(entry);
        }
        tag.put("demands", demands);
        ListTag plots = new ListTag();
        for (Plot plot : state.plots()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", plot.id());
            entry.putUUID("kingdom", plot.kingdomId());
            entry.putLong("house", plot.house());
            entry.putInt("progress", plot.progress());
            entry.putInt("needed", plot.needed());
            entry.putString("status", plot.status().id());
            plot.sponsor().ifPresent(sponsor -> entry.putUUID("sponsor", sponsor));
            entry.putLong("started", plot.started());
            plots.add(entry);
        }
        tag.put("plots", plots);
        ListTag spies = new ListTag();
        for (Spy spy : state.spies()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", spy.id());
            entry.putUUID("owner", spy.owner());
            entry.putInt("seed", spy.seed());
            entry.putString("gender", spy.gender().name());
            entry.putInt("skill", spy.skill());
            entry.putString("status", spy.status().id());
            spy.mission().ifPresent(mission -> {
                CompoundTag m = new CompoundTag();
                m.putString("task", mission.task().id());
                m.putUUID("target", mission.target());
                m.putLong("house", mission.house());
                m.putLong("started", mission.started());
                m.putLong("ends", mission.ends());
                entry.put("mission", m);
            });
            spy.captor().ifPresent(captor -> entry.putUUID("captor", captor));
            entry.putLong("ransom", spy.ransom());
            entry.putLong("releaseBy", spy.releaseBy());
            entry.putLong("hired", spy.hired());
            spies.add(entry);
        }
        tag.put("spies", spies);
        return tag;
    }

    private static CompoundTag writeCourt(Court court) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("kingdom", court.kingdomId());
        CompoundTag content = new CompoundTag();
        court.contents().forEach((faction, value) -> content.putInt(faction.id(), value));
        entry.put("content", content);
        entry.putLong("played", court.playedDays());
        entry.putLong("lastPlayed", court.lastPlayed());
        entry.putInt("low", court.lowDays());
        entry.putBoolean("warned", court.warned());
        entry.putLong("lastDemand", court.lastDemand());
        entry.putLong("lastGrant", court.lastPlayerGrant());
        entry.putInt("rebelDays", court.rebelDays());
        entry.putLong("rebelHouse", court.rebelHouse());
        entry.putInt("stability", court.stability().value());
        ListTag modifiers = new ListTag();
        for (Stability.Modifier modifier : court.modifiers()) {
            CompoundTag m = new CompoundTag();
            m.putString("cause", modifier.cause());
            m.putInt("amount", modifier.amount());
            m.putLong("until", modifier.until());
            modifiers.add(m);
        }
        entry.put("modifiers", modifiers);
        entry.put("chronicle", writeNotes(court.chronicle()));
        entry.put("reports", writeNotes(court.reports()));
        return entry;
    }

    private static ListTag writeNotes(List<Note> notes) {
        ListTag list = new ListTag();
        for (Note note : notes) {
            CompoundTag n = new CompoundTag();
            n.putLong("day", note.day());
            n.putString("key", note.key());
            ListTag args = new ListTag();
            for (String arg : note.args()) {
                args.add(StringTag.valueOf(arg));
            }
            n.put("args", args);
            list.add(n);
        }
        return list;
    }

    // ---------------------------------------------------------------- reading

    /**
     * Reads the tag into the state.
     *
     * @return what could not be understood; empty if everything was read
     */
    static List<String> read(CompoundTag tag, NobilityState state) {
        List<String> problems = new ArrayList<>();
        state.restoreNextId(tag.getLong("nextId"));
        ListTag houses = tag.getList("houses", Tag.TAG_COMPOUND);
        for (int i = 0; i < houses.size(); i++) {
            try {
                CompoundTag e = houses.getCompound(i);
                RulerCharacter character = RulerCharacter.byId(e.getString("character")).orElseThrow(() -> bad("character", e));
                RulerCharacter.Trait trait = RulerCharacter.Trait.byId(e.getString("trait")).orElseThrow(() -> bad("trait", e));
                Faction faction = Faction.byId(e.getString("faction")).orElseThrow(() -> bad("faction", e));
                state.restoreHouse(new NobleHouse(e.getLong("id"), e.getUUID("kingdom"), e.getInt("family"), e.getInt("head"),
                        Gender.valueOf(e.getString("gender")), character, trait, faction, e.getLong("wealth"), e.getInt("loyalty"),
                        e.getInt("generation"), e.getLong("founded")));
            } catch (RuntimeException ex) {
                problems.add("house " + i + ": " + ex.getMessage());
            }
        }
        ListTag grants = tag.getList("grants", Tag.TAG_COMPOUND);
        for (int i = 0; i < grants.size(); i++) {
            try {
                CompoundTag e = grants.getCompound(i);
                Title title = Title.byId(e.getString("title")).orElseThrow(() -> bad("title", e));
                Fief fief = Fief.parse(e.getString("fief")).orElseThrow(() -> bad("fief", e));
                state.restoreGrant(new Grant(e.getLong("id"), e.getUUID("kingdom"), title, fief, e.getLong("house"),
                        e.hasUUID("player") ? e.getUUID("player") : null, e.getLong("since")));
            } catch (RuntimeException ex) {
                problems.add("title " + i + ": " + ex.getMessage());
            }
        }
        ListTag courts = tag.getList("courts", Tag.TAG_COMPOUND);
        for (int i = 0; i < courts.size(); i++) {
            try {
                readCourt(courts.getCompound(i), state);
            } catch (RuntimeException ex) {
                problems.add("court " + i + ": " + ex.getMessage());
            }
        }
        ListTag demands = tag.getList("demands", Tag.TAG_COMPOUND);
        for (int i = 0; i < demands.size(); i++) {
            try {
                CompoundTag e = demands.getCompound(i);
                state.restoreDemand(new Demand(e.getLong("id"), e.getUUID("kingdom"), Faction.byId(e.getString("faction")).orElseThrow(() -> bad("faction", e)),
                        e.getLong("house"), Demand.Kind.byId(e.getString("kind")).orElseThrow(() -> bad("kind", e)),
                        e.hasUUID("target") ? e.getUUID("target") : null, e.getLong("value"), e.getLong("made"), e.getLong("due")));
            } catch (RuntimeException ex) {
                problems.add("demand " + i + ": " + ex.getMessage());
            }
        }
        ListTag plots = tag.getList("plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < plots.size(); i++) {
            try {
                CompoundTag e = plots.getCompound(i);
                state.restorePlot(new Plot(e.getLong("id"), e.getUUID("kingdom"), e.getLong("house"), e.getInt("progress"), e.getInt("needed"),
                        Plot.Status.byId(e.getString("status")).orElseThrow(() -> bad("status", e)), e.hasUUID("sponsor") ? e.getUUID("sponsor") : null,
                        e.getLong("started")));
            } catch (RuntimeException ex) {
                problems.add("plot " + i + ": " + ex.getMessage());
            }
        }
        ListTag spies = tag.getList("spies", Tag.TAG_COMPOUND);
        for (int i = 0; i < spies.size(); i++) {
            try {
                CompoundTag e = spies.getCompound(i);
                Spy.Status status = Spy.Status.byId(e.getString("status")).orElseThrow(() -> bad("status", e));
                Spy.Mission mission = null;
                if (e.contains("mission", Tag.TAG_COMPOUND)) {
                    CompoundTag m = e.getCompound("mission");
                    mission = new Spy.Mission(Spy.Task.byId(m.getString("task")).orElseThrow(() -> bad("task", m)), m.getUUID("target"),
                            m.getLong("house"), m.getLong("started"), m.getLong("ends"));
                }
                state.restoreSpy(new Spy(e.getLong("id"), e.getUUID("owner"), e.getInt("seed"), Gender.valueOf(e.getString("gender")), e.getInt("skill"),
                        status, mission, e.hasUUID("captor") ? e.getUUID("captor") : null, e.getLong("ransom"), e.getLong("releaseBy"), e.getLong("hired")));
            } catch (RuntimeException ex) {
                problems.add("spy " + i + ": " + ex.getMessage());
            }
        }
        problems.addAll(state.verify());
        return problems;
    }

    private static void readCourt(CompoundTag e, NobilityState state) {
        UUID kingdom = e.getUUID("kingdom");
        Map<Faction, Integer> content = new EnumMap<>(Faction.class);
        CompoundTag saved = e.getCompound("content");
        for (Faction faction : Faction.values()) {
            if (saved.contains(faction.id(), Tag.TAG_INT)) {
                content.put(faction, saved.getInt(faction.id()));
            }
        }
        List<Stability.Modifier> modifiers = new ArrayList<>();
        ListTag list = e.getList("modifiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            modifiers.add(new Stability.Modifier(m.getString("cause"), m.getInt("amount"), m.getLong("until")));
        }
        state.court(kingdom).restore(content, e.getLong("played"), e.getLong("lastPlayed"), e.getInt("low"), e.getBoolean("warned"),
                e.getLong("lastDemand"), e.getLong("lastGrant"), e.getInt("rebelDays"), e.getLong("rebelHouse"), modifiers,
                readNotes(e.getList("chronicle", Tag.TAG_COMPOUND)), readNotes(e.getList("reports", Tag.TAG_COMPOUND)), e.getInt("stability"));
    }

    private static List<Note> readNotes(ListTag list) {
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag n = list.getCompound(i);
            List<String> args = new ArrayList<>();
            ListTag saved = n.getList("args", Tag.TAG_STRING);
            for (int j = 0; j < saved.size(); j++) {
                args.add(saved.getString(j));
            }
            notes.add(new Note(n.getLong("day"), n.getString("key"), args));
        }
        return notes;
    }

    private static IllegalArgumentException bad(String what, CompoundTag tag) {
        return new IllegalArgumentException("unknown " + what + " in " + tag);
    }
}
