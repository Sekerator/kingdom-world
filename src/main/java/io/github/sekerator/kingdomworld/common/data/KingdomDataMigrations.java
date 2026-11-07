package io.github.sekerator.kingdomworld.common.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Upgrades saved kingdom data written by older versions of the mod to {@link KingdomSavedData#FORMAT_VERSION}.
 *
 * <p>To change the format: bump {@code FORMAT_VERSION}, then add a {@code case} for the previous version that
 * rewrites the tag into the new layout. Steps run one after another, so data of any older version is upgraded
 * through every intermediate format. Never edit a step once it has been released.
 *
 * <p>A step that meets data it cannot convert throws {@link IllegalArgumentException}; the caller then keeps
 * the original data read-only instead of losing it.
 */
final class KingdomDataMigrations {

    private KingdomDataMigrations() {
    }

    /**
     * @param tag         saved data in format {@code fromVersion}; may be modified in place
     * @param fromVersion format the tag was written in, not newer than the current format
     * @return the data in the current format
     */
    static CompoundTag upgrade(CompoundTag tag, int fromVersion) {
        CompoundTag current = tag;
        for (int version = fromVersion; version < KingdomSavedData.FORMAT_VERSION; version++) {
            switch (version) {
                case 1 -> current = upgradeFrom1To2(current);
                case 2 -> current = upgradeFrom2To3(current);
                case 3 -> current = upgradeFrom3To4(current);
                default -> throw new IllegalArgumentException("No migration from kingdom data format " + version);
            }
        }
        return current;
    }

    /**
     * Format 2 adds land, members, rights and pending requests to every kingdom. A format 1 kingdom has none of
     * them yet; its ruler becomes the member holding the king's position.
     */
    private static CompoundTag upgradeFrom1To2(CompoundTag tag) {
        ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            CompoundTag kingdom = kingdoms.getCompound(i);
            if (kingdom.hasUUID(KingdomNbt.RULER) && !kingdom.contains(KingdomNbt.MEMBERS)) {
                CompoundTag king = new CompoundTag();
                king.putUUID(KingdomNbt.PLAYER, kingdom.getUUID(KingdomNbt.RULER));
                king.putString(KingdomNbt.ROLE, "king");
                king.putLong(KingdomNbt.JOINED, kingdom.getLong(KingdomNbt.CREATED_GAME_TIME));
                ListTag members = new ListTag();
                members.add(king);
                kingdom.put(KingdomNbt.MEMBERS, members);
            }
        }
        tag.putInt(KingdomSavedData.TAG_FORMAT_VERSION, 2);
        return tag;
    }

    /**
     * Format 3 adds citizens, their places, the chronicle and storehouses; a format 2 kingdom has none of them.
     * The new right to manage citizens goes to the steward, as it does in new kingdoms; rights tables the king
     * edited stay as they are otherwise.
     */
    private static CompoundTag upgradeFrom2To3(CompoundTag tag) {
        ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            CompoundTag kingdom = kingdoms.getCompound(i);
            if (!kingdom.contains(KingdomNbt.PERMISSIONS, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag rights = kingdom.getCompound(KingdomNbt.PERMISSIONS);
            if (!rights.contains("steward", Tag.TAG_LIST)) {
                continue;
            }
            ListTag steward = rights.getList("steward", Tag.TAG_STRING);
            boolean present = false;
            for (int j = 0; j < steward.size(); j++) {
                present |= "manage_citizens".equals(steward.getString(j));
            }
            if (!present) {
                steward.add(StringTag.valueOf("manage_citizens"));
            }
        }
        tag.putInt(KingdomSavedData.TAG_FORMAT_VERSION, 3);
        return tag;
    }

    /**
     * Format 4 adds work posts and the record of production to every population, the state of each citizen's work
     * and the tools and armor citizens carry. A format 3 population has no posts, nothing produced on record and
     * citizens without a work state; nobody carries anything yet.
     */
    private static CompoundTag upgradeFrom3To4(CompoundTag tag) {
        ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            CompoundTag kingdom = kingdoms.getCompound(i);
            if (!kingdom.contains(CitizenNbt.POPULATION, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag population = kingdom.getCompound(CitizenNbt.POPULATION);
            if (!population.contains(CitizenNbt.POSTS)) {
                population.put(CitizenNbt.POSTS, new ListTag());
            }
            if (!population.contains(CitizenNbt.PRODUCTION)) {
                population.put(CitizenNbt.PRODUCTION, new ListTag());
            }
            ListTag citizens = population.getList("citizens", Tag.TAG_COMPOUND);
            for (int j = 0; j < citizens.size(); j++) {
                CompoundTag citizen = citizens.getCompound(j);
                if (!citizen.contains("workStatus")) {
                    citizen.putString("workStatus", "none");
                }
            }
        }
        if (!tag.contains(KingdomSavedData.TAG_GEAR)) {
            tag.put(KingdomSavedData.TAG_GEAR, new ListTag());
        }
        tag.putInt(KingdomSavedData.TAG_FORMAT_VERSION, 4);
        return tag;
    }
}
