package io.github.sekerator.kingdomworld.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.util.ManaUtil;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.magic.MagicWells;
import io.github.sekerator.kingdomworld.compat.CompatModule;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Integration with Ars Nouveau: a magic focus fills the source jars (and any other block that takes source) nearby and
 * refills the mana of Ars Nouveau of subjects nearby, for the energy of their kingdom. Source and mana go through the API of
 * Ars Nouveau ({@link ISourceTile}, {@link IManaCap}). This is the only package allowed to reference classes of Ars Nouveau.
 */
public final class ArsNouveauCompat implements CompatModule {
    public static final String MOD_ID = "ars_nouveau";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void init() {
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        MagicWells.register(new Mana());
        MagicWells.register(new Source());
        LOGGER.info("Ars Nouveau {} detected, magic foci fill its source jars and refill its mana for the energy of kingdoms", version);
    }

    private static Optional<IManaCap> mana(ServerPlayer player) {
        return CapabilityRegistry.getMana(player).resolve();
    }

    private static int max(ServerPlayer player, IManaCap cap) {
        int max = cap.getMaxMana();
        if (max <= 0) {
            // A player Ars Nouveau has not ticked yet: its maximum is not known to the capability.
            max = ManaUtil.getMaxMana(player);
            cap.setMaxMana(max);
        }
        return max;
    }

    private static final class Mana implements MagicWells.ManaWell {
        @Override
        public String name() {
            return "Ars Nouveau mana";
        }

        @Override
        public boolean enabled(MagicRules rules) {
            return rules.arsWell;
        }

        @Override
        public long need(ServerPlayer player, MagicRules rules) {
            Optional<IManaCap> cap = mana(player);
            if (cap.isEmpty()) {
                return 0;
            }
            double missing = max(player, cap.get()) - cap.get().getCurrentMana();
            return missing <= 0.0 ? 0 : (long) Math.ceil(missing / rules.arsManaPerEnergy);
        }

        @Override
        public double give(ServerPlayer player, long units, MagicRules rules) {
            Optional<IManaCap> cap = mana(player);
            if (cap.isEmpty()) {
                return 0.0;
            }
            double before = cap.get().getCurrentMana();
            double room = Math.max(0.0, max(player, cap.get()) - before);
            cap.get().addMana(Math.min(room, (double) units * rules.arsManaPerEnergy));
            return Math.max(0.0, cap.get().getCurrentMana() - before);
        }
    }

    private static final class Source implements MagicWells.SourceWell {
        @Override
        public String name() {
            return "Ars Nouveau source";
        }

        @Override
        public boolean enabled(MagicRules rules) {
            return rules.arsWell;
        }

        @Override
        public boolean accepts(BlockEntity entity) {
            return entity instanceof ISourceTile tile && tile.canAcceptSource();
        }

        @Override
        public long need(BlockEntity entity, MagicRules rules) {
            ISourceTile tile = (ISourceTile) entity;
            int missing = tile.getMaxSource() - tile.getSource();
            return missing <= 0 ? 0 : (missing + rules.arsSourcePerEnergy - 1) / rules.arsSourcePerEnergy;
        }

        @Override
        public double give(BlockEntity entity, long units, MagicRules rules) {
            ISourceTile tile = (ISourceTile) entity;
            int before = tile.getSource();
            long room = Math.max(0, tile.getMaxSource() - before);
            tile.addSource((int) Math.min(room, Math.min(Integer.MAX_VALUE, units * rules.arsSourcePerEnergy)));
            entity.setChanged();
            return Math.max(0, tile.getSource() - before);
        }
    }
}
