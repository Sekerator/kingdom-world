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
                case 4 -> current = upgradeFrom4To5(current);
                case 5 -> current = upgradeFrom5To6(current);
                case 6 -> current = upgradeFrom6To7(current);
                case 7 -> current = upgradeFrom7To8(current);
                case 8 -> current = upgradeFrom8To9(current);
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

    /**
     * Format 5 adds the king's orders to every kingdom and the auto mode and workstations to every work post; a
     * format 4 kingdom has no orders and its posts work on their own as before. The new right to manage production
     * goes to the steward, as it does in new kingdoms.
     */
    private static CompoundTag upgradeFrom4To5(CompoundTag tag) {
        ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            CompoundTag kingdom = kingdoms.getCompound(i);
            grant(kingdom, "steward", "manage_production");
            if (kingdom.contains(CitizenNbt.POPULATION, Tag.TAG_COMPOUND)) {
                ListTag posts = kingdom.getCompound(CitizenNbt.POPULATION).getList(CitizenNbt.POSTS, Tag.TAG_COMPOUND);
                for (int j = 0; j < posts.size(); j++) {
                    CompoundTag post = posts.getCompound(j);
                    if (!post.contains("auto")) {
                        post.putBoolean("auto", true);
                    }
                }
            }
        }
        tag.putInt(KingdomSavedData.TAG_FORMAT_VERSION, 5);
        return tag;
    }

    /**
     * Format 6 adds money: a treasury to every kingdom, the pay of every citizen, the accounts of the players and the
     * old currencies. A format 5 kingdom gets an empty treasury with the default coin values when it is read, its first
     * payday only starts the count, and its citizens expect no pay until then; nobody has an account yet. The new right
     * to manage the treasury goes to the treasurer, as it does in new kingdoms.
     */
    private static CompoundTag upgradeFrom5To6(CompoundTag tag) {
        ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            grant(kingdoms.getCompound(i), "treasurer", "manage_treasury");
        }
        tag.putInt(KingdomSavedData.TAG_FORMAT_VERSION, 6);
        return tag;
    }

    /**
     * Format 7 adds trade: markets, deals, caravans and goods going back to players at the root, reserves of other
     * currencies in every treasury, exchange rates, payments in escrow and the coins in hands. A format 6 world has no
     * market yet, no deals and no reserves, and every currency starts at the rate of one; nothing else changes. The new
     * right to manage the market goes to the treasurer and the steward, the right to make deals with other kingdoms to the
     * treasurer and the chancellor, as in new kingdoms.
     */
    private static CompoundTag upgradeFrom6To7(CompoundTag tag) {
        ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            CompoundTag kingdom = kingdoms.getCompound(i);
            grant(kingdom, "treasurer", "manage_market");
            grant(kingdom, "steward", "manage_market");
            grant(kingdom, "treasurer", "manage_trade");
            grant(kingdom, "chancellor", "manage_trade");
        }
        tag.putInt(KingdomSavedData.TAG_FORMAT_VERSION, 7);
        return tag;
    }

    /**
     * Upgrades that do not need a format of their own, by name: new rights that positions get once. Data written by this
     * version lists every upgrade it has; data without one gets it on load. Applied after the format migrations.
     */
    static final java.util.List<String> UPGRADES = java.util.List.of("guild_rights", "army_rights", "magic_rights");

    /**
     * Applies the upgrades the data does not list yet.
     *
     * @return whether anything was applied
     */
    static boolean applyUpgrades(CompoundTag tag) {
        ListTag done = tag.getList(KingdomSavedData.TAG_UPGRADES, Tag.TAG_STRING);
        java.util.Set<String> applied = new java.util.HashSet<>();
        for (int i = 0; i < done.size(); i++) {
            applied.add(done.getString(i));
        }
        boolean changed = false;
        if (!applied.contains("guild_rights")) {
            // The right to manage the guild goes to the marshal, as it does in new kingdoms.
            ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < kingdoms.size(); i++) {
                grant(kingdoms.getCompound(i), "marshal", "manage_guild");
            }
            changed = true;
        }
        if (!applied.contains("army_rights")) {
            // The right to run the army goes to the marshal, as it does in new kingdoms.
            ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < kingdoms.size(); i++) {
                grant(kingdoms.getCompound(i), "marshal", "manage_army");
            }
            changed = true;
        }
        if (!applied.contains("magic_rights")) {
            // The right to run the mage tower goes to the court mage, as it does in new kingdoms.
            ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < kingdoms.size(); i++) {
                grant(kingdoms.getCompound(i), "court_mage", "manage_magic");
            }
            changed = true;
        }
        return changed;
    }

    /**
     * Format 8 adds construction: the constructions under way and the register of finished buildings at the root, both
     * empty for a format 7 world. The new right to manage construction goes to the steward, as in new kingdoms.
     */
    private static CompoundTag upgradeFrom7To8(CompoundTag tag) {
        ListTag kingdoms = tag.getList(KingdomSavedData.TAG_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            grant(kingdoms.getCompound(i), "steward", "manage_construction");
        }
        if (!tag.contains(KingdomSavedData.TAG_CONSTRUCTION)) {
            tag.put(KingdomSavedData.TAG_CONSTRUCTION, ConstructionNbt.empty());
        }
        tag.putInt(KingdomSavedData.TAG_FORMAT_VERSION, 8);
        return tag;
    }

    /**
     * Format 9 adds the roles and levels of buildings, repairs by themselves, what was found damaged, the kinds of
     * constructions (upgrades, repairs, demolitions), the wood and stone they are built of, and the blueprints kingdoms
     * saved. A building of format 8 plays the role its type names, at level 1; constructions build new buildings; no
     * kingdom has saved blueprints. Saving blueprints needs the right to manage construction, so no right changes.
     */
    private static CompoundTag upgradeFrom8To9(CompoundTag tag) {
        if (!tag.contains(KingdomSavedData.TAG_CONSTRUCTION)) {
            tag.put(KingdomSavedData.TAG_CONSTRUCTION, ConstructionNbt.empty());
        } else if (tag.get(KingdomSavedData.TAG_CONSTRUCTION) instanceof CompoundTag construction) {
            ConstructionNbt.upgradeFrom8(construction);
        } else {
            throw new IllegalArgumentException("'" + KingdomSavedData.TAG_CONSTRUCTION + "' is not a compound");
        }
        tag.putInt(KingdomSavedData.TAG_FORMAT_VERSION, 9);
        return tag;
    }

    /**
     * Adds a right to a position whose rights the kingdom saved, unless it holds it already.
     */
    private static void grant(CompoundTag kingdom, String role, String right) {
        if (!kingdom.contains(KingdomNbt.PERMISSIONS, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag rights = kingdom.getCompound(KingdomNbt.PERMISSIONS);
        if (!rights.contains(role, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = rights.getList(role, Tag.TAG_STRING);
        for (int j = 0; j < list.size(); j++) {
            if (right.equals(list.getString(j))) {
                return;
            }
        }
        list.add(StringTag.valueOf(right));
    }
}
