package io.github.sekerator.kingdomworld.common.companion;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.guild.MonsterRanks;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.companion.Companion;
import io.github.sekerator.kingdomworld.core.companion.CompanionRules;
import io.github.sekerator.kingdomworld.core.companion.CompanionState;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.MonsterRanking;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Companions in the running world, once a second: they get hungry and eat on their own (from the pack, then from the
 * owner's inventory), free companions are paid at a new day, loyalty is watched (a warning first, an escape or a quit only
 * after long bad keeping), slaves of kingdoms that forbid slavery go free, bodies follow their owners and the parties keep
 * their contracts. Nothing of this runs for an owner who is offline: the companion waits.
 */
public final class CompanionWork {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String KEY = "kingdomworld.companion.";
    /** Ticks between two runs. */
    static final int CHECK_TICKS = 20;

    /**
     * Where a slave who escapes goes with its pack. By default it vanishes with everything it carried; the stage of roads
     * and robbers lets it join a camp of robbers instead.
     */
    @FunctionalInterface
    public interface EscapeRoute {
        EscapeRoute VANISH = (server, companion, pack, where) -> {
        };

        void escaped(MinecraftServer server, Companion companion, List<ItemStack> pack, @Nullable WorldPos where);
    }

    private static EscapeRoute escapeRoute = EscapeRoute.VANISH;
    /** Players of game tests, who are in no list of players, but count as online while a test runs. */
    private static final java.util.Map<UUID, ServerPlayer> PRESENT = new java.util.HashMap<>();

    private CompanionWork() {
    }

    /**
     * An owner who is online, or {@code null}.
     */
    @Nullable
    static ServerPlayer owner(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        return online != null ? online : PRESENT.get(player);
    }

    /**
     * Makes a player of a game test count as online, or not any more.
     */
    public static void present(ServerPlayer player, boolean here) {
        if (here) {
            PRESENT.put(player.getUUID(), player);
        } else {
            PRESENT.remove(player.getUUID());
        }
    }

    // ---------------------------------------------------------------- access

    public static CompanionState state(MinecraftServer server) {
        return CompanionSavedData.get(server).state();
    }

    public static boolean isReadOnly(MinecraftServer server) {
        return CompanionSavedData.get(server).isReadOnly() || KingdomWorldApi.isReadOnly(server) || GuildWork.isReadOnly(server);
    }

    /**
     * Runs a change of companions or parties, refusing it while their data, the guild or the kingdoms are read-only: they
     * move money of the kingdoms and read the ranks of the guild.
     */
    public static Outcome change(MinecraftServer server, String what, Function<CompanionState, Outcome> change) {
        if (isReadOnly(server)) {
            LOGGER.debug("Refused to {}: companion, guild or kingdom data is read-only", what);
            return Outcome.fail(KingdomError.READ_ONLY);
        }
        return change.apply(state(server));
    }

    /**
     * The sharing of contracts by the parties, for the guild.
     */
    public static GuildState.Sharing sharing(MinecraftServer server) {
        return CompanionSavedData.get(server).isReadOnly() ? GuildState.Sharing.NONE : state(server).parties().sharing();
    }

    /**
     * A leader took a contract: it becomes the party's.
     */
    public static void adoptContract(MinecraftServer server, UUID taker, long contractId) {
        if (!isReadOnly(server)) {
            state(server).parties().adoptContract(taker, contractId);
        }
    }

    /**
     * Sets where an escaped slave goes; for the stage of roads and robbers.
     */
    public static void setEscapeRoute(EscapeRoute route) {
        escapeRoute = route == null ? EscapeRoute.VANISH : route;
    }

    // ---------------------------------------------------------------- once a second

    static void tick(MinecraftServer server) {
        if (isReadOnly(server)) {
            return;
        }
        CompanionRules rules = CompanionSettings.rules();
        CompanionState state = state(server);
        long now = KingdomWorldApi.gameTime(server);
        long day = TreasuryActions.day(server);
        GuildState guild = GuildWork.state(server);
        state.parties().expireInvites(now);
        state.parties().forgetContracts((id, holder) -> guild.contract(id).map(contract -> contract.takenBy(holder)).orElse(false));
        state.parties().keepOnly(player -> guild.member(player).isPresent());
        for (Companion companion : List.copyOf(state.companions())) {
            ServerPlayer owner = owner(server, companion.owner());
            if (owner == null) {
                continue;
            }
            live(server, state, companion, owner, now, day, rules);
        }
        for (Companion freed : state.obeyLaw(player -> KingdomWorldApi.state(server).membership().kingdomOf(player), now, day, rules)) {
            tell(server, freed.owner(), Component.translatable(CitizenTexts.gendered(KEY + "freed_by_law", freed.gender()), CompanionTexts.name(freed)));
        }
        CompanionBodies.update(server);
        CompanionSync.tick(server);
    }

    private static void live(MinecraftServer server, CompanionState state, Companion companion, ServerPlayer owner, long now, long day,
                             CompanionRules rules) {
        UUID id = companion.id();
        Optional<CompanionEntity> body = CompanionBodies.body(id);
        boolean infirmary = body.isEmpty() && CompanionBodies.inInfirmary(server, companion.owner(), companion.lastPos().orElse(null));
        state.live(id, CHECK_TICKS, now, body.isPresent(), infirmary, rules);
        if (companion.food() <= rules.eatBelow) {
            eat(server, state, companion, owner, body.orElse(null), rules);
        }
        if (!companion.isSlave()) {
            CompanionState.Wage wage = state.payWage(id, day, rules, KingdomWorldApi.state(server), now);
            switch (wage) {
                case UNPAID -> tell(owner, Component.translatable(CitizenTexts.gendered(KEY + "unpaid", companion.gender()), CompanionTexts.name(companion),
                        CompanionTexts.money(server, companion.branch(), companion.wage())).withStyle(ChatFormatting.GOLD));
                case LEAVES -> {
                    quit(server, companion, owner, CitizenTexts.gendered(KEY + "left_unpaid", companion.gender()));
                    return;
                }
                default -> {
                }
            }
        }
        switch (state.mood(id, now, rules)) {
            case WARNING -> tell(owner, Component.translatable(CitizenTexts.gendered(companion.isSlave() ? KEY + "warning_slave" : KEY + "warning_free", companion.gender()),
                    CompanionTexts.name(companion)).withStyle(ChatFormatting.RED));
            case RECOVERED -> tell(owner, Component.translatable(CitizenTexts.gendered(KEY + "recovered", companion.gender()), CompanionTexts.name(companion))
                    .withStyle(ChatFormatting.GREEN));
            case LEAVES -> {
                if (companion.isSlave()) {
                    escape(server, companion, owner);
                } else {
                    quit(server, companion, owner, CitizenTexts.gendered(KEY + "quit", companion.gender()));
                }
            }
            default -> {
            }
        }
    }

    /**
     * A hungry companion eats: from its pack first, then from its owner's inventory if it is with the owner.
     */
    private static void eat(MinecraftServer server, CompanionState state, Companion companion, ServerPlayer owner,
                            @Nullable CompanionEntity body, CompanionRules rules) {
        int room = CompanionRules.MAX_FOOD - companion.food();
        SimpleContainer pack = CompanionSavedData.get(server).pack(companion.id());
        int usable = companion.skills().slots();
        int index = CompanionGear.food(pack, 0, usable, room);
        if (index >= 0) {
            CompanionGear.Eaten eaten = CompanionGear.eat(pack, index);
            state.feed(companion.id(), eaten.nutrition());
            if (!eaten.left().isEmpty()) {
                ItemStack rest = pack.addItem(eaten.left());
                if (!rest.isEmpty()) {
                    drop(body != null ? body : owner, rest);
                }
            }
            return;
        }
        boolean withOwner = body == null ? !companion.order().atPlace() : body.level() == owner.level() && body.distanceToSqr(owner) < 16.0 * 16.0;
        if (!withOwner) {
            return;
        }
        int own = CompanionGear.food(owner.getInventory(), 0, owner.getInventory().items.size(), room);
        if (own >= 0) {
            CompanionGear.Eaten eaten = CompanionGear.eat(owner.getInventory(), own);
            state.feed(companion.id(), eaten.nutrition());
            if (!eaten.left().isEmpty() && !owner.getInventory().add(eaten.left())) {
                drop(owner, eaten.left());
            }
        }
    }

    private static void drop(Entity where, ItemStack stack) {
        where.spawnAtLocation(stack, 0.5f);
    }

    /**
     * Hands a pack to the owner: into the inventory, what does not fit falls at the owner's feet.
     */
    static void giveBack(ServerPlayer owner, SimpleContainer pack) {
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.removeItemNoUpdate(slot);
            if (!stack.isEmpty() && !owner.getInventory().add(stack)) {
                drop(owner, stack);
            }
        }
        pack.setChanged();
    }

    /**
     * A companion leaves the owner's service for good, its body and its record go; the pack goes to the owner unless the
     * caller already took it.
     */
    static void part(MinecraftServer server, Companion companion, @Nullable ServerPlayer owner) {
        CompanionBodies.body(companion.id()).ifPresent(CompanionBodies::despawn);
        CompanionMenu.closeFor(server, companion.id());
        CompanionSavedData.get(server).takePack(companion.id()).ifPresent(pack -> {
            if (owner != null) {
                giveBack(owner, pack);
            }
        });
        state(server).remove(companion.id());
    }

    /**
     * A slave runs away with everything it carries.
     */
    static void escape(MinecraftServer server, Companion companion, ServerPlayer owner) {
        WorldPos where = CompanionBodies.body(companion.id()).map(body -> new WorldPos(Dimensions.id(body.level()), body.getBlockX(),
                body.getBlockY(), body.getBlockZ())).orElse(companion.lastPos().orElse(null));
        List<ItemStack> carried = new ArrayList<>();
        CompanionSavedData.get(server).takePack(companion.id()).ifPresent(pack -> {
            for (int slot = 0; slot < pack.getContainerSize(); slot++) {
                ItemStack stack = pack.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    carried.add(stack);
                }
            }
        });
        part(server, companion, null);
        try {
            escapeRoute.escaped(server, companion, carried, where);
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld could not send an escaped companion on its way", e);
        }
        tell(owner, Component.translatable(CitizenTexts.gendered(KEY + "escaped", companion.gender()), CompanionTexts.name(companion)).withStyle(ChatFormatting.RED));
        LOGGER.info("Companion {} of {} escaped", companion.id(), owner.getGameProfile().getName());
    }

    /**
     * A free companion quits and gives back what it carried.
     */
    static void quit(MinecraftServer server, Companion companion, ServerPlayer owner, String key) {
        part(server, companion, owner);
        tell(owner, Component.translatable(key, CompanionTexts.name(companion)).withStyle(ChatFormatting.GOLD));
    }

    // ---------------------------------------------------------------- death and kills

    /**
     * A companion died: its pack falls where it died, the chronicle of its owner's kingdom (or of the kingdom where it was
     * bought) remembers it, and the record is gone.
     */
    static void die(ServerLevel level, UUID id, DamageSource source, BlockPos pos) {
        MinecraftServer server = level.getServer();
        Optional<Companion> found = state(server).companion(id);
        if (found.isEmpty()) {
            return;
        }
        Companion companion = found.get();
        CompanionMenu.closeFor(server, id);
        CompanionSavedData.get(server).takePack(id).ifPresent(pack -> Containers.dropContents(level, pos, pack));
        WorldPos place = new WorldPos(Dimensions.id(level), pos.getX(), pos.getY(), pos.getZ());
        UUID kingdom = KingdomWorldApi.state(server).membership().kingdomOf(companion.owner()).orElse(companion.branch());
        long now = KingdomWorldApi.gameTime(server);
        DeathRecord record = new DeathRecord(companion.id(), companion.nameSeed(), companion.gender(), CitizenTexts.COMPANION, causeOf(source),
                source.getEntity() instanceof Player player ? player.getGameProfile().getName() : "", now, place);
        if (!KingdomWorldApi.isReadOnly(server) && KingdomWorldApi.kingdom(server, kingdom).isPresent()) {
            KingdomWorldApi.change(server, "remember a companion", world -> {
                world.citizens().registry().remember(kingdom, record);
                return Outcome.DONE;
            });
        }
        state(server).remove(id);
        tell(server, companion.owner(), Component.translatable(CitizenTexts.gendered(KEY + "died", companion.gender()), CompanionTexts.name(companion), place.coordinates())
                .withStyle(ChatFormatting.RED));
    }

    private static CauseOfDeath causeOf(DamageSource source) {
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
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)) {
            return CauseOfDeath.FIRE;
        }
        if (source.is(DamageTypes.FALL)) {
            return CauseOfDeath.FALL;
        }
        if (source.is(DamageTypes.DROWN)) {
            return CauseOfDeath.DROWNING;
        }
        return CauseOfDeath.OTHER;
    }

    /**
     * A companion killed a monster: experience by the monster's rank. Its owner's contracts move on in the guild.
     */
    static void onKill(MinecraftServer server, CompanionEntity body, LivingEntity victim) {
        UUID id = body.companionId();
        if (id == null || isReadOnly(server) || !(victim instanceof Enemy)) {
            return;
        }
        MonsterRanking.Ranked ranked = MonsterRanks.of(victim);
        CompanionState state = state(server);
        state.recordKill(id);
        int levels = state.addExperience(id, 4L * (ranked.rank().ordinal() + 1), CompanionSettings.rules());
        if (levels > 0) {
            state.companion(id).ifPresent(companion -> {
                body.refresh(companion);
                tell(server, companion.owner(), Component.translatable(CitizenTexts.gendered(KEY + "level_up", companion.gender()), CompanionTexts.name(companion), companion.level())
                        .withStyle(ChatFormatting.GREEN));
            });
        }
    }

    /**
     * What counts for the contracts of a player (held or shared in the party): monsters a companion on a contract hunts.
     */
    static Predicate<LivingEntity> contractTargets(MinecraftServer server, ServerPlayer owner) {
        GuildState guild = GuildWork.state(server);
        GuildState.Sharing sharing = sharing(server);
        List<Contract> mine = new ArrayList<>();
        for (Contract contract : guild.contracts()) {
            if (contract.kind().byKills() && (contract.takenBy(owner.getUUID()) || GuildState.isShared(contract, owner.getUUID(), sharing))) {
                mine.add(contract);
            }
        }
        if (mine.isEmpty()) {
            return mob -> false;
        }
        return mob -> {
            if (!MonsterRanks.isMonster(mob)) {
                return false;
            }
            MonsterRanking.Ranked ranked = MonsterRanks.of(mob);
            String type = MonsterRanks.id(mob.getType());
            for (Contract contract : mine) {
                if (contract.counts(type, ranked.rank(), ranked.boss())) {
                    return true;
                }
            }
            return false;
        };
    }

    // ---------------------------------------------------------------- messages

    static void tell(@Nullable ServerPlayer player, Component message) {
        // Players made by game tests have no connection.
        if (player != null && player.connection != null) {
            player.sendSystemMessage(message);
        }
    }

    static void tell(MinecraftServer server, UUID player, Component message) {
        KingdomActions.tell(server, player, message);
    }

    static void reset() {
        PRESENT.clear();
        CompanionBodies.reset();
        CompanionSync.reset();
        escapeRoute = EscapeRoute.VANISH;
    }
}
