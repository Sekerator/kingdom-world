package io.github.sekerator.kingdomworld.common.flag;

import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * NBT form of the flag of a kingdom, kept with the kingdom under {@link #FLAG}:
 *
 * <pre>
 * { base: "blue", layers: [ { pattern: "minecraft:cross", color: "white" }, ... ] }
 * </pre>
 *
 * <p>Colours are kept by the names of the dyes and patterns by their ids, so that the saved flag reads the same whatever the
 * game numbers them. Reading never throws: a flag that cannot be understood, or whose field has an unknown colour, is read as
 * none (the flag of the colour of the kingdom); a layer with an unknown colour or an id of a wrong form is dropped, and layers
 * beyond {@link KingdomFlag#MAX_LAYERS} are cut off. A pattern of the right form the game does not know is kept: it is not
 * drawn, and goes when the king saves the flag again.
 */
public final class FlagNbt {
    public static final String FLAG = "flag";
    static final String BASE = "base";
    static final String LAYERS = "layers";
    static final String PATTERN = "pattern";
    static final String COLOR = "color";

    private FlagNbt() {
    }

    public static CompoundTag write(KingdomFlag flag) {
        CompoundTag tag = new CompoundTag();
        tag.putString(BASE, KingdomColors.dyeName(flag.base()));
        ListTag layers = new ListTag();
        for (KingdomFlag.Layer layer : flag.layers()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(PATTERN, layer.pattern());
            entry.putString(COLOR, KingdomColors.dyeName(layer.dye()));
            layers.add(entry);
        }
        tag.put(LAYERS, layers);
        return tag;
    }

    /**
     * Keeps a flag in the tag of its kingdom; nothing for the flag of its colour.
     */
    public static void put(CompoundTag kingdom, @Nullable KingdomFlag flag) {
        if (flag != null) {
            kingdom.put(FLAG, write(flag));
        }
    }

    /**
     * The flag kept in the tag of a kingdom, or {@code null} for none or one that cannot be understood.
     */
    @Nullable
    public static KingdomFlag get(CompoundTag kingdom) {
        return kingdom.contains(FLAG) ? read(kingdom.get(FLAG)) : null;
    }

    /**
     * Reads a flag, or {@code null} for one that cannot be understood.
     */
    @Nullable
    public static KingdomFlag read(@Nullable Tag raw) {
        if (!(raw instanceof CompoundTag tag) || !tag.contains(BASE, Tag.TAG_STRING)) {
            return null;
        }
        int base = KingdomColors.dyeIndex(tag.getString(BASE));
        if (base < 0) {
            return null;
        }
        List<KingdomFlag.Layer> layers = new ArrayList<>();
        if (tag.contains(LAYERS)) {
            if (!(tag.get(LAYERS) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
                return null;
            }
            for (int i = 0; i < list.size() && layers.size() < KingdomFlag.MAX_LAYERS; i++) {
                CompoundTag entry = list.getCompound(i);
                String pattern = entry.getString(PATTERN);
                int dye = KingdomColors.dyeIndex(entry.getString(COLOR));
                if (dye >= 0 && KingdomFlag.validPattern(pattern)) {
                    layers.add(new KingdomFlag.Layer(pattern, dye));
                }
            }
        }
        return new KingdomFlag(base, layers);
    }
}
