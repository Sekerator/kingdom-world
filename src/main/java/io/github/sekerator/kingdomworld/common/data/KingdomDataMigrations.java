package io.github.sekerator.kingdomworld.common.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Upgrades saved kingdom data written by older versions of the mod to {@link KingdomSavedData#FORMAT_VERSION}.
 *
 * <p>To change the format: bump {@code FORMAT_VERSION}, then add a {@code case} for the previous version that
 * rewrites the tag into the new layout. Steps run one after another, so data of any older version is upgraded
 * through every intermediate format. Never edit a step once it has been released.
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
                // case 1 -> current = upgradeFrom1To2(current);
                default -> throw new IllegalStateException("No migration from kingdom data format " + version);
            }
        }
        return current;
    }
}
