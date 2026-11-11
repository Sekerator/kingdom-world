package io.github.sekerator.kingdomworld.common.economy;

import io.github.sekerator.kingdomworld.KingdomWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The money set aside for the houses of citizens from their wages until the houses are looked at, stored with the
 * overworld in {@code <world>/data/kingdomworld_households.dat}. The file is small and nothing depends on it: money in it
 * has left the treasury already, so a purse that cannot be read is simply gone, as wages were before houses kept anything.
 */
public final class HouseholdSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_households";
    /** Current layout of the saved data. */
    public static final int FORMAT_VERSION = 1;

    /**
     * The money set aside for one house.
     */
    public record Purse(UUID kingdomId, long amount) {
    }

    private final Map<Long, Purse> purses = new LinkedHashMap<>();

    public static HouseholdSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(HouseholdSavedData::load, HouseholdSavedData::new, DATA_NAME);
    }

    /**
     * The purses by the id of the building they belong to.
     */
    public Map<Long, Purse> purses() {
        return Collections.unmodifiableMap(purses);
    }

    public long amount(long building) {
        Purse purse = purses.get(building);
        return purse == null ? 0 : purse.amount();
    }

    /**
     * Sets what is set aside for a house; nothing forgets the house.
     */
    public void set(long building, UUID kingdomId, long amount) {
        if (amount <= 0) {
            if (purses.remove(building) != null) {
                setDirty();
            }
            return;
        }
        Purse before = purses.put(building, new Purse(kingdomId, amount));
        if (before == null || before.amount() != amount) {
            setDirty();
        }
    }

    public static HouseholdSavedData load(CompoundTag tag) {
        HouseholdSavedData data = new HouseholdSavedData();
        ListTag list = tag.getList("purses", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag purse = list.getCompound(i);
            if (purse.hasUUID("kingdom") && purse.getLong("amount") > 0) {
                data.purses.put(purse.getLong("building"), new Purse(purse.getUUID("kingdom"), purse.getLong("amount")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("formatVersion", FORMAT_VERSION);
        ListTag list = new ListTag();
        purses.forEach((building, purse) -> {
            CompoundTag one = new CompoundTag();
            one.putLong("building", building);
            one.putUUID("kingdom", purse.kingdomId());
            one.putLong("amount", purse.amount());
            list.add(one);
        });
        tag.put("purses", list);
        return tag;
    }
}
