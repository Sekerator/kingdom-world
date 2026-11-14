package io.github.sekerator.kingdomworld.client.flag;

import com.mojang.datafixers.util.Pair;
import io.github.sekerator.kingdomworld.common.flag.FlagPatterns;
import io.github.sekerator.kingdomworld.common.network.packet.FlagPackets;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The flags of all kingdoms as the server told them, with the layers of each ready to draw. A kingdom the client was not told of
 * yet is drawn as a white banner.
 */
public final class ClientFlags {
    private static final Map<UUID, FlagPackets.Entry> ENTRIES = new HashMap<>();
    private static final Map<UUID, List<Pair<Holder<BannerPattern>, DyeColor>>> LAYERS = new HashMap<>();
    private static List<Pair<Holder<BannerPattern>, DyeColor>> unknown;
    /** Grows with every change, so that screens know to look again. */
    private static long version;

    private ClientFlags() {
    }

    /**
     * Takes in what the server told: a full directory replaces everything known before.
     */
    public static void apply(FlagPackets.Directory packet) {
        if (packet.full()) {
            ENTRIES.clear();
            LAYERS.clear();
        }
        for (FlagPackets.Entry entry : packet.entries()) {
            ENTRIES.put(entry.kingdom(), entry);
            LAYERS.put(entry.kingdom(), layers(entry.flag()));
        }
        for (UUID id : packet.removed()) {
            ENTRIES.remove(id);
            LAYERS.remove(id);
        }
        version++;
    }

    public static void clear() {
        ENTRIES.clear();
        LAYERS.clear();
        unknown = null;
        version++;
    }

    public static long version() {
        return version;
    }

    /**
     * The flag a kingdom flies, if the server told it.
     */
    public static Optional<KingdomFlag> flag(UUID kingdom) {
        return Optional.ofNullable(ENTRIES.get(kingdom)).map(FlagPackets.Entry::flag);
    }

    /**
     * Every flag the server told.
     */
    public static Collection<FlagPackets.Entry> all() {
        return List.copyOf(ENTRIES.values());
    }

    /**
     * The layers of the flag of a kingdom ready to draw; a white banner for a kingdom not told of.
     */
    public static List<Pair<Holder<BannerPattern>, DyeColor>> layers(UUID kingdom) {
        List<Pair<Holder<BannerPattern>, DyeColor>> layers = kingdom == null ? null : LAYERS.get(kingdom);
        if (layers != null) {
            return layers;
        }
        if (unknown == null) {
            unknown = layers(KingdomFlag.plain(DyeColor.WHITE.getId()));
        }
        return unknown;
    }

    /**
     * The layers of a flag ready to draw: the field first, then every pattern the game knows; a pattern it does not know (of a
     * mod taken out) is left out.
     */
    public static List<Pair<Holder<BannerPattern>, DyeColor>> layers(KingdomFlag flag) {
        List<Pair<Holder<BannerPattern>, DyeColor>> layers = new ArrayList<>(flag.layers().size() + 1);
        Holder<BannerPattern> base = FlagPatterns.base();
        if (base != null) {
            layers.add(Pair.of(base, DyeColor.byId(flag.base())));
        }
        for (KingdomFlag.Layer layer : flag.layers()) {
            Holder<BannerPattern> pattern = FlagPatterns.holder(layer.pattern());
            if (pattern != null) {
                layers.add(Pair.of(pattern, DyeColor.byId(layer.dye())));
            }
        }
        return List.copyOf(layers);
    }
}
