package io.github.sekerator.kingdomworld.common.law;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.companion.CompanionWork;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.law.BountyRule;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawState;
import io.github.sekerator.kingdomworld.core.law.Victims;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Wires the law into the game: thefts from containers, fields and herds, blows at citizens and murders become crimes; the
 * guards beat thieves down and execute murderers (before anything falls to the ground or into a gravestone); members of the
 * guild collect the heads of wanted murderers; a click on a soldier lets a wanted player give up; the law moves on once a
 * second.
 */
public final class LawEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = LawWork.KEY;

    private LawEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(LawEvents::onServerTick);
        LawCities.register();
        MinecraftForge.EVENT_BUS.addListener(LawEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(LawEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(LawEvents::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(LawEvents::onLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(LawEvents::onContainerOpen);
        MinecraftForge.EVENT_BUS.addListener(LawEvents::onContainerClose);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, BlockEvent.EntityPlaceEvent.class, LawEvents::onPlace);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingHurtEvent.class, LawEvents::onHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, LawEvents::onDamage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, LawEvents::onDeath);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingDropsEvent.class, LawEvents::onDrops);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.EntityInteract.class, LawEvents::onInteract);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, net.minecraftforge.event.entity.living.LivingAttackEvent.class,
                LawEvents::onAttackCitizen);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, net.minecraftforge.event.entity.player.AttackEntityEvent.class,
                LawEvents::onStrikeCitizen);
    }

    /**
     * Whether the law judges what a player does on the land of a kingdom instead of the protection refusing it: a real player
     * on the server while the law is writable. Machines of mods and anybody while the law is read-only meet the old refusals.
     */
    public static boolean judges(Player player, Level level) {
        return player instanceof ServerPlayer && !(player instanceof FakePlayer) && level instanceof ServerLevel server
                && !LawWork.isReadOnly(server.getServer());
    }

    /**
     * Blows the law cannot judge are refused as before: those of machines, and of anybody while the law is read-only.
     */
    public static boolean refused(Player attacker, CitizenEntity citizen) {
        if (io.github.sekerator.kingdomworld.common.protection.ProtectionService.mayHurtCitizen(attacker, citizen)) {
            return false;
        }
        return !judges(attacker, citizen.level());
    }

    private static void refuse(Player attacker, CitizenEntity citizen) {
        if (citizen.getServer() == null || citizen.kingdomId() == null) {
            return;
        }
        KingdomWorldApi.kingdom(citizen.getServer(), citizen.kingdomId()).ifPresent(kingdom -> io.github.sekerator.kingdomworld.common.protection
                .ProtectionService.notify(attacker, Component.translatable("kingdomworld.protection.citizen",
                        Component.literal(io.github.sekerator.kingdomworld.common.protection.ProtectionService.shortName(kingdom.name()))
                                .withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(kingdom.color()))))));
    }

    private static void onAttackCitizen(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof CitizenEntity citizen && !citizen.level().isClientSide
                && event.getSource().getEntity() instanceof Player attacker && refused(attacker, citizen)) {
            event.setCanceled(true);
            refuse(attacker, citizen);
        }
    }

    private static void onStrikeCitizen(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.getTarget() instanceof CitizenEntity citizen && !citizen.level().isClientSide && refused(event.getEntity(), citizen)) {
            event.setCanceled(true);
            refuse(event.getEntity(), citizen);
        }
    }

    // ---------------------------------------------------------------- the server

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        try {
            if (tick % 10 == 0) {
                LawContainers.tick(server);
            }
            if (tick % 20 == 0) {
                LawSettings.refresh();
                LawWork.tick(server);
                LawSync.tick(server);
            }
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld law tick failed", e);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        LawSavedData data = LawSavedData.get(server);
        LawState state = data.state();
        // The ban of slavery of each kingdom is the law the companions obey.
        CompanionWork.state(server).setLaw(kingdomId -> !LawSavedData.get(server).state().slaveryBanned(kingdomId));
        // The cities of the roads with their gates.
        LawWork.setGates(LawCities.gates(server));
        LOGGER.info("KingdomWorld law: {} wanted, {} confiscation(s), {} court(s), data format {}{}", state.allWanted().size(),
                state.confiscations().size(), state.laws().size(), LawSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        LawSettings.reset();
        LawWork.reset();
        LawWork.setGates(null);
        LawActions.resetEntries();
        LawSync.reset();
        LawPunish.resetSentences();
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            LawSync.dirty(player.getUUID());
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        if (event.getEntity() instanceof ServerPlayer leaving) {
            // What was taken or broken before leaving is a crime all the same.
            LawContainers.forget(leaving);
            LawVandalism.reportNow(leaving);
        }
        LawPunish.forgetSentence(player);
        LawActions.forgetEntry(player);
        LawSync.forget(player);
        LawGuards.forget(player);
    }

    // ---------------------------------------------------------------- thefts

    /**
     * A player without the right to containers opens one on the land of a kingdom: allowed, but what is taken is a theft.
     * Called by the protection instead of refusing.
     */
    public static void containerUse(Player player, Level level, BlockPos pos) {
        if (player instanceof ServerPlayer server && !(player instanceof FakePlayer)) {
            LawContainers.aboutToOpen(server, pos, LawContainers.ownerOf(level, pos));
        }
    }

    /**
     * Whether a player placed the container at a place on the land of a kingdom: it is that player's, not the kingdom's and
     * not a citizen's.
     */
    public static boolean placedByPlayer(Level level, BlockPos pos) {
        return LawContainers.ownerOf(level, pos) != null;
    }

    /**
     * Watches a window as if the player had just opened the container at {@code pos}, for game tests (their players have no
     * connection to open windows through).
     */
    public static void watchForCheck(ServerPlayer player, BlockPos pos, net.minecraft.world.inventory.AbstractContainerMenu menu) {
        LawContainers.aboutToOpen(player, pos, LawContainers.ownerOf(player.level(), pos));
        LawContainers.opened(player, menu);
    }

    /**
     * Closes a watched window, for game tests.
     */
    public static void closedForCheck(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu) {
        LawContainers.closed(player, menu);
    }

    private static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            LawContainers.opened(player, event.getContainer());
        }
    }

    private static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            LawContainers.closed(player, event.getContainer());
        }
    }

    /**
     * Containers a player places on the land of a kingdom belong to that player (the storehouses of the kingdom belong to
     * the kingdom).
     */
    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)
                || player instanceof FakePlayer || KingdomWorldApi.ownerAt(level, event.getPos()) == null
                || event.getPlacedBlock().is(ModRegistries.STOREHOUSE.get())) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(event.getPos());
        if (entity != null && (entity instanceof Container || entity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent())) {
            LawContainers.markOwner(level, event.getPos(), player.getUUID());
        }
    }

    /**
     * Whether a block is a ripe crop: grain and roots at their last age, nether wart, cocoa.
     */
    public static boolean isRipe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        return false;
    }

    /**
     * A player without the right to build breaks a ripe crop on the land of a kingdom: a theft rather than refused. Called by
     * the protection before it refuses.
     *
     * @return whether the harvest is allowed
     */
    public static boolean harvest(Player player, Level level, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayer actor) || actor instanceof FakePlayer || !(level instanceof ServerLevel serverLevel)
                || !isRipe(state) || LawWork.isReadOnly(actor.server)) {
            return false;
        }
        UUID kingdom = KingdomWorldApi.ownerAt(level, pos);
        if (kingdom == null) {
            return false;
        }
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, level.getBlockEntity(pos), actor, actor.getMainHandItem());
        long worth = Math.max(1, LawPunish.worth(actor.server, kingdom, drops));
        LawWork.deed(actor, pos, kingdom, CrimeKind.THEFT, worth, "", Permission.BUILD, false, null);
        return true;
    }

    /**
     * Animals killed on the land of a kingdom by a player without the right to build: a theft of what they drop.
     */
    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Animal animal) || animal.level().isClientSide
                || !(event.getSource().getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        UUID kingdom = KingdomWorldApi.ownerAt(animal.level(), animal.blockPosition());
        if (kingdom == null) {
            return;
        }
        boolean owner = animal instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID());
        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity item : event.getDrops()) {
            drops.add(item.getItem());
        }
        long worth = Math.max(1, LawPunish.worth(player.server, kingdom, drops));
        LawWork.deed(player, animal.blockPosition(), kingdom, CrimeKind.THEFT, worth, "", Permission.BUILD, owner, animal);
    }

    /**
     * A click on an entity: on a soldier of a kingdom that wants the player, the window to give up; shearing or milking an
     * animal of a kingdom is a theft.
     */
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        Entity target = event.getTarget();
        MinecraftServer server = player.server;
        if (target instanceof CitizenEntity body && LawWork.isSoldier(body)) {
            if (!LawWork.isReadOnly(server) && LawWork.state(server).wanted(body.kingdomId(), player.getUUID()).isPresent()) {
                LawActions.openSurrender(player, body.kingdomId());
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (!(target instanceof Animal animal) || animal.isBaby()) {
            return;
        }
        UUID kingdom = KingdomWorldApi.ownerAt(animal.level(), animal.blockPosition());
        if (kingdom == null) {
            return;
        }
        ItemStack stack = event.getItemStack();
        long worth = 0;
        if (animal instanceof Shearable shearable && shearable.readyForShearing() && stack.canPerformAction(ToolActions.SHEARS_HARVEST)) {
            worth = 2 * LawPunish.worth(server, kingdom, new ItemStack(Items.WHITE_WOOL));
        } else if (animal instanceof Cow && stack.is(Items.BUCKET)) {
            worth = LawPunish.worth(server, kingdom, new ItemStack(Items.MILK_BUCKET)) - LawPunish.worth(server, kingdom, new ItemStack(Items.BUCKET));
        }
        if (worth > 0 || animal instanceof Cow && stack.is(Items.BUCKET)) {
            boolean owner = animal instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID());
            LawWork.deed(player, animal.blockPosition(), kingdom, CrimeKind.THEFT, Math.max(1, worth), "", Permission.BUILD, owner, animal);
        }
    }

    // ---------------------------------------------------------------- blows, beatings, deaths

    /**
     * A player strikes a citizen: an assault on the land of the citizen's kingdom (or wherever it stands).
     */
    private static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof CitizenEntity citizen) || citizen.level().isClientSide || citizen.kingdomId() == null
                || !(event.getSource().getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || event.getAmount() <= 0
                || event.getAmount() >= citizen.getHealth()) {
            return;
        }
        UUID land = KingdomWorldApi.ownerAt(citizen.level(), citizen.blockPosition());
        UUID kingdom = land != null ? land : citizen.kingdomId();
        LawWork.deed(player, citizen.blockPosition(), kingdom, CrimeKind.ASSAULT, 0, victim(citizen), null, false, citizen);
        if (LawWork.isSoldier(citizen) && citizen.citizenId() != null
                && citizen.getHealth() - event.getAmount() < citizen.getMaxHealth()
                * io.github.sekerator.kingdomworld.common.citizen.CitizenSettings.rules().woundedFraction()) {
            LawAlarms.guardHurt(kingdom, player.getUUID(), citizen.citizenId(), false);
        }
    }

    /**
     * A player struck a citizen whose shield took the blow. No harm was done, but it is an assault all the same: the alarm
     * is raised and the fight counts from this blow, as if it had landed.
     */
    public static void wardedOff(CitizenEntity citizen, ServerPlayer player) {
        if (citizen.level().isClientSide || citizen.kingdomId() == null || player instanceof FakePlayer) {
            return;
        }
        UUID land = KingdomWorldApi.ownerAt(citizen.level(), citizen.blockPosition());
        UUID kingdom = land != null ? land : citizen.kingdomId();
        LawWork.deed(player, citizen.blockPosition(), kingdom, CrimeKind.ASSAULT, 0, victim(citizen), null, false, citizen);
    }

    /**
     * A citizen as the victim a record names.
     */
    private static String victim(CitizenEntity citizen) {
        MinecraftServer server = citizen.getServer();
        if (server == null || citizen.citizenId() == null) {
            return "";
        }
        return KingdomWorldApi.state(server).citizens().get(citizen.citizenId())
                .map(record -> Victims.citizen(record.nameSeed(), record.gender() == io.github.sekerator.kingdomworld.core.citizen.Gender.FEMALE))
                .orElse("");
    }

    /**
     * Guards beat a thief down, but no further: the blow that would kill leaves the player with a little health, and the
     * fine is paid at once.
     */
    private static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide || player instanceof FakePlayer
                || !(event.getSource().getEntity() instanceof CitizenEntity body) || !LawWork.isSoldier(body)) {
            return;
        }
        MinecraftServer server = player.server;
        if (LawWork.isReadOnly(server)) {
            return;
        }
        // A soldier that strikes sees whom it strikes: what an alarm still keeps unreported is on the record before the blow
        // lands, so that the blow is a beating or an execution by the law and never a plain killing.
        LawAlarms.sees(server, body.kingdomId(), player);
        Optional<Wanted> wanted = LawWork.state(server).wanted(body.kingdomId(), player.getUUID());
        if (wanted.isEmpty() || wanted.get().executable()) {
            return;
        }
        if (player.getHealth() - event.getAmount() <= LawSettings.rules().beatenHealth) {
            event.setAmount(0.0f);
            LawPunish.beaten(player, body.kingdomId());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        MinecraftServer server = victim.getServer();
        if (victim.level().isClientSide || server == null || LawWork.isReadOnly(server)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer == null) {
            killer = victim.getKillCredit();
        }
        try {
            if (victim instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                playerDied(event, player, killer);
            } else if (victim instanceof CitizenEntity citizen && citizen.kingdomId() != null && killer instanceof ServerPlayer murderer
                    && !(murderer instanceof FakePlayer)) {
                UUID land = KingdomWorldApi.ownerAt(citizen.level(), citizen.blockPosition());
                UUID kingdom = land != null ? land : citizen.kingdomId();
                LawWork.deed(murderer, citizen.blockPosition(), kingdom, CrimeKind.MURDER, 0, victim(citizen), null, false, citizen);
                if (LawWork.isSoldier(citizen) && citizen.citizenId() != null) {
                    LawAlarms.guardHurt(kingdom, murderer.getUUID(), citizen.citizenId(), true);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("KingdomWorld law could not judge the death of {}", victim.getName().getString(), e);
        }
    }

    private static void playerDied(LivingDeathEvent event, ServerPlayer player, Entity killer) {
        MinecraftServer server = player.server;
        LawState state = LawWork.state(server);
        Optional<UUID> sentence = LawPunish.takeSentence(player);
        if (sentence.isPresent()) {
            // The player gave up to die by the law: the goods go into the record before anything falls.
            LawPunish.execute(player, sentence.get(), "executed_surrender");
            return;
        }
        if (killer instanceof CitizenEntity body && LawWork.isSoldier(body)) {
            Optional<Wanted> wanted = state.wanted(body.kingdomId(), player.getUUID());
            if (wanted.isEmpty()) {
                return;
            }
            if (wanted.get().executable()) {
                // The goods go into the record before the death drops anything: neither the ground nor a gravestone gets them.
                LawPunish.execute(player, body.kingdomId(), "executed");
            } else {
                event.setCanceled(true);
                LawPunish.beaten(player, body.kingdomId());
            }
            return;
        }
        if (!(killer instanceof ServerPlayer hunter) || hunter == player || hunter instanceof FakePlayer) {
            return;
        }
        if (headCollected(server, hunter, player)) {
            return;
        }
        LawWork.deed(hunter, player.blockPosition(), null, CrimeKind.MURDER, 0, player.getGameProfile().getName(), null, false, player);
    }

    /**
     * A member of the guild killed a murderer whose head a kingdom wants (online and at the keyboard): a lawful execution,
     * the contract is the hunter's.
     *
     * @return whether the death was such an execution
     */
    /**
     * Whether a hunter and the wanted are partners: in one party of the guild.
     */
    static boolean related(MinecraftServer server, ServerPlayer hunter, ServerPlayer outlaw) {
        return CompanionWork.state(server).parties().partyOf(hunter.getUUID()).map(found -> found.has(outlaw.getUUID())).orElse(false);
    }

    static boolean headCollected(MinecraftServer server, ServerPlayer hunter, ServerPlayer outlaw) {
        if (GuildWork.isReadOnly(server) || GuildWork.state(server).member(hunter.getUUID()).isEmpty() || outlaw.hasDisconnected()
                || LawWork.isAway(outlaw)) {
            return false;
        }
        LawState state = LawWork.state(server);
        for (Wanted wanted : List.copyOf(state.wantedOf(outlaw.getUUID()))) {
            if (!wanted.executable()) {
                continue;
            }
            Optional<Contract> contract = LawWork.bountyFor(server, wanted.kingdomId(), outlaw.getUUID());
            if (contract.isEmpty()) {
                continue;
            }
            if (related(server, hunter, outlaw)) {
                // Two of a kind do not empty the treasury: the head of a partner pays nothing, the reward goes back.
                LawWork.withdrawBounty(server, wanted);
                LawPunish.execute(outlaw, wanted.kingdomId(), "executed_hunter");
                hunter.sendSystemMessage(Component.translatable(KEY + "head_no_reward", outlaw.getGameProfile().getName())
                        .withStyle(ChatFormatting.GOLD));
                LOGGER.info("{} killed {}, who is in the same party: no reward", hunter.getGameProfile().getName(),
                        outlaw.getGameProfile().getName());
                return true;
            }
            // From the third head of the same player, the reward is no more than what the kingdom takes from the player.
            int before = state.captures(hunter.getUUID(), outlaw.getUUID());
            long coins = BountyRule.reduced(before)
                    ? io.github.sekerator.kingdomworld.common.economy.CoinStacks.count(outlaw.getInventory(), wanted.kingdomId())
                    .value(io.github.sekerator.kingdomworld.common.trade.Purses.units(server, wanted.kingdomId())) : 0;
            state.setBounty(wanted.kingdomId(), outlaw.getUUID(), 0);
            Optional<io.github.sekerator.kingdomworld.core.law.Confiscation> taken = LawPunish.execute(outlaw, wanted.kingdomId(), "executed_hunter");
            long ransom = taken.map(confiscation -> LawSettings.rules().ransom(confiscation.worth())).orElse(0L);
            long cap = BountyRule.reward(contract.get().reward(), before, ransom, coins);
            Optional<GuildState.Progress> done = GuildWork.state(server).claimOutlaw(contract.get().id(), hunter.getUUID(),
                    TreasuryActions.day(server), KingdomWorldApi.state(server), CompanionWork.sharing(server), cap);
            state.countCapture(hunter.getUUID(), outlaw.getUUID());
            if (done.isPresent()) {
                LawWork.contractDone(server, wanted.kingdomId(), hunter.getUUID());
                String key = cap < contract.get().reward() ? "head_collected_reduced" : "head_collected";
                hunter.sendSystemMessage(Component.translatable(KEY + key, outlaw.getGameProfile().getName(),
                        MoneyTexts.amount(done.get().paid(), LawTexts.units(server, contract.get().currency()))).withStyle(ChatFormatting.GREEN));
            }
            LOGGER.info("{} collected the head of {} for {} (contract #{})", hunter.getGameProfile().getName(), outlaw.getGameProfile().getName(),
                    wanted.kingdomId(), contract.get().id());
            return true;
        }
        return false;
    }
}
