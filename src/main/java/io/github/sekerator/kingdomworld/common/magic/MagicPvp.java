package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.core.protection.ProtectedAction;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Whom spells may hurt. Spells follow the access policy of the kingdoms: in peacetime they hurt no other player and no
 * citizen, neither foreign nor of the caster's own kingdom; exemptions of the access policy (war, a later stage) allow
 * spells against the players and citizens of an enemy. Spells of mages hurt monsters only.
 */
public final class MagicPvp {
    private MagicPvp() {
    }

    /**
     * Whether a spell of {@code caster} may hurt {@code victim}.
     */
    public static boolean mayHurt(@Nullable Entity caster, LivingEntity victim) {
        if (caster == victim) {
            return false;
        }
        if (caster instanceof CitizenEntity) {
            return victim instanceof Enemy && !(victim instanceof CitizenEntity);
        }
        if (!(caster instanceof Player player) || !(victim.level() instanceof ServerLevel level)) {
            return !(victim instanceof Player) && !(victim instanceof CitizenEntity);
        }
        UUID kingdom;
        if (victim instanceof CitizenEntity citizen) {
            kingdom = citizen.kingdomId();
        } else if (victim instanceof Player target) {
            kingdom = KingdomWorldApi.kingdomOf(level.getServer(), target.getUUID()).map(k -> k.id()).orElse(null);
        } else {
            return true;
        }
        if (kingdom == null) {
            // Players without a kingdom are not fair game either.
            return false;
        }
        KingdomSavedData data = KingdomSavedData.get(level.getServer());
        if (data.state().membership().isMember(player.getUUID(), kingdom)) {
            return false;
        }
        long chunk = ChunkKey.of(victim.blockPosition().getX() >> 4, victim.blockPosition().getZ() >> 4);
        return KingdomWorldApi.accessPolicy().isAllowed(data.state().membership(), kingdom, ProtectionService.subjectOf(player),
                ProtectedAction.ATTACK_CITIZEN, Dimensions.id(level), chunk, ProtectionService.settings());
    }
}
