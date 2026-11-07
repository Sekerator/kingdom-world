package io.github.sekerator.kingdomworld.compat.irons;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.magic.MagicWells;
import io.github.sekerator.kingdomworld.compat.CompatModule;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Integration with Iron's Spells 'n Spellbooks: the well of a magic focus refills the mana of subjects nearby for the energy of
 * their kingdom, through the official API ({@link MagicData} and the maximum mana attribute). Iron's own regeneration sends
 * the new mana to the client. This is the only package allowed to reference classes of Iron's Spells.
 */
public final class IronsSpellsCompat implements CompatModule {
    public static final String MOD_ID = "irons_spellbooks";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void init() {
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        MagicWells.register(new Well());
        LOGGER.info("Iron's Spells 'n Spellbooks {} detected, the wells of magic foci refill its mana for the energy of kingdoms", version);
    }

    private static final class Well implements MagicWells.ManaWell {
        @Override
        public String name() {
            return "Iron's Spells mana";
        }

        @Override
        public boolean enabled(MagicRules rules) {
            return rules.ironsWell;
        }

        private static double max(ServerPlayer player) {
            return player.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        }

        @Override
        public long need(ServerPlayer player, MagicRules rules) {
            double missing = max(player) - MagicData.getPlayerMagicData(player).getMana();
            return missing <= 0.0 ? 0 : (long) Math.ceil(missing / rules.ironsManaPerEnergy);
        }

        @Override
        public double give(ServerPlayer player, long units, MagicRules rules) {
            MagicData data = MagicData.getPlayerMagicData(player);
            float before = data.getMana();
            float after = (float) Math.min(max(player), before + (double) units * rules.ironsManaPerEnergy);
            data.setMana(after);
            return Math.max(0.0, after - before);
        }
    }
}
