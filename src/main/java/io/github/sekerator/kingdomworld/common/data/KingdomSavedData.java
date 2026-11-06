package io.github.sekerator.kingdomworld.common.data;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * World-wide kingdom state, stored with the overworld in {@code <world>/data/kingdomworld_kingdoms.dat}.
 *
 * <p>The root tag carries {@value #TAG_FORMAT_VERSION}; older formats are upgraded by
 * {@link KingdomDataMigrations} on load and written back in the current format on the next save.
 */
public final class KingdomSavedData extends SavedData {
    public static final String DATA_NAME = KingdomWorld.MOD_ID + "_kingdoms";
    /** Current layout of the saved data. Increase it together with a migration step. */
    public static final int FORMAT_VERSION = 1;
    /** Format assumed for data without a version field. */
    static final int FIRST_FORMAT_VERSION = 1;

    static final String TAG_FORMAT_VERSION = "formatVersion";
    static final String TAG_KINGDOMS = "kingdoms";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final KingdomRegistry registry = new KingdomRegistry();

    public KingdomSavedData() {
        registry.setChangeListener(this::setDirty);
    }

    /**
     * The kingdom data of the given server. Must be called on the server thread.
     */
    public static KingdomSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(KingdomSavedData::load, KingdomSavedData::new, DATA_NAME);
    }

    public KingdomRegistry registry() {
        return registry;
    }

    public static KingdomSavedData load(CompoundTag tag) {
        int version = tag.contains(TAG_FORMAT_VERSION, Tag.TAG_INT) ? tag.getInt(TAG_FORMAT_VERSION) : FIRST_FORMAT_VERSION;
        CompoundTag data = tag;
        if (version > FORMAT_VERSION) {
            LOGGER.warn("Kingdom data uses format {} from a newer KingdomWorld (this version supports {}). "
                    + "Loading what is understood; unknown data will be lost when the world saves.", version, FORMAT_VERSION);
        } else if (version < FORMAT_VERSION) {
            LOGGER.info("Upgrading kingdom data from format {} to {}", version, FORMAT_VERSION);
            data = KingdomDataMigrations.upgrade(tag, version);
        }

        KingdomSavedData result = new KingdomSavedData();
        ListTag kingdoms = data.getList(TAG_KINGDOMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < kingdoms.size(); i++) {
            try {
                Kingdom kingdom = KingdomNbt.read(kingdoms.getCompound(i));
                KingdomRegistry.AddResult added = result.registry.add(kingdom);
                if (added != KingdomRegistry.AddResult.ADDED) {
                    LOGGER.warn("Skipping saved kingdom {} ({}): {}", kingdom.name(), kingdom.id(), added);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Skipping unreadable saved kingdom #{}: {}", i, e.getMessage());
            }
        }
        // Freshly loaded data only needs saving if it was converted from another format.
        result.setDirty(version != FORMAT_VERSION);
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(TAG_FORMAT_VERSION, FORMAT_VERSION);
        ListTag kingdoms = new ListTag();
        for (Kingdom kingdom : registry.all()) {
            kingdoms.add(KingdomNbt.write(kingdom));
        }
        tag.put(TAG_KINGDOMS, kingdoms);
        return tag;
    }
}
