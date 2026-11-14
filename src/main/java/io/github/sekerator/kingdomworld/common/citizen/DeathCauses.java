package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.citizen.DeathCause;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * What killed a body, for the chronicle: the same for citizens and for companions. Who dealt the blow comes first (a player by
 * name, a citizen or soldier by its kingdom, a robber, a monster, a golem, an animal or another creature by its kind and its own
 * name), then the kinds of damage the chronicle always knew, then the kind of damage by its id. The work far from players gives
 * its causes (the mine, the forest, a battle) without this.
 */
public final class DeathCauses {

    private DeathCauses() {
    }

    public static DeathCause of(@Nullable MinecraftServer server, DamageSource source) {
        Entity attacker = source.getEntity();
        DeathCause.Attacker kind = attacker(attacker);
        String name = "";
        String type = "";
        if (attacker != null) {
            ResourceLocation id = EntityType.getKey(attacker.getType());
            type = id == null ? "" : id.toString();
            if (attacker instanceof Player player) {
                name = player.getGameProfile().getName();
            } else if (attacker instanceof CitizenEntity body) {
                name = server == null || body.kingdomId() == null ? ""
                        : KingdomWorldApi.kingdom(server, body.kingdomId()).map(Kingdom::name).orElse("");
            } else {
                name = ownName(attacker);
            }
        }
        return DeathCause.classify(kind, name, type, hurt(source), source.getMsgId());
    }

    /**
     * Who an attacker is, as the chronicle tells it.
     */
    static DeathCause.Attacker attacker(@Nullable Entity attacker) {
        if (attacker == null) {
            return DeathCause.Attacker.NONE;
        }
        if (attacker instanceof Player) {
            return DeathCause.Attacker.PLAYER;
        }
        if (attacker instanceof CitizenEntity body) {
            return Professions.isMilitary(body.professionId()) ? DeathCause.Attacker.SOLDIER : DeathCause.Attacker.CITIZEN;
        }
        if (attacker instanceof io.github.sekerator.kingdomworld.common.road.BanditEntity) {
            return DeathCause.Attacker.ROBBER;
        }
        if (attacker instanceof Enemy) {
            return DeathCause.Attacker.MONSTER;
        }
        if (attacker instanceof AbstractGolem) {
            return DeathCause.Attacker.GOLEM;
        }
        if (attacker instanceof Animal || attacker instanceof WaterAnimal) {
            return DeathCause.Attacker.ANIMAL;
        }
        return DeathCause.Attacker.CREATURE;
    }

    /**
     * The kind of damage among those the chronicle always knew.
     */
    static DeathCause.Hurt hurt(DamageSource source) {
        if (source.is(DamageTypes.STARVE)) {
            return DeathCause.Hurt.STARVATION;
        }
        if (source.is(DamageTypes.LAVA)) {
            return DeathCause.Hurt.LAVA;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return DeathCause.Hurt.FIRE;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return DeathCause.Hurt.FALL;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return DeathCause.Hurt.DROWNING;
        }
        return DeathCause.Hurt.OTHER;
    }

    /**
     * The name a creature was given (with a name tag, say); names a mod puts together in the language of each player are left out.
     */
    private static String ownName(Entity entity) {
        Component name = entity.getCustomName();
        if (name == null || !(name.getContents() instanceof LiteralContents) || !name.getSiblings().isEmpty()) {
            return "";
        }
        return name.getString();
    }
}
