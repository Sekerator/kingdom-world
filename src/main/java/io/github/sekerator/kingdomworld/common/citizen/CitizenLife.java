package io.github.sekerator.kingdomworld.common.citizen;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.DeathCause;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
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
        return arrive(server, kingdomId, place, null, announce, bed);
    }

    /**
     * A new citizen arrives at {@code place}, the town hall of a city, and moves into a free bed of that city.
     *
     * @param home the free bed it moves into; the free bed nearest to the place, as {@link #arrive}, when it is gone meanwhile
     *             or {@code null}
     * @return the newcomer, or empty if the data is read-only
     */
    public static Optional<Citizen> arriveInto(MinecraftServer server, UUID kingdomId, WorldPos place, @org.jetbrains.annotations.Nullable WorldPos home,
                                               boolean announce) {
        return arrive(server, kingdomId, place, home, announce, true);
    }

    private static Optional<Citizen> arrive(MinecraftServer server, UUID kingdomId, WorldPos place, @org.jetbrains.annotations.Nullable WorldPos home,
                                            boolean announce, boolean bed) {
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
            if (bed && home != null && state.citizens().sites().get(home).map(site -> site.isFree() && kingdomId.equals(site.kingdomId()))
                    .orElse(false) && io.github.sekerator.kingdomworld.common.construction.BuildingEffects.bedUsable(server, kingdomId, home)
                    && state.citizens().assignHome(citizen, home)) {
                return Outcome.DONE;
            }
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
     * A settler of a settlement of a state arrives: always the same person for the same world, settlement and slot (its id,
     * name, gender, look and age come from them), so that a citizen folded into the numbers of its settlement comes back as
     * itself. Nothing happens if the citizen is there already.
     *
     * @param born when it was born, from what its settlement kept of it; {@link Long#MIN_VALUE} for its age by the slot
     * @return the settler, or empty if the data is read-only or it exists
     */
    public static Optional<Citizen> settle(MinecraftServer server, UUID kingdomId, int settlement, long settlementSeed, int slot, WorldPos place,
                                           long born) {
        UUID id = settlerId(server.overworld().getSeed(), settlement, slot);
        if (KingdomWorldApi.state(server).citizens().get(id).isPresent()) {
            return Optional.empty();
        }
        long mixed = io.github.sekerator.kingdomworld.core.npc.Settler.mix(settlementSeed, slot);
        long now = KingdomWorldApi.gameTime(server);
        long birth = born != Long.MIN_VALUE ? born : now - (ADULT_DAYS + Math.floorMod(mixed >>> 40, 21)) * CitizenNeeds.TICKS_PER_DAY;
        Citizen citizen = new Citizen(id, kingdomId, (int) mixed, (mixed & (1L << 33)) == 0 ? Gender.MALE : Gender.FEMALE,
                (int) Math.floorMod(mixed >>> 34, SKINS), birth, place);
        citizen.restoreSettlement(settlement);
        Outcome outcome = KingdomWorldApi.change(server, "add a settler", state -> {
            if (state.kingdoms().get(kingdomId).isEmpty()) {
                return Outcome.fail(io.github.sekerator.kingdomworld.core.kingdom.KingdomError.UNKNOWN_KINGDOM);
            }
            state.citizens().add(citizen);
            io.github.sekerator.kingdomworld.common.construction.BuildingEffects.freeBed(server, kingdomId, place)
                    .ifPresent(free -> state.citizens().assignHome(citizen, free.pos()));
            return Outcome.DONE;
        });
        return outcome.ok() ? Optional.of(citizen) : Optional.empty();
    }

    /**
     * The id of a settler: the same for the same world, settlement and slot.
     */
    public static UUID settlerId(long worldSeed, int settlement, int slot) {
        return UUID.nameUUIDFromBytes(("kingdomworld:settler:" + worldSeed + ":" + settlement + ":" + slot)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * A citizen dies for good: it leaves the population, its places become free, the chronicle remembers it and
     * the king and the members who manage citizens are told.
     */
    public static Optional<DeathRecord> die(MinecraftServer server, UUID citizenId, CauseOfDeath cause, String killer,
                                            WorldPos place) {
        return die(server, citizenId, DeathCause.of(cause, killer), place);
    }

    /**
     * A citizen dies for good, with the whole cause of its death (who killed, the kind of damage).
     */
    public static Optional<DeathRecord> die(MinecraftServer server, UUID citizenId, DeathCause cause, WorldPos place) {
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(citizenId);
        if (citizen.isEmpty()) {
            return Optional.empty();
        }
        long now = KingdomWorldApi.gameTime(server);
        DeathRecord[] record = new DeathRecord[1];
        Outcome outcome = KingdomWorldApi.change(server, "record the death of a citizen", state -> {
            record[0] = state.citizens().die(citizenId, cause, now, place).orElse(null);
            return Outcome.DONE;
        });
        if (!outcome.ok() || record[0] == null) {
            return Optional.empty();
        }
        CitizenBodies.force(citizenId, false);
        io.github.sekerator.kingdomworld.common.production.Production.release(citizenId);
        if (citizen.get().settlement() != 0) {
            // A death is a citizen less in the numbers of its settlement.
            io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).settlement(citizen.get().settlement())
                    .ifPresent(settlement -> settlement.setPopulation(settlement.population() - 1));
        }
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
        LOGGER.info("Citizen {} ({}) of kingdom {} died: {}{}{} at {} in {}", citizen.get().name(CitizenNames.Alphabet.LATIN),
                citizen.get().shortId(), citizen.get().kingdomId(), cause.cause().id(),
                cause.killerType().isEmpty() ? "" : " by " + cause.killerType(), cause.damage().isEmpty() ? "" : " (" + cause.damage() + ")",
                record[0].place().coordinates(), record[0].place().dimension());
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, citizen.get().kingdomId());
        kingdom.ifPresent(k -> KingdomActions.notifyMembers(server, k, Permission.MANAGE_CITIZENS,
                diedMessage(citizen.get(), cause, record[0].place())));
        return Optional.of(record[0]);
    }

    /**
     * What the king and those who manage the citizens are told of a death, with the whole cause.
     */
    static net.minecraft.network.chat.Component diedMessage(Citizen citizen, DeathCause cause, WorldPos place) {
        Gender gender = citizen.gender();
        if (cause.telling() == DeathCause.Telling.GAME) {
            // The death message of the game holds the name itself.
            return net.minecraft.network.chat.Component.translatable(CitizenTexts.gendered("kingdomworld.citizen.died_told", gender),
                    CitizenTexts.death(cause, CitizenTexts.name(citizen), gender), CitizenTexts.place(place));
        }
        return net.minecraft.network.chat.Component.translatable(CitizenTexts.gendered("kingdomworld.citizen.died", gender),
                CitizenTexts.name(citizen), CitizenTexts.cause(cause, gender), CitizenTexts.place(place));
    }
}
