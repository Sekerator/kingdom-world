package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.core.law.Confiscation;
import io.github.sekerator.kingdomworld.core.law.Crime;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.KingdomLaw;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the law to NBT and reads it back.
 *
 * <pre>
 * root: formatVersion,
 *       wanted: [ { kingdom, player, name, since, fine, commuted, bounty, crimes: [ { kind, worth, time, victim, count } ] } ],
 *       confiscations: [ { id, kingdom, kingdomName, owner, ownerName, time, day, worth, items, goods: [ item ] } ], nextConfiscation,
 *       reputation: [ { kingdom, player, value } ], trade: [ { kingdom, player, money } ], passes: [ { kingdom, player, until } ],
 *       laws: [ { kingdom, multiplier, entryFee, slavery, bounty } ], autoPay: [ player ], captures: [ { hunter, player, count } ]
 * </pre>
 *
 * A record that cannot be read is a problem: the data stays read-only, so that saving loses nothing. An item no mod knows any
 * more among confiscated goods is a problem too.
 */
final class LawNbt {
    private static final String WANTED = "wanted";
    private static final String CRIMES = "crimes";
    private static final String CONFISCATIONS = "confiscations";
    private static final String NEXT_CONFISCATION = "nextConfiscation";
    private static final String GOODS = "goods";
    private static final String REPUTATION = "reputation";
    private static final String TRADE = "trade";
    private static final String PASSES = "passes";
    private static final String LAWS = "laws";
    private static final String AUTO_PAY = "autoPay";
    private static final String CAPTURES = "captures";
    private static final String KINGDOM = "kingdom";
    private static final String PLAYER = "player";

    private LawNbt() {
    }

    static void write(LawState state, Map<Long, List<ItemStack>> goods, CompoundTag root) {
        ListTag wanted = new ListTag();
        for (Wanted entry : state.allWanted()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(KINGDOM, entry.kingdomId());
            tag.putUUID(PLAYER, entry.player());
            tag.putString("name", entry.name());
            tag.putLong("since", entry.since());
            tag.putLong("fine", entry.fineOverride());
            tag.putBoolean("commuted", entry.commuted());
            tag.putLong("bounty", entry.bounty());
            ListTag crimes = new ListTag();
            for (Crime crime : entry.crimes()) {
                CompoundTag one = new CompoundTag();
                one.putString("kind", crime.kind().id());
                one.putLong("worth", crime.worth());
                one.putLong("time", crime.time());
                one.putString("victim", crime.victim());
                one.putInt("count", crime.count());
                crimes.add(one);
            }
            tag.put(CRIMES, crimes);
            wanted.add(tag);
        }
        root.put(WANTED, wanted);
        ListTag confiscations = new ListTag();
        for (Confiscation confiscation : state.confiscations()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("id", confiscation.id());
            tag.putUUID(KINGDOM, confiscation.kingdomId());
            tag.putString("kingdomName", confiscation.kingdomName());
            tag.putUUID("owner", confiscation.owner());
            tag.putString("ownerName", confiscation.ownerName());
            tag.putLong("time", confiscation.time());
            tag.putLong("day", confiscation.day());
            tag.putLong("worth", confiscation.worth());
            tag.putInt("items", confiscation.items());
            ListTag items = new ListTag();
            for (ItemStack stack : goods.getOrDefault(confiscation.id(), List.of())) {
                if (!stack.isEmpty()) {
                    items.add(stack.save(new CompoundTag()));
                }
            }
            tag.put(GOODS, items);
            confiscations.add(tag);
        }
        root.put(CONFISCATIONS, confiscations);
        root.putLong(NEXT_CONFISCATION, state.nextConfiscationId());
        ListTag reputation = new ListTag();
        state.reputations().forEach((kingdom, of) -> of.forEach((player, value) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(KINGDOM, kingdom);
            tag.putUUID(PLAYER, player);
            tag.putInt("value", value);
            reputation.add(tag);
        }));
        root.put(REPUTATION, reputation);
        ListTag trade = new ListTag();
        state.tradeProgress().forEach((kingdom, of) -> of.forEach((player, money) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(KINGDOM, kingdom);
            tag.putUUID(PLAYER, player);
            tag.putLong("money", money);
            trade.add(tag);
        }));
        root.put(TRADE, trade);
        ListTag passes = new ListTag();
        state.passes().forEach((kingdom, of) -> of.forEach((player, until) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(KINGDOM, kingdom);
            tag.putUUID(PLAYER, player);
            tag.putLong("until", until);
            passes.add(tag);
        }));
        root.put(PASSES, passes);
        ListTag laws = new ListTag();
        state.laws().forEach((kingdom, law) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(KINGDOM, kingdom);
            tag.putInt("multiplier", law.fineMultiplierPermille());
            tag.putLong("entryFee", law.entryFee());
            tag.putBoolean("slavery", law.slaveryBanned());
            tag.putLong("bounty", law.bountyReward());
            laws.add(tag);
        });
        root.put(LAWS, laws);
        ListTag autoPay = new ListTag();
        for (UUID player : state.autoPayers()) {
            autoPay.add(NbtUtils.createUUID(player));
        }
        root.put(AUTO_PAY, autoPay);
        ListTag captures = new ListTag();
        state.allCaptures().forEach((hunter, of) -> of.forEach((player, count) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("hunter", hunter);
            tag.putUUID("player", player);
            tag.putInt("count", count);
            captures.add(tag);
        }));
        root.put(CAPTURES, captures);
    }

    /**
     * Reads what can be read into empty state and goods.
     *
     * @return problems found; empty when everything was read
     */
    static List<String> read(CompoundTag root, LawState state, Map<Long, List<ItemStack>> goods) {
        List<String> problems = new ArrayList<>();
        for (CompoundTag tag : compounds(root, WANTED, problems)) {
            try {
                Wanted entry = new Wanted(tag.getUUID(KINGDOM), tag.getUUID(PLAYER), tag.getString("name"), tag.getLong("since"));
                List<Crime> crimes = new ArrayList<>();
                for (CompoundTag one : compounds(tag, CRIMES, problems)) {
                    CrimeKind kind = CrimeKind.byId(one.getString("kind"))
                            .orElseThrow(() -> new IllegalArgumentException("unknown crime '" + one.getString("kind") + "'"));
                    crimes.add(new Crime(kind, one.getLong("worth"), one.getLong("time"), one.getString("victim"), one.getInt("count")));
                }
                state.restoreWanted(entry, crimes, tag.getLong("since"), tag.getLong("fine"), tag.getBoolean("commuted"), tag.getLong("bounty"));
            } catch (RuntimeException e) {
                problems.add("wanted record: " + e.getMessage());
            }
        }
        for (CompoundTag tag : compounds(root, CONFISCATIONS, problems)) {
            try {
                List<ItemStack> stacks = new ArrayList<>();
                for (CompoundTag item : compounds(tag, GOODS, problems)) {
                    String id = item.getString("id");
                    ResourceLocation key = ResourceLocation.tryParse(id);
                    if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                        throw new IllegalArgumentException("unknown item '" + id + "' among confiscated goods");
                    }
                    stacks.add(ItemStack.of(item));
                }
                Confiscation confiscation = new Confiscation(tag.getLong("id"), tag.getUUID(KINGDOM), tag.getString("kingdomName"),
                        tag.getUUID("owner"), tag.getString("ownerName"), tag.getLong("time"), tag.getLong("day"), tag.getLong("worth"),
                        tag.getInt("items"));
                state.restoreConfiscation(confiscation);
                goods.put(confiscation.id(), stacks);
            } catch (RuntimeException e) {
                problems.add("confiscation: " + e.getMessage());
            }
        }
        state.restoreNextConfiscation(root.getLong(NEXT_CONFISCATION));
        for (CompoundTag tag : compounds(root, REPUTATION, problems)) {
            try {
                state.restoreReputation(tag.getUUID(KINGDOM), tag.getUUID(PLAYER), tag.getInt("value"));
            } catch (RuntimeException e) {
                problems.add("reputation: " + e.getMessage());
            }
        }
        for (CompoundTag tag : compounds(root, TRADE, problems)) {
            try {
                state.restoreTrade(tag.getUUID(KINGDOM), tag.getUUID(PLAYER), tag.getLong("money"));
            } catch (RuntimeException e) {
                problems.add("trade: " + e.getMessage());
            }
        }
        for (CompoundTag tag : compounds(root, PASSES, problems)) {
            try {
                state.restorePass(tag.getUUID(KINGDOM), tag.getUUID(PLAYER), tag.getLong("until"));
            } catch (RuntimeException e) {
                problems.add("pass: " + e.getMessage());
            }
        }
        for (CompoundTag tag : compounds(root, LAWS, problems)) {
            try {
                state.restoreLaw(tag.getUUID(KINGDOM), new KingdomLaw(tag.getInt("multiplier"), tag.getLong("entryFee"), tag.getBoolean("slavery"),
                        tag.getLong("bounty")));
            } catch (RuntimeException e) {
                problems.add("court: " + e.getMessage());
            }
        }
        if (root.contains(AUTO_PAY)) {
            ListTag autoPay = root.getList(AUTO_PAY, Tag.TAG_INT_ARRAY);
            for (Tag tag : autoPay) {
                try {
                    state.restoreAutoPay(NbtUtils.loadUUID(tag));
                } catch (RuntimeException e) {
                    problems.add("auto pay: " + e.getMessage());
                }
            }
        }
        for (CompoundTag tag : compounds(root, CAPTURES, problems)) {
            try {
                state.restoreCaptures(tag.getUUID("hunter"), tag.getUUID("player"), tag.getInt("count"));
            } catch (RuntimeException e) {
                problems.add("captures: " + e.getMessage());
            }
        }
        return problems;
    }

    private static List<CompoundTag> compounds(CompoundTag tag, String key, List<String> problems) {
        List<CompoundTag> result = new ArrayList<>();
        if (!tag.contains(key)) {
            return result;
        }
        if (!(tag.get(key) instanceof ListTag list)) {
            problems.add(key + " is not a list");
            return result;
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            problems.add(key + " holds entries that are not compounds");
            return result;
        }
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getCompound(i));
        }
        return result;
    }
}
