package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

/**
 * NBT form of {@link Kingdom}.
 */
public final class KingdomNbt {
    static final String ID = "id";
    static final String NAME = "name";
    static final String COLOR = "color";
    static final String RULER = "ruler";
    static final String CREATED_GAME_TIME = "createdGameTime";

    private KingdomNbt() {
    }

    public static CompoundTag write(Kingdom kingdom) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, kingdom.id());
        tag.putString(NAME, kingdom.name());
        tag.putInt(COLOR, kingdom.color());
        if (kingdom.rulerId() != null) {
            tag.putUUID(RULER, kingdom.rulerId());
        }
        tag.putLong(CREATED_GAME_TIME, kingdom.createdGameTime());
        return tag;
    }

    /**
     * @throws IllegalArgumentException if the tag does not describe a valid kingdom
     */
    public static Kingdom read(CompoundTag tag) {
        if (!tag.hasUUID(ID)) {
            throw new IllegalArgumentException("Missing kingdom id");
        }
        if (!tag.contains(NAME, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing kingdom name");
        }
        UUID ruler = tag.hasUUID(RULER) ? tag.getUUID(RULER) : null;
        return new Kingdom(tag.getUUID(ID), tag.getString(NAME), tag.getInt(COLOR), ruler, tag.getLong(CREATED_GAME_TIME));
    }
}
