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
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.LawState;
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
        LOGGER.info("KingdomWorld law: {} wanted, {} confiscation(s), {} court(s), data format {}{}", state.allWanted().size(),
                state.confiscations().size(), state.laws().size(), LawSavedData.FORMAT_VERSION,
                data.isReadOnly() ? " (READ-ONLY: " + data.readOnlyReason().orElse("?") + ")" : "");
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        LawSettings.reset();
        LawWork.reset();
        LawSync.reset();
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            LawSync.dirty(player.getUUID());
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        LawContainers.forget(player);
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
        LawWork.deed(player, citizen.blockPosition(), land != null ? land : citizen.kingdomId(), CrimeKind.ASSAULT, 0, "", null, false, citizen);
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
                LawWork.deed(murderer, citizen.blockPosition(), land != null ? land : citizen.kingdomId(), CrimeKind.MURDER, 0, "", null, false,
                        citizen);
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
            Optional<GuildState.Progress> done = GuildWork.state(server).claimOutlaw(contract.get().id(), hunter.getUUID(),
                    TreasuryActions.day(server), KingdomWorldApi.state(server), CompanionWork.sharing(server));
            state.setBounty(wanted.kingdomId(), outlaw.getUUID(), 0);
            LawPunish.execute(outlaw, wanted.kingdomId(), "executed_hunter");
            if (done.isPresent()) {
                LawWork.contractDone(server, wanted.kingdomId(), hunter.getUUID());
                hunter.sendSystemMessage(Component.translatable(KEY + "head_collected", outlaw.getGameProfile().getName(),
                        MoneyTexts.amount(done.get().paid(), LawTexts.units(server, contract.get().currency()))).withStyle(ChatFormatting.GREEN));
            }
            LOGGER.info("{} collected the head of {} for {} (contract #{})", hunter.getGameProfile().getName(), outlaw.getGameProfile().getName(),
                    wanted.kingdomId(), contract.get().id());
            return true;
        }
        return false;
    }
}
