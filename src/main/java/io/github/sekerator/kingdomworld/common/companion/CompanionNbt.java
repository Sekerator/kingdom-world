package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionOrder;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.companion.LoyaltyNote;
import io.github.sekerator.kingdomworld.core.companion.LoyaltyReason;
import io.github.sekerator.kingdomworld.core.companion.Offer;
import io.github.sekerator.kingdomworld.core.companion.Origin;
import io.github.sekerator.kingdomworld.core.companion.Party;
import io.github.sekerator.kingdomworld.core.companion.PartyState;
import io.github.sekerator.kingdomworld.core.companion.Skills;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Companions, their packs, the markets and the parties in saved data. Every entry is read on its own: an entry that cannot
 * be read, or a pack with an item no mod knows any more, is described and left out, and the caller keeps the whole data
 * read-only so that nothing is lost by saving over it.
 *
 * <pre>
 * companions: [ { id, owner, status, seed, gender, skin, origin, level, experience, combat, carrying, stamina, health, food, hunger,
 *                 starving, loyalty, notes: [ { reason, change, time } ], kept, starve, warned, branch, price, wage, paidDay, unpaid,
 *                 order, orderPos?, lastPos?, acquired, kills, pack: [ { Slot, item } ] } ]
 * markets: [ { branch, period, offers: [ { id, slave, seed, gender, skin, origin, level, combat, carrying, stamina, price } ] } ]
 * closed: [ uuid ]
 * parties: [ { id, leader, members: [ uuid ], shares: [ { player, share } ], even, fire, contracts: [ { id, holder } ] } ]
 * invites: [ { party, player, from, expires } ]
 * nextOffer, nextParty
 * </pre>
 */
final class CompanionNbt {
    static final String COMPANIONS = "companions";
    static final String MARKETS = "markets";
    static final String CLOSED = "closed";
    static final String PARTIES = "parties";
    static final String INVITES = "invites";
    static final String NEXT_OFFER = "nextOffer";
    static final String NEXT_PARTY = "nextParty";

    private CompanionNbt() {
    }

    // ---------------------------------------------------------------- writing

    static void write(CompanionState state, Map<UUID, SimpleContainer> packs, CompoundTag tag) {
        ListTag companions = new ListTag();
        for (Companion companion : state.companions()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", companion.id());
            entry.putUUID("owner", companion.owner());
            entry.putString("status", companion.status().id());
            entry.putInt("seed", companion.nameSeed());
            entry.putString("gender", companion.gender().id());
            entry.putInt("skin", companion.skin());
            entry.putString("origin", companion.origin().id());
            entry.putInt("level", companion.level());
            entry.putLong("experience", companion.experience());
            entry.putInt("combat", companion.skills().combat());
            entry.putInt("carrying", companion.skills().carrying());
            entry.putInt("stamina", companion.skills().stamina());
            entry.putFloat("health", companion.health());
            entry.putInt("food", companion.food());
            entry.putLong("hunger", companion.hungerProgress());
            entry.putLong("starving", companion.starvingSince());
            entry.putInt("loyalty", companion.loyalty());
            ListTag notes = new ListTag();
            for (LoyaltyNote note : companion.notes()) {
                CompoundTag one = new CompoundTag();
                one.putString("reason", note.reason().id());
                one.putInt("change", note.change());
                one.putLong("time", note.time());
                notes.add(one);
            }
            entry.put("notes", notes);
            entry.putLong("kept", companion.keptProgress());
            entry.putLong("starve", companion.starveProgress());
            entry.putLong("warned", companion.warnedAt());
            entry.putUUID("branch", companion.branch());
            entry.putLong("price", companion.price());
            entry.putLong("wage", companion.wage());
            entry.putLong("paidDay", companion.paidDay());
            entry.putInt("unpaid", companion.unpaidDays());
            entry.putString("order", companion.order().id());
            companion.orderPos().ifPresent(pos -> entry.put("orderPos", writePos(pos)));
            companion.lastPos().ifPresent(pos -> entry.put("lastPos", writePos(pos)));
            entry.putLong("acquired", companion.acquired());
            entry.putInt("kills", companion.kills());
            entry.put("pack", writePack(packs.get(companion.id())));
            companions.add(entry);
        }
        tag.put(COMPANIONS, companions);
        ListTag markets = new ListTag();
        for (Map.Entry<UUID, CompanionState.Market> market : state.markets().entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("branch", market.getKey());
            entry.putLong("period", market.getValue().period());
            ListTag offers = new ListTag();
            for (Offer offer : market.getValue().offers()) {
                CompoundTag one = new CompoundTag();
                one.putLong("id", offer.id());
                one.putBoolean("slave", offer.slave());
                one.putInt("seed", offer.nameSeed());
                one.putString("gender", offer.gender().id());
                one.putInt("skin", offer.skin());
                one.putString("origin", offer.origin().id());
                one.putInt("level", offer.level());
                one.putInt("combat", offer.skills().combat());
                one.putInt("carrying", offer.skills().carrying());
                one.putInt("stamina", offer.skills().stamina());
                one.putLong("price", offer.price());
                offers.add(one);
            }
            entry.put("offers", offers);
            markets.add(entry);
        }
        tag.put(MARKETS, markets);
        ListTag closed = new ListTag();
        for (UUID kingdom : state.closedMarkets()) {
            closed.add(NbtUtils.createUUID(kingdom));
        }
        tag.put(CLOSED, closed);
        PartyState partyState = state.parties();
        ListTag parties = new ListTag();
        for (Party party : partyState.parties()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", party.id());
            entry.putUUID("leader", party.leader());
            ListTag members = new ListTag();
            ListTag shares = new ListTag();
            for (UUID member : party.members()) {
                members.add(NbtUtils.createUUID(member));
                CompoundTag share = new CompoundTag();
                share.putUUID("player", member);
                share.putInt("share", party.share(member));
                shares.add(share);
            }
            entry.put("members", members);
            entry.put("shares", shares);
            entry.putBoolean("even", party.evenSplit());
            entry.putBoolean("fire", party.friendlyFire());
            ListTag contracts = new ListTag();
            for (Map.Entry<Long, UUID> contract : party.contracts().entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putLong("id", contract.getKey());
                one.putUUID("holder", contract.getValue());
                contracts.add(one);
            }
            entry.put("contracts", contracts);
            parties.add(entry);
        }
        tag.put(PARTIES, parties);
        ListTag invites = new ListTag();
        for (PartyState.Invite invite : partyState.invites()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("party", invite.party());
            entry.putUUID("player", invite.player());
            entry.putUUID("from", invite.from());
            entry.putLong("expires", invite.expires());
            invites.add(entry);
        }
        tag.put(INVITES, invites);
        tag.putLong(NEXT_OFFER, state.nextOfferId());
        tag.putLong(NEXT_PARTY, partyState.nextPartyId());
    }

    private static ListTag writePack(SimpleContainer pack) {
        ListTag items = new ListTag();
        if (pack == null) {
            return items;
        }
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (!stack.isEmpty()) {
                CompoundTag one = new CompoundTag();
                one.putByte("Slot", (byte) slot);
                one.put("item", stack.save(new CompoundTag()));
                items.add(one);
            }
        }
        return items;
    }

    private static CompoundTag writePos(WorldPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", pos.dimension());
        tag.putInt("x", pos.x());
        tag.putInt("y", pos.y());
        tag.putInt("z", pos.z());
        return tag;
    }

    // ---------------------------------------------------------------- reading

    private static WorldPos readPos(CompoundTag tag) {
        return new WorldPos(tag.getString("dim"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("no " + key);
        }
        return tag.getUUID(key);
    }

    private static <T> T known(java.util.Optional<T> value, String what, String id) {
        return value.orElseThrow(() -> new IllegalArgumentException("unknown " + what + " '" + id + "'"));
    }

    /**
     * A list inside an entry: missing reads as empty, but a list of something else is damage, not an empty list.
     */
    private static ListTag typed(CompoundTag tag, String key, int type) {
        if (!tag.contains(key)) {
            return new ListTag();
        }
        if (!tag.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException(key + " is not a list");
        }
        ListTag raw = (ListTag) tag.get(key);
        if (!raw.isEmpty() && raw.getElementType() != type) {
            throw new IllegalArgumentException(key + " holds entries of another type");
        }
        return raw;
    }

    private static List<CompoundTag> list(CompoundTag tag, String key, List<String> problems) {
        List<CompoundTag> result = new ArrayList<>();
        if (!tag.contains(key)) {
            return result;
        }
        if (!tag.contains(key, Tag.TAG_LIST)) {
            problems.add(key + " is not a list");
            return result;
        }
        ListTag raw = (ListTag) tag.get(key);
        if (!raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) {
            problems.add(key + " holds entries that are not compounds");
            return result;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }

    /**
     * Reads a pack. An item no mod knows any more is a problem, so that it is not lost by saving.
     */
    static SimpleContainer readPack(ListTag items) {
        SimpleContainer pack = new SimpleContainer(Skills.MAX_SLOTS);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag one = items.getCompound(i);
            int slot = one.getByte("Slot") & 255;
            CompoundTag item = one.getCompound("item");
            String id = item.getString("id");
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                throw new IllegalArgumentException("unknown item '" + id + "' in the pack");
            }
            if (slot >= pack.getContainerSize()) {
                throw new IllegalArgumentException("pack slot " + slot + " out of range");
            }
            pack.setItem(slot, ItemStack.of(item));
        }
        return pack;
    }

    /**
     * Reads everything that can be read into empty state and packs.
     *
     * @return what could not be read
     */
    static List<String> read(CompoundTag tag, CompanionState state, Map<UUID, SimpleContainer> packs) {
        List<String> problems = new ArrayList<>();
        for (CompoundTag entry : list(tag, COMPANIONS, problems)) {
            try {
                Companion.Status status = known(Companion.Status.byId(entry.getString("status")), "status", entry.getString("status"));
                Gender gender = known(Gender.byId(entry.getString("gender")), "gender", entry.getString("gender"));
                Origin origin = known(Origin.byId(entry.getString("origin")), "origin", entry.getString("origin"));
                CompanionOrder order = known(CompanionOrder.byId(entry.getString("order")), "order", entry.getString("order"));
                Skills skills = new Skills(entry.getInt("combat"), entry.getInt("carrying"), entry.getInt("stamina"));
                Companion companion = new Companion(uuid(entry, "id"), uuid(entry, "owner"), status, entry.getInt("seed"), gender,
                        entry.getInt("skin"), origin, entry.getInt("level"), skills, uuid(entry, "branch"), entry.getLong("price"),
                        entry.getLong("wage"), entry.getLong("acquired"), entry.getLong("paidDay"), entry.getInt("loyalty"));
                List<LoyaltyNote> notes = new ArrayList<>();
                ListTag noteList = typed(entry, "notes", Tag.TAG_COMPOUND);
                for (int i = 0; i < noteList.size(); i++) {
                    CompoundTag note = noteList.getCompound(i);
                    notes.add(new LoyaltyNote(known(LoyaltyReason.byId(note.getString("reason")), "reason", note.getString("reason")),
                            note.getInt("change"), note.getLong("time")));
                }
                WorldPos orderPos = entry.contains("orderPos", Tag.TAG_COMPOUND) ? readPos(entry.getCompound("orderPos")) : null;
                if (order.atPlace() && orderPos == null) {
                    throw new IllegalArgumentException("order " + order.id() + " without a place");
                }
                companion.restore(entry.getLong("experience"), entry.getFloat("health"), entry.getInt("food"), entry.getLong("hunger"),
                        entry.getLong("starving"), entry.getInt("loyalty"), notes, entry.getLong("kept"), entry.getLong("starve"),
                        entry.getLong("warned"), entry.getLong("paidDay"), entry.getInt("unpaid"), order, orderPos,
                        entry.contains("lastPos", Tag.TAG_COMPOUND) ? readPos(entry.getCompound("lastPos")) : null, entry.getInt("kills"));
                SimpleContainer pack = readPack(typed(entry, "pack", Tag.TAG_COMPOUND));
                state.restoreCompanion(companion);
                packs.put(companion.id(), pack);
            } catch (RuntimeException e) {
                problems.add("companion " + (entry.hasUUID("id") ? entry.getUUID("id") : "?") + ": " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, MARKETS, problems)) {
            try {
                UUID branch = uuid(entry, "branch");
                List<Offer> offers = new ArrayList<>();
                ListTag list = typed(entry, "offers", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag one = list.getCompound(i);
                    offers.add(new Offer(one.getLong("id"), branch, one.getBoolean("slave"), one.getInt("seed"),
                            known(Gender.byId(one.getString("gender")), "gender", one.getString("gender")), one.getInt("skin"),
                            known(Origin.byId(one.getString("origin")), "origin", one.getString("origin")), one.getInt("level"),
                            new Skills(one.getInt("combat"), one.getInt("carrying"), one.getInt("stamina")), one.getLong("price")));
                }
                state.restoreMarket(branch, new CompanionState.Market(entry.getLong("period"), offers));
            } catch (RuntimeException e) {
                problems.add("market: " + e.getMessage());
            }
        }
        ListTag closedList;
        try {
            closedList = typed(tag, CLOSED, Tag.TAG_INT_ARRAY);
        } catch (RuntimeException e) {
            problems.add("closed markets: " + e.getMessage());
            closedList = new ListTag();
        }
        for (Tag id : closedList) {
            try {
                state.restoreClosed(NbtUtils.loadUUID(id));
            } catch (RuntimeException e) {
                problems.add("closed market: " + e.getMessage());
            }
        }
        PartyState parties = state.parties();
        for (CompoundTag entry : list(tag, PARTIES, problems)) {
            try {
                Party party = new Party(entry.getLong("id"), uuid(entry, "leader"));
                List<UUID> others = new ArrayList<>();
                for (Tag id : typed(entry, "members", Tag.TAG_INT_ARRAY)) {
                    UUID member = NbtUtils.loadUUID(id);
                    if (!member.equals(party.leader())) {
                        others.add(member);
                    }
                }
                Map<UUID, Integer> shares = new LinkedHashMap<>();
                ListTag shareList = typed(entry, "shares", Tag.TAG_COMPOUND);
                for (int i = 0; i < shareList.size(); i++) {
                    shares.put(uuid(shareList.getCompound(i), "player"), shareList.getCompound(i).getInt("share"));
                }
                Map<Long, UUID> contracts = new LinkedHashMap<>();
                ListTag contractList = typed(entry, "contracts", Tag.TAG_COMPOUND);
                for (int i = 0; i < contractList.size(); i++) {
                    contracts.put(contractList.getCompound(i).getLong("id"), uuid(contractList.getCompound(i), "holder"));
                }
                party.restore(others, shares, entry.getBoolean("even"), entry.getBoolean("fire"), contracts);
                parties.restoreParty(party);
            } catch (RuntimeException e) {
                problems.add("party #" + entry.getLong("id") + ": " + e.getMessage());
            }
        }
        for (CompoundTag entry : list(tag, INVITES, problems)) {
            try {
                parties.restoreInvite(new PartyState.Invite(entry.getLong("party"), uuid(entry, "player"), uuid(entry, "from"),
                        entry.getLong("expires")));
            } catch (RuntimeException e) {
                problems.add("invitation: " + e.getMessage());
            }
        }
        state.restoreNextOffer(tag.getLong(NEXT_OFFER));
        parties.restoreNextId(tag.getLong(NEXT_PARTY));
        return problems;
    }
}
