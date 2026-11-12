package io.github.sekerator.kingdomworld.common.citizen;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Births and deaths: newcomers arriving in a kingdom and citizens dying, with the messages to the people who
 * manage the kingdom's citizens.
 */
public final class CitizenLife {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Looks per gender; the client has a texture for each. */
    public static final int SKINS = 4;
    /** Newcomers arrive grown up, aged this many days or up to twenty more. */
    private static final int ADULT_DAYS = 20;

    private CitizenLife() {
    }

    /**
     * A new citizen arrives at {@code place}, with a random name and look, and moves into the free bed nearest to
     * it if there is one.
     *
     * @return the newcomer, or empty if the data is read-only
     */
    public static Optional<Citizen> arrive(MinecraftServer server, UUID kingdomId, WorldPos place, boolean announce) {
        return arrive(server, kingdomId, place, announce, true);
    }

    /**
     * A new citizen arrives at {@code place}, with a random name and look.
     *
     * @param bed whether it moves into the free bed nearest to the place; a recruit of a royal army from abroad lives in a camp
     *            and takes none
     * @return the newcomer, or empty if the data is read-only
     */
    public static Optional<Citizen> arrive(MinecraftServer server, UUID kingdomId, WorldPos place, boolean announce, boolean bed) {
        RandomSource random = server.overworld().getRandom();
        long now = KingdomWorldApi.gameTime(server);
        Citizen citizen = new Citizen(UUID.randomUUID(), kingdomId, random.nextInt(),
                random.nextBoolean() ? Gender.MALE : Gender.FEMALE, random.nextInt(SKINS),
                now - (ADULT_DAYS + random.nextInt(21)) * CitizenNeeds.TICKS_PER_DAY, place);
        Outcome outcome = KingdomWorldApi.change(server, "add a citizen", state -> {
            if (state.kingdoms().get(kingdomId).isEmpty()) {
                return Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNKNOWN_KINGDOM);
            }
            state.citizens().add(citizen);
            state.citizens().registry().markArrival(kingdomId, now);
            // The same beds the simulation offers: a house first, never a room of a tavern or the bed of a player.
            if (bed) {
                io.github.sekerator.kingdomworld.common.construction.BuildingEffects.freeBed(server, kingdomId, place)
                        .ifPresent(free -> state.citizens().assignHome(citizen, free.pos()));
            }
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            return Optional.empty();
        }
        if (announce) {
            KingdomWorldApi.kingdom(server, kingdomId).ifPresent(kingdom -> KingdomActions.notifyMembers(server, kingdom,
                    Permission.MANAGE_CITIZENS, net.minecraft.network.chat.Component.translatable(
                            CitizenTexts.gendered("kingdomworld.citizen.arrived", citizen.gender()), CitizenTexts.name(citizen),
                            KingdomActions.kingdomName(kingdom))));
        }
        return Optional.of(citizen);
    }

    /**
     * A citizen dies for good: it leaves the population, its places become free, the chronicle remembers it and
     * the king and the members who manage citizens are told.
     */
    public static Optional<DeathRecord> die(MinecraftServer server, UUID citizenId, CauseOfDeath cause, String killer,
                                            WorldPos place) {
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(citizenId);
        if (citizen.isEmpty()) {
            return Optional.empty();
        }
        long now = KingdomWorldApi.gameTime(server);
        DeathRecord[] record = new DeathRecord[1];
        Outcome outcome = KingdomWorldApi.change(server, "record the death of a citizen", state -> {
            record[0] = state.citizens().die(citizenId, cause, killer, now, place).orElse(null);
            return Outcome.DONE;
        });
        if (!outcome.ok() || record[0] == null) {
            return Optional.empty();
        }
        CitizenBodies.force(citizenId, false);
        io.github.sekerator.kingdomworld.common.production.Production.release(citizenId);
        CitizenEntity body = CitizenBodies.body(citizenId);
        if (body != null && body.isAlive()) {
            body.storeGear(io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(server).gear());
            body.discard();
        }
        // Tools and armor of a citizen who died without a body go back to the storehouses.
        for (net.minecraft.world.item.ItemStack stack : io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(server)
                .gear().takeAll(citizenId)) {
            // Gear a kingdom issued to an officer is gone with the officer; that of a fighter comes back as plain gear.
            net.minecraft.world.item.ItemStack kept = io.github.sekerator.kingdomworld.common.army.ArmyKits.released(stack);
            if (!kept.isEmpty()) {
                io.github.sekerator.kingdomworld.common.storage.KingdomStorage.insert(server, citizen.get().kingdomId(), kept, place);
            }
        }
        LOGGER.info("Citizen {} ({}) of kingdom {} died: {} at {} in {}", citizen.get().name(CitizenNames.Alphabet.LATIN),
                citizen.get().shortId(), citizen.get().kingdomId(), cause.id(), record[0].place().coordinates(),
                record[0].place().dimension());
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, citizen.get().kingdomId());
        kingdom.ifPresent(k -> KingdomActions.notifyMembers(server, k, Permission.MANAGE_CITIZENS,
                net.minecraft.network.chat.Component.translatable(CitizenTexts.gendered("kingdomworld.citizen.died", citizen.get().gender()),
                        CitizenTexts.name(citizen.get()), CitizenTexts.cause(cause, killer, citizen.get().gender()),
                        CitizenTexts.place(record[0].place()))));
        return Optional.of(record[0]);
    }

    /**
     * What killed a body, for the chronicle.
     */
    static CauseOfDeath causeOf(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player) {
            return CauseOfDeath.PLAYER;
        }
        if (attacker instanceof Enemy) {
            return CauseOfDeath.MONSTER;
        }
        if (source.is(DamageTypes.STARVE)) {
            return CauseOfDeath.STARVATION;
        }
        if (source.is(DamageTypes.LAVA)) {
            return CauseOfDeath.LAVA;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return CauseOfDeath.FIRE;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return CauseOfDeath.FALL;
        }
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            return CauseOfDeath.DROWNING;
        }
        if (attacker != null) {
            return CauseOfDeath.MONSTER;
        }
        return CauseOfDeath.OTHER;
    }

    static String killerOf(DamageSource source) {
        return source.getEntity() instanceof Player player ? player.getGameProfile().getName() : "";
    }
}
