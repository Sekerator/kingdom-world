package io.github.sekerator.kingdomworld.common.diplomacy;

import io.github.sekerator.kingdomworld.core.diplomacy.Bond;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.PeaceTerms;
import io.github.sekerator.kingdomworld.core.diplomacy.Proposal;
import io.github.sekerator.kingdomworld.core.diplomacy.Relations;
import io.github.sekerator.kingdomworld.core.diplomacy.Treaty;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.diplomacy.WarGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Diplomacy and wars in NBT. Reading collects what it cannot understand instead of throwing; the caller keeps the data
 * read-only when anything is left over.
 */
final class DiplomacyNbt {
    private DiplomacyNbt() {
    }

    static CompoundTag write(DiplomacyState state) {
        CompoundTag tag = new CompoundTag();
        ListTag treaties = new ListTag();
        for (Treaty treaty : state.treaties()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("first", treaty.first());
            entry.putUUID("second", treaty.second());
            entry.putString("kind", treaty.kind().id());
            entry.putLong("since", treaty.since());
            entry.putLong("until", treaty.until());
            if (treaty.overlord() != null) {
                entry.putUUID("overlord", treaty.overlord());
                entry.putInt("share", treaty.share());
            }
            treaties.add(entry);
        }
        tag.put("treaties", treaties);
        ListTag wars = new ListTag();
        for (War war : state.wars()) {
            wars.add(writeWar(war));
        }
        tag.put("wars", wars);
        ListTag proposals = new ListTag();
        for (Proposal proposal : state.proposals()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", proposal.id());
            entry.putUUID("from", proposal.from());
            entry.putUUID("to", proposal.to());
            entry.putString("kind", proposal.kind().id());
            if (proposal.vassal() != null) {
                entry.putUUID("vassal", proposal.vassal());
            }
            entry.putLong("war", proposal.war());
            entry.put("terms", writeTerms(proposal.terms()));
            entry.putLong("created", proposal.created());
            entry.putLong("expires", proposal.expires());
            entry.putString("status", proposal.status().id());
            proposals.add(entry);
        }
        tag.put("proposals", proposals);
        ListTag relations = new ListTag();
        for (Map.Entry<String, Relations.Relation> entry : state.relations().all().entrySet()) {
            CompoundTag relation = new CompoundTag();
            relation.putString("pair", entry.getKey());
            relation.putInt("score", entry.getValue().score());
            CompoundTag reasons = new CompoundTag();
            entry.getValue().reasons().forEach((reason, count) -> reasons.putInt(reason.id(), count));
            relation.put("reasons", reasons);
            relations.add(relation);
        }
        tag.put("relations", relations);
        ListTag presence = new ListTag();
        for (Map.Entry<UUID, long[]> entry : state.presence().all().entrySet()) {
            CompoundTag seen = new CompoundTag();
            seen.putUUID("kingdom", entry.getKey());
            seen.putLong("present", entry.getValue()[0]);
            seen.putLong("fight", entry.getValue()[1]);
            presence.add(seen);
        }
        tag.put("presence", presence);
        ListTag tributes = new ListTag();
        for (DiplomacyState.Tribute tribute : state.tributes()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("payer", tribute.payer());
            entry.putUUID("payee", tribute.payee());
            entry.putInt("share", tribute.share());
            entry.putLong("until", tribute.untilDay());
            entry.putLong("war", tribute.war());
            tributes.add(entry);
        }
        tag.put("tributes", tributes);
        ListTag claims = new ListTag();
        for (DiplomacyState.Claim claim : state.claims()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("war", claim.war());
            entry.putUUID("winner", claim.winner());
            entry.putUUID("loser", claim.loser());
            entry.putInt("chunks", claim.chunks());
            entry.putString("city", claim.city());
            entry.putBoolean("throne", claim.throne());
            entry.putLong("made", claim.made());
            claims.add(entry);
        }
        tag.put("claims", claims);
        ListTag chronicle = new ListTag();
        for (DiplomacyState.Event event : state.chronicle()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("time", event.time());
            entry.putString("kind", event.kind());
            entry.putUUID("first", event.first());
            if (event.second() != null) {
                entry.putUUID("second", event.second());
            }
            entry.putLong("war", event.war());
            entry.putString("detail", event.detail());
            chronicle.add(entry);
        }
        tag.put("chronicle", chronicle);
        tag.put("lastDeclared", writeTimes(state.lastDeclared()));
        tag.put("lastNpcWar", writeTimes(state.lastNpcWar()));
        tag.put("lastWarEnd", writeTimes(state.lastWarEnd()));
        tag.putLong("tributeDay", state.tributeDay());
        tag.putLong("nextWar", state.nextWarId());
        tag.putLong("nextProposal", state.nextProposalId());
        return tag;
    }

    private static CompoundTag writeWar(War war) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", war.id());
        tag.putUUID("attacker", war.attacker());
        tag.putUUID("defender", war.defender());
        tag.putString("goal", war.goal().id());
        tag.putLong("declared", war.declared());
        tag.putLong("starts", war.starts());
        tag.putString("phase", war.phase().id());
        ListTag allies = new ListTag();
        for (War.Side side : War.Side.values()) {
            for (UUID ally : war.allies(side)) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("kingdom", ally);
                entry.putString("side", side.id());
                allies.add(entry);
            }
        }
        tag.put("allies", allies);
        ListTag calls = new ListTag();
        war.calls().forEach((kingdom, side) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("kingdom", kingdom);
            entry.putString("side", side.id());
            calls.add(entry);
        });
        tag.put("calls", calls);
        for (War.Side side : War.Side.values()) {
            CompoundTag of = new CompoundTag();
            of.putDouble("score", war.score(side));
            of.putDouble("exhaustion", war.exhaustion(side));
            War.Tally tally = war.tally(side);
            of.putInt("battles", tally.battles());
            of.putInt("kills", tally.kills());
            of.putInt("caravans", tally.caravans());
            of.putInt("blockades", tally.blockades());
            of.putInt("losses", tally.losses());
            tag.put("side_" + side.id(), of);
        }
        tag.putBoolean("brokePact", war.brokePact());
        tag.putLong("ended", war.ended());
        tag.putString("result", war.result().id());
        tag.put("terms", writeTerms(war.terms()));
        tag.putLong("lastDay", war.lastDay());
        return tag;
    }

    private static CompoundTag writeTerms(PeaceTerms terms) {
        CompoundTag tag = new CompoundTag();
        if (terms.winner() != null) {
            tag.putUUID("winner", terms.winner());
        }
        tag.putLong("indemnity", terms.indemnity());
        tag.putInt("tributeShare", terms.tributeShare());
        tag.putInt("tributeDays", terms.tributeDays());
        tag.putBoolean("vassalage", terms.vassalage());
        tag.putBoolean("independence", terms.independence());
        tag.putInt("land", terms.landChunks());
        tag.putString("city", terms.city());
        tag.putBoolean("throne", terms.throne());
        return tag;
    }

    private static ListTag writeTimes(Map<UUID, Long> times) {
        ListTag list = new ListTag();
        times.forEach((kingdom, time) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("kingdom", kingdom);
            entry.putLong("time", time);
            list.add(entry);
        });
        return list;
    }

    /**
     * Reads diplomacy into an empty state.
     *
     * @return what could not be read, empty if everything was
     */
    static List<String> read(CompoundTag tag, DiplomacyState state) {
        List<String> problems = new ArrayList<>();
        ListTag treaties = tag.getList("treaties", Tag.TAG_COMPOUND);
        for (int i = 0; i < treaties.size(); i++) {
            CompoundTag entry = treaties.getCompound(i);
            try {
                Bond kind = Bond.byId(entry.getString("kind")).orElseThrow(() -> new IllegalArgumentException("kind " + entry.getString("kind")));
                UUID overlord = entry.hasUUID("overlord") ? entry.getUUID("overlord") : null;
                state.restoreTreaty(new Treaty(entry.getUUID("first"), entry.getUUID("second"), kind, entry.getLong("since"), entry.getLong("until"),
                        overlord, entry.getInt("share")));
            } catch (RuntimeException e) {
                problems.add("treaty " + i + ": " + e.getMessage());
            }
        }
        ListTag wars = tag.getList("wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < wars.size(); i++) {
            try {
                state.restoreWar(readWar(wars.getCompound(i)));
            } catch (RuntimeException e) {
                problems.add("war " + i + ": " + e.getMessage());
            }
        }
        ListTag proposals = tag.getList("proposals", Tag.TAG_COMPOUND);
        for (int i = 0; i < proposals.size(); i++) {
            CompoundTag entry = proposals.getCompound(i);
            try {
                Proposal.Kind kind = Proposal.Kind.byId(entry.getString("kind")).orElseThrow(() -> new IllegalArgumentException("kind"));
                Proposal.Status status = Proposal.Status.byId(entry.getString("status")).orElseThrow(() -> new IllegalArgumentException("status"));
                state.restoreProposal(new Proposal(entry.getLong("id"), entry.getUUID("from"), entry.getUUID("to"), kind,
                        entry.hasUUID("vassal") ? entry.getUUID("vassal") : null, entry.getLong("war"), readTerms(entry.getCompound("terms")),
                        entry.getLong("created"), entry.getLong("expires"), status));
            } catch (RuntimeException e) {
                problems.add("proposal " + i + ": " + e.getMessage());
            }
        }
        ListTag relations = tag.getList("relations", Tag.TAG_COMPOUND);
        for (int i = 0; i < relations.size(); i++) {
            CompoundTag entry = relations.getCompound(i);
            try {
                String pair = entry.getString("pair");
                String[] ids = pair.split("\\|");
                if (ids.length != 2) {
                    throw new IllegalArgumentException("pair " + pair);
                }
                UUID.fromString(ids[0]);
                UUID.fromString(ids[1]);
                Map<Relations.Reason, Integer> reasons = new EnumMap<>(Relations.Reason.class);
                CompoundTag counts = entry.getCompound("reasons");
                for (String key : counts.getAllKeys()) {
                    Relations.Reason reason = Relations.Reason.byId(key).orElseThrow(() -> new IllegalArgumentException("reason " + key));
                    reasons.put(reason, counts.getInt(key));
                }
                state.relations().restore(pair, entry.getInt("score"), reasons);
            } catch (RuntimeException e) {
                problems.add("relation " + i + ": " + e.getMessage());
            }
        }
        ListTag presence = tag.getList("presence", Tag.TAG_COMPOUND);
        for (int i = 0; i < presence.size(); i++) {
            CompoundTag entry = presence.getCompound(i);
            try {
                state.presence().restore(entry.getUUID("kingdom"), entry.getLong("present"), entry.getLong("fight"));
            } catch (RuntimeException e) {
                problems.add("presence " + i + ": " + e.getMessage());
            }
        }
        ListTag tributes = tag.getList("tributes", Tag.TAG_COMPOUND);
        for (int i = 0; i < tributes.size(); i++) {
            CompoundTag entry = tributes.getCompound(i);
            try {
                state.restoreTribute(new DiplomacyState.Tribute(entry.getUUID("payer"), entry.getUUID("payee"), entry.getInt("share"),
                        entry.getLong("until"), entry.getLong("war")));
            } catch (RuntimeException e) {
                problems.add("tribute " + i + ": " + e.getMessage());
            }
        }
        ListTag claims = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claims.size(); i++) {
            CompoundTag entry = claims.getCompound(i);
            try {
                state.restoreClaim(new DiplomacyState.Claim(entry.getLong("war"), entry.getUUID("winner"), entry.getUUID("loser"),
                        entry.getInt("chunks"), entry.getString("city"), entry.getBoolean("throne"), entry.getLong("made")));
            } catch (RuntimeException e) {
                problems.add("claim " + i + ": " + e.getMessage());
            }
        }
        ListTag chronicle = tag.getList("chronicle", Tag.TAG_COMPOUND);
        for (int i = 0; i < chronicle.size(); i++) {
            CompoundTag entry = chronicle.getCompound(i);
            try {
                state.restoreEvent(new DiplomacyState.Event(entry.getLong("time"), entry.getString("kind"), entry.getUUID("first"),
                        entry.hasUUID("second") ? entry.getUUID("second") : null, entry.getLong("war"), entry.getString("detail")));
            } catch (RuntimeException e) {
                problems.add("chronicle " + i + ": " + e.getMessage());
            }
        }
        try {
            state.restoreTimes(readTimes(tag.getList("lastDeclared", Tag.TAG_COMPOUND)), readTimes(tag.getList("lastNpcWar", Tag.TAG_COMPOUND)),
                    readTimes(tag.getList("lastWarEnd", Tag.TAG_COMPOUND)), tag.contains("tributeDay") ? tag.getLong("tributeDay") : Long.MIN_VALUE);
        } catch (RuntimeException e) {
            problems.add("times: " + e.getMessage());
        }
        state.restoreNextIds(tag.getLong("nextWar"), tag.getLong("nextProposal"));
        return problems;
    }

    private static War readWar(CompoundTag tag) {
        WarGoal goal = WarGoal.byId(tag.getString("goal")).orElseThrow(() -> new IllegalArgumentException("goal " + tag.getString("goal")));
        War war = new War(tag.getLong("id"), tag.getUUID("attacker"), tag.getUUID("defender"), goal, tag.getLong("declared"), tag.getLong("starts"));
        War.Phase phase = War.Phase.byId(tag.getString("phase")).orElseThrow(() -> new IllegalArgumentException("phase"));
        War.Result result = War.Result.byId(tag.getString("result")).orElseThrow(() -> new IllegalArgumentException("result"));
        double[] score = new double[2];
        double[] exhaustion = new double[2];
        for (War.Side side : War.Side.values()) {
            CompoundTag of = tag.getCompound("side_" + side.id());
            score[side.index()] = of.getDouble("score");
            exhaustion[side.index()] = of.getDouble("exhaustion");
            war.tally(side).restore(of.getInt("battles"), of.getInt("kills"), of.getInt("caravans"), of.getInt("blockades"), of.getInt("losses"));
        }
        war.restore(phase, score, exhaustion, tag.getBoolean("brokePact"), tag.getLong("ended"), result, readTerms(tag.getCompound("terms")),
                tag.getLong("lastDay"));
        ListTag allies = tag.getList("allies", Tag.TAG_COMPOUND);
        for (int i = 0; i < allies.size(); i++) {
            CompoundTag entry = allies.getCompound(i);
            war.restoreAlly(entry.getUUID("kingdom"), side(entry.getString("side")));
        }
        ListTag calls = tag.getList("calls", Tag.TAG_COMPOUND);
        for (int i = 0; i < calls.size(); i++) {
            CompoundTag entry = calls.getCompound(i);
            war.restoreCall(entry.getUUID("kingdom"), side(entry.getString("side")));
        }
        return war;
    }

    private static War.Side side(String id) {
        Optional<War.Side> side = War.Side.byId(id);
        return side.orElseThrow(() -> new IllegalArgumentException("side " + id));
    }

    private static PeaceTerms readTerms(CompoundTag tag) {
        return new PeaceTerms(tag.hasUUID("winner") ? tag.getUUID("winner") : null, tag.getLong("indemnity"), tag.getInt("tributeShare"),
                tag.getInt("tributeDays"), tag.getBoolean("vassalage"), tag.getBoolean("independence"), tag.getInt("land"), tag.getString("city"),
                tag.getBoolean("throne"));
    }

    private static Map<UUID, Long> readTimes(ListTag list) {
        Map<UUID, Long> times = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            times.put(entry.getUUID("kingdom"), entry.getLong("time"));
        }
        return times;
    }
}
