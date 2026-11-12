package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The plan of the kingdoms of the world, the progress of their appearance and the records of the kingdoms in saved data. Every
 * entry is read on its own: an entry that cannot be read is described and left out, and the caller keeps the whole data
 * read-only.
 *
 * <pre>
 * plan: { cursor, shift, done, next }
 * sites: [ { id, x, z, level, seed, towns: [x, z, ...], status, kingdom?, stage, step, ground, reason, given (format 3: 1 money, 2 laws, 4 stores) } ]
 * kingdoms: [ { kingdom, site, level, latin, cyrillic, character, traits: [id], ruler, gender, towns: [ { latin, cyrillic } ],
 *              founded, turn, camps (format 5): [ { x, z, y, status } ] } ]
 * rulers: see {@link RulerNbt} (format 2)
 * </pre>
 */
final class NpcNbt {
    private NpcNbt() {
    }

    static void write(NpcState state, CompoundTag tag) {
        CompoundTag plan = new CompoundTag();
        plan.putInt("cursor", state.planCursor());
        plan.putInt("shift", state.planShift());
        plan.putInt("pass", state.planPass());
        plan.putBoolean("done", state.planned());
        plan.putInt("next", state.nextSiteId());
        tag.put("plan", plan);
        ListTag sites = new ListTag();
        for (NpcSite site : state.sites()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", site.id());
            entry.putInt("x", site.x());
            entry.putInt("z", site.z());
            entry.putString("level", site.level().id());
            entry.putLong("seed", site.seed());
            entry.putInt("layout", site.layout());
            entry.putString("style", site.style());
            entry.putString("wood", site.wood());
            int[] towns = new int[site.towns().size() * 2];
            for (int i = 0; i < site.towns().size(); i++) {
                towns[2 * i] = site.towns().get(i).x();
                towns[2 * i + 1] = site.towns().get(i).z();
            }
            entry.putIntArray("towns", towns);
            entry.putString("status", site.status().id());
            site.kingdomId().ifPresent(id -> entry.putUUID("kingdom", id));
            entry.putString("stage", site.stage().id());
            entry.putInt("step", site.step());
            entry.putInt("ground", site.groundY());
            entry.putString("reason", site.reason());
            entry.putInt("given", site.givenFlags());
            sites.add(entry);
        }
        tag.put("sites", sites);
        ListTag kingdoms = new ListTag();
        for (NpcKingdom kingdom : state.kingdoms()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("kingdom", kingdom.kingdomId());
            entry.putInt("site", kingdom.siteId());
            entry.putString("level", kingdom.level().id());
            entry.putString("latin", kingdom.name().latin());
            entry.putString("cyrillic", kingdom.name().cyrillic());
            entry.putString("character", kingdom.character().id());
            ListTag traits = new ListTag();
            kingdom.traits().forEach(trait -> traits.add(StringTag.valueOf(trait.id())));
            entry.put("traits", traits);
            entry.putInt("ruler", kingdom.rulerSeed());
            entry.putString("gender", kingdom.rulerGender().id());
            ListTag towns = new ListTag();
            for (NpcNames.Name town : kingdom.towns()) {
                CompoundTag name = new CompoundTag();
                name.putString("latin", town.latin());
                name.putString("cyrillic", town.cyrillic());
                towns.add(name);
            }
            entry.put("towns", towns);
            entry.putLong("founded", kingdom.founded());
            entry.putLong("turn", kingdom.lastTurnDay());
            ListTag camps = new ListTag();
            for (io.github.sekerator.kingdomworld.core.npc.ArmyCamp camp : state.camps(kingdom.kingdomId())) {
                CompoundTag place = new CompoundTag();
                place.putInt("x", camp.x());
                place.putInt("z", camp.z());
                place.putInt("y", camp.y());
                place.putString("status", camp.status().id());
                camps.add(place);
            }
            if (!camps.isEmpty()) {
                entry.put("camps", camps);
            }
            kingdoms.add(entry);
        }
        tag.put("kingdoms", kingdoms);
        RulerNbt.write(state, tag);
    }

    /**
     * Reads what can be read into the state.
     *
     * @return descriptions of the entries that could not be read
     */
    static List<String> read(CompoundTag tag, NpcState state) {
        List<String> problems = new ArrayList<>();
        CompoundTag plan = tag.getCompound("plan");
        int maxId = 0;
        ListTag sites = tag.getList("sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < sites.size(); i++) {
            CompoundTag entry = sites.getCompound(i);
            try {
                NpcLevel level = NpcLevel.byId(entry.getString("level")).orElseThrow(() -> new IllegalArgumentException("level"));
                int[] towns = entry.getIntArray("towns");
                if (towns.length % 2 != 0) {
                    throw new IllegalArgumentException("towns");
                }
                List<NpcSite.Town> list = new ArrayList<>();
                for (int t = 0; t + 1 < towns.length; t += 2) {
                    list.add(new NpcSite.Town(towns[t], towns[t + 1]));
                }
                // Places saved before the districts have no layout: they keep the grid they were planned with.
                int layout = entry.contains("layout") ? entry.getInt("layout") : NpcSite.LAYOUT_GRID;
                NpcSite site = new NpcSite(entry.getInt("id"), entry.getInt("x"), entry.getInt("z"), level, entry.getLong("seed"), list, layout,
                        entry.getString("style"), entry.getString("wood"));
                NpcSite.Status status = NpcSite.Status.byId(entry.getString("status"))
                        .orElseThrow(() -> new IllegalArgumentException("status"));
                SpawnStage stage = SpawnStage.byId(entry.getString("stage")).orElseThrow(() -> new IllegalArgumentException("stage"));
                UUID kingdom = entry.hasUUID("kingdom") ? entry.getUUID("kingdom") : null;
                site.restore(status, kingdom, stage, entry.getInt("step"), entry.getInt("ground"), entry.getString("reason"),
                        entry.getInt("given"));
                state.restoreSite(site);
                maxId = Math.max(maxId, site.id());
            } catch (RuntimeException e) {
                problems.add("site " + i + ": " + e.getMessage());
            }
        }
        state.restorePlan(plan.getInt("cursor"), plan.getInt("shift"), plan.getInt("pass"), plan.getBoolean("done"),
                Math.max(plan.getInt("next"), maxId + 1));
        ListTag kingdoms = tag.getList("kingdoms", Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            CompoundTag entry = kingdoms.getCompound(i);
            try {
                if (!entry.hasUUID("kingdom")) {
                    throw new IllegalArgumentException("kingdom");
                }
                NpcLevel level = NpcLevel.byId(entry.getString("level")).orElseThrow(() -> new IllegalArgumentException("level"));
                RulerCharacter character = RulerCharacter.byId(entry.getString("character"))
                        .orElseThrow(() -> new IllegalArgumentException("character"));
                List<RulerCharacter.Trait> traits = new ArrayList<>();
                ListTag traitTags = entry.getList("traits", Tag.TAG_STRING);
                for (int t = 0; t < traitTags.size(); t++) {
                    String id = traitTags.getString(t);
                    traits.add(RulerCharacter.Trait.byId(id).orElseThrow(() -> new IllegalArgumentException("trait " + id)));
                }
                Gender gender = Gender.byId(entry.getString("gender")).orElseThrow(() -> new IllegalArgumentException("gender"));
                List<NpcNames.Name> towns = new ArrayList<>();
                ListTag townTags = entry.getList("towns", Tag.TAG_COMPOUND);
                for (int t = 0; t < townTags.size(); t++) {
                    CompoundTag name = townTags.getCompound(t);
                    towns.add(new NpcNames.Name(name.getString("latin"), name.getString("cyrillic")));
                }
                String latin = entry.getString("latin");
                if (latin.isEmpty()) {
                    throw new IllegalArgumentException("name");
                }
                List<io.github.sekerator.kingdomworld.core.npc.ArmyCamp> camps = new ArrayList<>();
                ListTag campTags = entry.getList("camps", Tag.TAG_COMPOUND);
                for (int c = 0; c < campTags.size(); c++) {
                    CompoundTag place = campTags.getCompound(c);
                    String status = place.getString("status");
                    camps.add(new io.github.sekerator.kingdomworld.core.npc.ArmyCamp(place.getInt("x"), place.getInt("z"), place.getInt("y"),
                            io.github.sekerator.kingdomworld.core.npc.ArmyCamp.Status.byId(status)
                                    .orElseThrow(() -> new IllegalArgumentException("camp status " + status))));
                }
                state.restoreKingdom(new NpcKingdom(entry.getUUID("kingdom"), entry.getInt("site"), level,
                        new NpcNames.Name(latin, entry.getString("cyrillic")), character, traits, entry.getInt("ruler"), gender, towns,
                        entry.getLong("founded"), entry.getLong("turn")));
                state.restoreCamps(entry.getUUID("kingdom"), camps);
            } catch (RuntimeException e) {
                problems.add("kingdom " + i + ": " + e.getMessage());
            }
        }
        RulerNbt.read(tag, state, problems);
        return problems;
    }
}
