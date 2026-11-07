package io.github.sekerator.kingdomworld.common.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ZoneSurveys;
import io.github.sekerator.kingdomworld.common.data.KingdomSavedData;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildSettings;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.magic.CrystalEnergy;
import io.github.sekerator.kingdomworld.common.magic.CrystalGeneratorBlockEntity;
import io.github.sekerator.kingdomworld.common.magic.MageBehavior;
import io.github.sekerator.kingdomworld.common.magic.MagicActions;
import io.github.sekerator.kingdomworld.common.magic.MagicDevices;
import io.github.sekerator.kingdomworld.common.magic.MagicRegistries;
import io.github.sekerator.kingdomworld.common.magic.MagicSavedData;
import io.github.sekerator.kingdomworld.common.magic.MagicSettings;
import io.github.sekerator.kingdomworld.common.magic.MagicWork;
import io.github.sekerator.kingdomworld.common.magic.PlayerSpells;
import io.github.sekerator.kingdomworld.common.magic.SpellFireball;
import io.github.sekerator.kingdomworld.common.magic.StaffItem;
import io.github.sekerator.kingdomworld.common.network.data.MagicViews;
import io.github.sekerator.kingdomworld.common.network.packet.MagicPackets;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.EnergyJournal;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import io.github.sekerator.kingdomworld.core.magic.Research;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Game tests of magic: a mage dissolves a crystal and the energy grows by exactly what it held; the ward stops spawns of
 * monsters in its chunks; gates carry a subject and spend energy; a generator gives Forge Energy to a machine and no more
 * than the store holds; the staff spends the energy of a crystal the player carries; a spell of a player does not hurt
 * another player or a citizen in peacetime; the server checks the right to run the mage tower; magic survives saving and the
 * court mage gets the new right on upgrade. Every test runs in a batch of its own and clears what it built.
 */
@GameTestHolder(KingdomWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagicGameTests {
    private static final String EMPTY = "empty";

    private MagicGameTests() {
    }

    // ---------------------------------------------------------------- helpers

    private static ServerPlayer player(GameTestHelper helper, UUID id, String name) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(id, name));
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }

    private static void join(MinecraftServer server, Kingdom kingdom, UUID player, Role role) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        state.membership().invite(kingdom.rulerId(), player, 1L);
        state.membership().acceptInvitation(player, kingdom.id(), 1L);
        if (role != Role.SUBJECT) {
            state.membership().setRole(kingdom.rulerId(), player, role);
        }
    }

    private static long day(MinecraftServer server) {
        return TreasuryActions.day(server);
    }

    private static void end(GameTestHelper helper, Kingdom kingdom, BlockPos from, BlockPos to, Citizen... citizens) {
        MinecraftServer server = helper.getLevel().getServer();
        MagicSettings.override(null);
        MagicWork.overrideWardRolls(null);
        MagicWork.state(server).forgetKingdom(kingdom.id());
        for (SpellFireball fireball : helper.getLevel().getEntitiesOfClass(SpellFireball.class, new AABB(from, to).inflate(32))) {
            fireball.discard();
        }
        CitizenGameTests.finish(helper, kingdom, citizens);
        GatheringGameTests.clear(helper.getLevel(), from, to);
    }

    // ---------------------------------------------------------------- mages

    @GameTest(template = EMPTY, batch = "kingdomworld_magic_mage")
    public static void aMageDissolvesACrystalAndTheEnergyGrowsByExactlyItsStore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Mage Tower", UUID.randomUUID());
        Citizen mage = null;
        try {
            helper.setBlock(new BlockPos(6, 0, 1), MagicRegistries.MAGIC_FOCUS.get());
            WorldPos postPos = GatheringGameTests.post(helper, new BlockPos(5, 0, 1), Professions.MAGE.id());
            helper.setBlock(new BlockPos(5, 0, 2), Blocks.CHEST);
            WorkPost post = PostWork.post(server, postPos).orElseThrow();
            ZoneSurveys.surveyNow(server, post);
            helper.assertTrue(post.stations().getOrDefault(io.github.sekerator.kingdomworld.core.production.Stations.MAGIC_FOCUS, 0) == 1,
                    "The survey did not find the focus");
            helper.assertTrue(!new MageBehavior().wanted(post, Map.of()).isEmpty(), "The post did not ask the carriers for crystals");
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 0, 2)));
            chest.setItem(0, MonsterCrystalItem.stack(Rank.E, 1));
            chest.setItem(1, MonsterCrystalItem.started(Rank.D, 37));
            MagicState magic = MagicWork.state(server);
            long before = magic.energy(kingdom.id());
            long fresh = GuildSettings.rules().crystalEnergy(Rank.E);
            helper.assertTrue(MageBehavior.dissolve(server, post, false) == MageBehavior.Dissolved.DONE, "The first crystal was not dissolved");
            helper.assertTrue(MageBehavior.dissolve(server, post, false) == MageBehavior.Dissolved.DONE, "The second crystal was not dissolved");
            helper.assertTrue(magic.energy(kingdom.id()) == before + fresh + 37, "The energy grew by " + (magic.energy(kingdom.id()) - before)
                    + ", not by " + (fresh + 37));
            helper.assertTrue(chest.getItem(0).isEmpty() && chest.getItem(1).isEmpty(), "The crystals stayed in the container");
            helper.assertTrue(MageBehavior.dissolve(server, post, false) == MageBehavior.Dissolved.NO_CRYSTALS, "Energy came from nothing");
            helper.assertTrue(magic.find(kingdom.id()).orElseThrow().journal().totals(day(server), EnergyJournal.Period.TODAY)
                    .get(EnergyCategory.CRYSTALS).income() == fresh + 37, "The journal did not write the crystals");

            // A mage far from players works the same way.
            mage = CitizenGameTests.newcomer(helper, kingdom, 1, 3);
            Citizen worker = mage;
            KingdomWorldApi.change(server, "test", state -> state.setProfession(null, worker.id(), Professions.MAGE.id()));
            helper.assertTrue(KingdomWorldApi.state(server).citizens().assignWorkplace(mage, postPos), "The mage got no seat at the post");
            chest.setItem(0, MonsterCrystalItem.stack(Rank.F, 1));
            long middle = magic.energy(kingdom.id());
            new MageBehavior().workAbstractly(server, mage, 24_000);
            helper.assertTrue(magic.energy(kingdom.id()) == middle + GuildSettings.rules().crystalEnergy(Rank.F),
                    "The mage far from players did not dissolve exactly one crystal");

            // A full store refuses the crystal and keeps it.
            magic.adminSet(kingdom.id(), magic.capacity(kingdom.id(), MagicSettings.rules()), day(server));
            chest.setItem(0, MonsterCrystalItem.stack(Rank.C, 1));
            long full = magic.energy(kingdom.id());
            helper.assertTrue(MageBehavior.dissolve(server, post, false) == MageBehavior.Dissolved.STORE_FULL, "A full store took a crystal");
            helper.assertTrue(MonsterCrystalItem.isCrystal(chest.getItem(0)) && magic.energy(kingdom.id()) == full, "The full store lost a crystal");
        } finally {
            if (mage != null) {
                end(helper, kingdom, from, to, mage);
            } else {
                end(helper, kingdom, from, to);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- spells of the kingdom

    @GameTest(template = EMPTY, batch = "kingdomworld_magic_ward")
    public static void theWardStopsSpawnsOfMonstersInItsChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Warded Vale", UUID.randomUUID());
        try {
            MagicState magic = MagicWork.state(server);
            KingdomWorldState world = KingdomWorldApi.state(server);
            MagicRules.Builder builder = MagicSettings.configured();
            builder.wardReduction = 1.0;
            MagicSettings.override(builder.build());
            MagicRules rules = MagicSettings.rules();
            BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
            String dimension = Dimensions.id(level);
            long chunk = ChunkKey.of(at.getX() >> 4, at.getZ() >> 4);
            magic.adminResearch(kingdom.id(), Research.WARD, true);
            magic.adminSet(kingdom.id(), 1_000, day(server));
            helper.assertTrue(magic.toggleWard(null, kingdom.id(), dimension, chunk, true, rules, world).ok(), "The chunk could not be warded");
            Zombie zombie = new Zombie(level);
            Cow cow = new Cow(EntityType.COW, level);
            helper.assertTrue(!MagicWork.wardStops(level, at, zombie), "A ward that is off stopped a monster");
            helper.assertTrue(magic.setSpell(null, kingdom.id(), KingdomSpell.WARD, true, 1, day(server), rules, world).ok(),
                    "The ward did not switch on");
            helper.assertTrue(magic.energy(kingdom.id()) == 1_000 - rules.spellPrice(KingdomSpell.WARD), "The ward did not pay for today");
            helper.assertTrue(MagicWork.wardStops(level, at, zombie), "The ward let a monster spawn");
            helper.assertTrue(!MagicWork.wardStops(level, at, cow), "The ward stopped an animal");
            helper.assertTrue(!MagicWork.wardStops(level, at.offset(16 * 20, 0, 0), zombie), "The ward reached a chunk it does not cover");
            // With half of the spawns stopped, half of the monsters come.
            builder.wardReduction = 0.5;
            MagicSettings.override(builder.build());
            double[] rolls = {0.1, 0.9};
            int[] next = {0};
            MagicWork.overrideWardRolls(() -> rolls[next[0]++ % rolls.length]);
            int stopped = 0;
            for (int i = 0; i < 20; i++) {
                if (MagicWork.wardStops(level, at, zombie)) {
                    stopped++;
                }
            }
            helper.assertTrue(stopped == 10, "The ward stopped " + stopped + " of 20 spawns, not 10");
            magic.setSpell(null, kingdom.id(), KingdomSpell.WARD, false, 0, day(server), rules, world);
            helper.assertTrue(!MagicWork.wardStops(level, at, zombie), "The ward works while switched off");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_magic_gates")
    public static void gatesCarryASubjectAndSpendEnergy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(9, 3, 9));
        GatheringGameTests.clear(level, from, to);
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Gate Keep", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        ServerPlayer outsider = player(helper, UUID.randomUUID(), "kw-outsider");
        try {
            MagicState magic = MagicWork.state(server);
            MagicRules rules = MagicSettings.rules();
            helper.setBlock(new BlockPos(1, 0, 6), MagicRegistries.MAGIC_GATE.get());
            helper.setBlock(new BlockPos(8, 0, 6), MagicRegistries.MAGIC_GATE.get());
            BlockPos first = helper.absolutePos(new BlockPos(1, 0, 6));
            BlockPos second = helper.absolutePos(new BlockPos(8, 0, 6));
            helper.assertTrue(magic.devicesOf(kingdom.id(), MagicDevice.Kind.GATE).isEmpty(), "Gates placed without a player were set up");
            MagicDevices.placedBy(level, first, MagicDevice.Kind.GATE, king);
            MagicDevices.placedBy(level, second, MagicDevice.Kind.GATE, king);
            List<MagicDevice> gates = magic.devicesOf(kingdom.id(), MagicDevice.Kind.GATE);
            helper.assertTrue(gates.size() == 2, "The gates were not recorded for the kingdom: " + gates.size());
            long target = magic.device(MagicDevices.at(level, second)).orElseThrow().id();
            magic.adminResearch(kingdom.id(), Research.WARD, true);
            magic.adminResearch(kingdom.id(), Research.GATES, true);
            magic.adminSet(kingdom.id(), 100, day(server));
            king.setPos(first.getX() + 0.5, first.getY() + 1, first.getZ() + 0.5);
            helper.assertTrue(!MagicActions.travel(king, first, target).success(), "A gate worked while the spell was off");
            helper.assertTrue(magic.setSpell(kingId, kingdom.id(), KingdomSpell.GATES, true, 0, day(server), rules,
                    KingdomWorldApi.state(server)).ok(), "The king could not switch the gates on");
            Optional<MagicViews.GateView> view = MagicActions.gateView(king, first);
            helper.assertTrue(view.isPresent() && view.get().gates().size() == 1 && view.get().gates().get(0).id() == target,
                    "The gate does not show the other gate");
            outsider.setPos(first.getX() + 0.5, first.getY() + 1, first.getZ() + 0.5);
            helper.assertTrue(MagicActions.gateView(outsider, first).isEmpty(), "An outsider saw where the gates lead");
            helper.assertTrue(!MagicActions.travel(outsider, first, target).success(), "An outsider used the gates");
            helper.assertTrue(magic.energy(kingdom.id()) == 100, "A refused trip cost energy");
            helper.assertTrue(MagicActions.travel(king, first, target).success(), "The king could not travel");
            helper.assertTrue(king.blockPosition().distManhattan(second.above()) <= 1, "The king did not arrive at the other gate: "
                    + king.blockPosition());
            long price = rules.spellPrice(KingdomSpell.GATES);
            helper.assertTrue(magic.energy(kingdom.id()) == 100 - price, "The trip did not cost " + price);
            helper.assertTrue(magic.find(kingdom.id()).orElseThrow().journal().totals(day(server), EnergyJournal.Period.TODAY)
                    .get(EnergyCategory.GATES).expense() == price, "The journal did not write the trip");
            magic.adminSet(kingdom.id(), price - 1, day(server));
            king.setPos(second.getX() + 0.5, second.getY() + 1, second.getZ() + 0.5);
            long back = magic.device(MagicDevices.at(level, first)).orElseThrow().id();
            helper.assertTrue(!MagicActions.travel(king, second, back).success(), "A trip without energy went through");
            helper.assertTrue(magic.energy(kingdom.id()) == price - 1, "A refused trip changed the energy");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- machines

    @GameTest(template = EMPTY, batch = "kingdomworld_magic_generator")
    public static void aGeneratorGivesForgeEnergyAndNoMoreThanTheStoreHolds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        GatheringGameTests.clear(level, from, to);
        UUID kingId = UUID.randomUUID();
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Generator Works", kingId);
        ServerPlayer king = player(helper, kingId, "kw-king");
        ServerPlayer subject = player(helper, UUID.randomUUID(), "kw-subject");
        join(server, kingdom, subject.getUUID(), Role.SUBJECT);
        try {
            MagicRules.Builder builder = MagicSettings.configured();
            builder.feRate = 40;
            builder.feMaxPerTick = 400;
            MagicSettings.override(builder.build());
            MagicState magic = MagicWork.state(server);
            helper.setBlock(new BlockPos(4, 0, 4), MagicRegistries.CRYSTAL_GENERATOR.get());
            BlockPos pos = helper.absolutePos(new BlockPos(4, 0, 4));
            CrystalGeneratorBlockEntity generator = (CrystalGeneratorBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(generator != null, "The generator has no block entity");
            // Placed without a player (a machine or a command), or by a subject without the right: it gives nothing.
            magic.adminSet(kingdom.id(), 1_000, day(server));
            EnergyStorage idle = new EnergyStorage(1_000_000);
            helper.assertTrue(magic.devicesOf(kingdom.id(), MagicDevice.Kind.GENERATOR).isEmpty(), "A generator placed without a player was set up");
            helper.assertTrue(generator.supply(idle, Integer.MAX_VALUE) == 0 && magic.energy(kingdom.id()) == 1_000,
                    "A generator nobody set up gave the kingdom's energy away");
            MagicDevices.placedBy(level, pos, MagicDevice.Kind.GENERATOR, subject);
            helper.assertTrue(magic.devicesOf(kingdom.id(), MagicDevice.Kind.GENERATOR).isEmpty(), "A subject without the right set up a generator");
            helper.assertTrue(generator.supply(idle, Integer.MAX_VALUE) == 0 && idle.getEnergyStored() == 0, "A subject's generator gave energy");
            MagicDevices.placedBy(level, pos, MagicDevice.Kind.GENERATOR, king);
            helper.assertTrue(magic.devicesOf(kingdom.id(), MagicDevice.Kind.GENERATOR).size() == 1, "The king could not set up the generator");
            IEnergyStorage capability = generator.getCapability(ForgeCapabilities.ENERGY, null).orElseThrow(IllegalStateException::new);
            helper.assertTrue(!capability.canReceive() && capability.receiveEnergy(1_000, false) == 0, "The generator takes Forge Energy");
            magic.adminSet(kingdom.id(), 3, day(server));
            EnergyStorage machine = new EnergyStorage(1_000_000);
            int given = generator.supply(machine, Integer.MAX_VALUE);
            helper.assertTrue(given == 120 && machine.getEnergyStored() == 120, "Three units should give 120 FE, not " + given);
            helper.assertTrue(magic.energy(kingdom.id()) == 0, "The store kept energy it gave away");
            helper.assertTrue(generator.supply(machine, Integer.MAX_VALUE) == 0 && machine.getEnergyStored() == 120,
                    "The generator gave more than the store held");
            magic.adminSet(kingdom.id(), 1_000, day(server));
            EnergyStorage small = new EnergyStorage(50);
            helper.assertTrue(generator.supply(small, Integer.MAX_VALUE) == 50, "A small machine got the wrong amount");
            helper.assertTrue(magic.energy(kingdom.id()) == 1_000 - 2 && generator.buffer() == 30,
                    "The generator paid for more than the machine took: " + magic.energy(kingdom.id()) + ", buffer " + generator.buffer());
            // Everything above happened in one tick: pulling gets what is left of the tick's limit.
            helper.assertTrue(capability.extractEnergy(1_000, false) == 400 - 120 - 50, "Pulling ignored the limit per tick");
            helper.assertTrue(magic.find(kingdom.id()).orElseThrow().journal().totals(day(server), EnergyJournal.Period.TODAY)
                    .get(EnergyCategory.MACHINES).expense() > 0, "The journal did not write the machines");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- spells of players

    @GameTest(template = EMPTY, batch = "kingdomworld_magic_staff")
    public static void theStaffSpendsTheEnergyOfACrystalThePlayerCarries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Staff School", UUID.randomUUID());
        // A stand-in player with a connection that drops its packets: spells give effects, which are sent to the client.
        ServerPlayer player = net.minecraftforge.common.util.FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "kw-apprentice"));
        BlockPos stand = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.getInventory().clearContent();
        try {
            MagicState magic = MagicWork.state(server);
            MagicRules rules = MagicSettings.rules();
            helper.assertTrue(!PlayerSpells.cast(player, PlayerSpell.FIREBALL).success(), "A spell nobody learned was cast");
            magic.adminLearn(player.getUUID(), PlayerSpell.FIREBALL, true);
            magic.adminLearn(player.getUUID(), PlayerSpell.LIGHT, true);
            helper.assertTrue(!PlayerSpells.cast(player, PlayerSpell.FIREBALL).success(), "A spell was cast without crystals");
            long fresh = GuildSettings.rules().crystalEnergy(Rank.E);
            player.getInventory().add(MonsterCrystalItem.stack(Rank.E, 2));
            helper.assertTrue(PlayerSpells.cast(player, PlayerSpell.FIREBALL).success(), "The fireball was not cast");
            long expected = 2 * fresh - rules.cost(PlayerSpell.FIREBALL);
            helper.assertTrue(CrystalEnergy.carried(player) == expected, "The crystals hold " + CrystalEnergy.carried(player) + ", not "
                    + expected);
            ItemStack started = ItemStack.EMPTY;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (MonsterCrystalItem.isStarted(player.getInventory().getItem(slot))) {
                    started = player.getInventory().getItem(slot);
                }
            }
            helper.assertTrue(started.getCount() == 1 && MonsterCrystalItem.energy(started) == fresh - rules.cost(PlayerSpell.FIREBALL),
                    "The started crystal does not remember what is left");
            helper.assertTrue(!PlayerSpells.cast(player, PlayerSpell.FIREBALL).success(), "The fireball was cast during its cooldown");
            helper.assertTrue(CrystalEnergy.carried(player) == expected, "A refused cast took energy");
            helper.assertTrue(PlayerSpells.cast(player, PlayerSpell.LIGHT).success() && player.hasEffect(MobEffects.NIGHT_VISION),
                    "The light was not cast");
            helper.assertTrue(CrystalEnergy.carried(player) == expected - rules.cost(PlayerSpell.LIGHT), "The light took the wrong energy");

            ItemStack staff = new ItemStack(MagicRegistries.STAFF.get());
            StaffItem.setSlot(staff, 1, PlayerSpell.LIGHT, rules.staffSlots);
            StaffItem.setSlot(staff, 2, PlayerSpell.FIREBALL, rules.staffSlots);
            helper.assertTrue(StaffItem.selected(staff, rules.staffSlots).equals(Optional.of(PlayerSpell.LIGHT)), "The first spell was not chosen");
            helper.assertTrue(StaffItem.selectNext(staff, rules.staffSlots) == 2 && StaffItem.selectNext(staff, rules.staffSlots) == 1,
                    "Choosing skips empty slots wrongly");
        } finally {
            MagicWork.state(server).adminLearn(player.getUUID(), PlayerSpell.FIREBALL, false);
            MagicWork.state(server).adminLearn(player.getUUID(), PlayerSpell.LIGHT, false);
            PlayerSpells.forget(player.getUUID());
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "kingdomworld_magic_pvp")
    public static void aSpellOfAPlayerHurtsNoOtherPlayerAndNoCitizenInPeacetime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        UUID kingId = UUID.randomUUID();
        Kingdom home = CitizenGameTests.found(helper, "GameTest Peaceful Realm", kingId);
        CreateKingdomResult other = KingdomWorldApi.createKingdom(server, "GameTest Foreign Realm", UUID.randomUUID());
        Kingdom foreign = ((CreateKingdomResult.Created) other).kingdom();
        ServerPlayer caster = player(helper, kingId, "kw-caster");
        ServerPlayer friend = player(helper, UUID.randomUUID(), "kw-friend");
        ServerPlayer stranger = player(helper, foreign.rulerId(), "kw-stranger");
        ServerPlayer wanderer = player(helper, UUID.randomUUID(), "kw-wanderer");
        join(server, home, friend.getUUID(), Role.SUBJECT);
        Citizen citizen = CitizenGameTests.newcomer(helper, home, 3, 3);
        try {
            CitizenBodies.force(citizen.id(), true);
            CitizenEntity body = CitizenBodies.spawnAt(level, citizen, helper.absolutePos(new BlockPos(3, 1, 3)));
            helper.assertTrue(body != null, "No body for the citizen");
            SpellFireball fireball = new SpellFireball(level, caster, 0.0, 0.0, 1.0, 6.0f);
            for (net.minecraft.world.entity.LivingEntity victim : List.of(friend, stranger, wanderer, body)) {
                float health = victim.getHealth();
                helper.assertTrue(!fireball.strike(victim) && victim.getHealth() == health, "The fireball hurt " + victim.getName().getString());
            }
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 1, 5));
            zombie.setNoAi(true);
            float health = zombie.getHealth();
            helper.assertTrue(fireball.strike(zombie) && zombie.getHealth() < health, "The fireball did not hurt a monster");
            zombie.discard();
            fireball.discard();
            // A fireball of a mage hurts monsters only.
            SpellFireball mages = new SpellFireball(level, body, 0.0, 0.0, 1.0, 6.0f);
            helper.assertTrue(!mages.strike(caster), "A mage's fireball hurt a player");
            mages.discard();
        } finally {
            KingdomWorldApi.dissolveKingdom(server, foreign.id());
            end(helper, home, from, to, citizen);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- rights and data

    @GameTest(template = EMPTY, batch = "kingdomworld_magic_rights")
    public static void theServerChecksTheRightToRunTheMageTower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos from = helper.absolutePos(new BlockPos(-1, 0, -1));
        BlockPos to = helper.absolutePos(new BlockPos(8, 3, 8));
        Kingdom kingdom = CitizenGameTests.found(helper, "GameTest Mage Council", UUID.randomUUID());
        ServerPlayer subject = player(helper, UUID.randomUUID(), "kw-subject");
        ServerPlayer mage = player(helper, UUID.randomUUID(), "kw-court-mage");
        ServerPlayer outsider = player(helper, UUID.randomUUID(), "kw-outsider");
        join(server, kingdom, subject.getUUID(), Role.SUBJECT);
        join(server, kingdom, mage.getUUID(), Role.COURT_MAGE);
        try {
            MagicState magic = MagicWork.state(server);
            magic.adminSet(kingdom.id(), 10_000, day(server));
            helper.assertTrue(KingdomWorldApi.state(server).membership().hasPermission(mage.getUUID(), kingdom.id(), Permission.MANAGE_MAGIC),
                    "The court mage should run the mage tower");
            MagicPackets.TabChange research = new MagicPackets.TabChange(kingdom.id(), MagicPackets.TabChange.Action.RESEARCH,
                    Research.WARD.ordinal(), true);
            helper.assertTrue(!MagicActions.applyTab(server, subject.getUUID(), subject, research).success(), "A subject started a research");
            helper.assertTrue(magic.energy(kingdom.id()) == 10_000, "The refused research took energy");
            helper.assertTrue(MagicActions.applyTab(server, mage.getUUID(), mage, research).success(), "The court mage could not start a research");
            helper.assertTrue(magic.energy(kingdom.id()) == 10_000 - MagicSettings.rules().researchCost(Research.WARD), "The research cost nothing");
            magic.adminResearch(kingdom.id(), Research.HEALING, true);
            MagicPackets.TabChange healing = new MagicPackets.TabChange(kingdom.id(), MagicPackets.TabChange.Action.SPELL,
                    KingdomSpell.HEALING.ordinal(), true);
            helper.assertTrue(!MagicActions.applyTab(server, subject.getUUID(), subject, healing).success(), "A subject switched a spell on");
            helper.assertTrue(MagicActions.applyTab(server, mage.getUUID(), mage, healing).success(), "The court mage could not switch a spell on");
            helper.assertTrue(!MagicActions.tabView(server, kingdom.id(), subject).orElseThrow().canManage(), "The tab lets a subject run the tower");
            helper.assertTrue(MagicActions.tabView(server, kingdom.id(), mage).orElseThrow().canManage(), "The tab does not let the court mage");
            helper.assertTrue(MagicActions.tabView(server, kingdom.id(), outsider).isEmpty(), "An outsider saw the magic tab");
            BlockPos land = helper.absolutePos(new BlockPos(4, 0, 4));
            helper.assertTrue(!MagicDevices.mayPlace(subject, land, MagicDevice.Kind.GENERATOR), "A subject may place a generator");
            helper.assertTrue(!MagicDevices.mayPlace(outsider, land, MagicDevice.Kind.GENERATOR), "An outsider may place a generator");
            helper.assertTrue(MagicDevices.mayPlace(mage, land, MagicDevice.Kind.GENERATOR), "The court mage may not place a generator");
            helper.assertTrue(!MagicDevices.mayPlace(mage, land.offset(16 * 30, 0, 0), MagicDevice.Kind.GENERATOR),
                    "A generator may stand outside the kingdom's land");
        } finally {
            end(helper, kingdom, from, to);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magicSurvivesSavingAndNewerDataStaysReadOnly(GameTestHelper helper) {
        MagicSavedData data = new MagicSavedData();
        MagicState magic = data.state();
        UUID kingdom = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        magic.adminSet(kingdom, 4_321, 7);
        magic.adminResearch(kingdom, Research.WARD, true);
        magic.adminResearch(kingdom, Research.TAILWIND, true);
        magic.restoreDevice(new MagicDevice(4, MagicDevice.Kind.ACCUMULATOR, kingdom, new WorldPos("minecraft:overworld", 1, 64, 2)));
        magic.adminLearn(player, PlayerSpell.BLINK, true);
        CompoundTag saved = data.save(new CompoundTag());
        MagicSavedData reloaded = MagicSavedData.load(saved);
        helper.assertTrue(!reloaded.isReadOnly(), "Saved magic could not be read back: " + reloaded.readOnlyReason());
        MagicState back = reloaded.state();
        helper.assertTrue(back.energy(kingdom) == 4_321, "The energy changed on the way");
        helper.assertTrue(back.find(kingdom).orElseThrow().done().equals(Set.of(Research.WARD, Research.TAILWIND)), "Research changed");
        helper.assertTrue(back.devices().size() == 1 && back.nextDeviceId() == 5, "Devices changed");
        helper.assertTrue(back.knows(player, PlayerSpell.BLINK), "Learned spells were lost");
        helper.assertTrue(back.find(kingdom).orElseThrow().journal().sum(7, EnergyJournal.Period.TODAY).income() == 4_321, "The journal was lost");
        CompoundTag again = reloaded.save(new CompoundTag());
        helper.assertTrue(again.equals(saved), "Saving the loaded magic gave different data");

        CompoundTag newer = saved.copy();
        newer.putInt("formatVersion", MagicSavedData.FORMAT_VERSION + 1);
        MagicSavedData locked = MagicSavedData.load(newer);
        helper.assertTrue(locked.isReadOnly() && locked.state().energy(kingdom) == 4_321, "Newer data was not read-only or not shown");
        helper.assertTrue(locked.save(new CompoundTag()).equals(newer), "Read-only data was not kept as it was");

        CompoundTag broken = saved.copy();
        broken.getList("learned", Tag.TAG_COMPOUND).getCompound(0).getList("spells", Tag.TAG_STRING).add(StringTag.valueOf("meteor"));
        helper.assertTrue(MagicSavedData.load(broken).isReadOnly(), "An unknown spell did not keep the data read-only");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theCourtMageGetsTheRightToRunTheTowerOnUpgrade(GameTestHelper helper) {
        KingdomSavedData data = new KingdomSavedData();
        UUID king = UUID.randomUUID();
        UUID mage = UUID.randomUUID();
        Kingdom kingdom = ((CreateKingdomResult.Created) data.state().found(UUID.randomUUID(), "Upgraded Tower Realm", 0x654321, king,
                new io.github.sekerator.kingdomworld.core.territory.Capital("minecraft:overworld", 600, 64, 600), 1L,
                io.github.sekerator.kingdomworld.core.state.KingdomRules.DEFAULT)).kingdom();
        data.state().membership().invite(king, mage, 2L);
        data.state().membership().acceptInvitation(mage, kingdom.id(), 3L);
        data.state().membership().setRole(king, mage, Role.COURT_MAGE);
        CompoundTag saved = data.save(new CompoundTag());
        // Data of the release before the mage tower: the guild's upgrade only, and no court mage with the right.
        ListTag upgrades = new ListTag();
        upgrades.add(StringTag.valueOf("guild_rights"));
        saved.put("upgrades", upgrades);
        ListTag rights = saved.getList("kingdoms", Tag.TAG_COMPOUND).getCompound(0).getCompound("permissions").getList("court_mage", Tag.TAG_STRING);
        for (int i = rights.size() - 1; i >= 0; i--) {
            if (rights.getString(i).equals("manage_magic")) {
                rights.remove(i);
            }
        }
        KingdomSavedData upgraded = KingdomSavedData.load(saved);
        helper.assertTrue(!upgraded.isReadOnly(), "Upgraded data is read-only: " + upgraded.readOnlyReason());
        helper.assertTrue(upgraded.state().membership().hasPermission(mage, kingdom.id(), Permission.MANAGE_MAGIC),
                "The court mage did not get the right to run the mage tower");
        CompoundTag again = upgraded.save(new CompoundTag());
        helper.assertTrue(again.getList("upgrades", Tag.TAG_STRING).contains(StringTag.valueOf("magic_rights")), "The upgrade was not noted");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magicPacketsSurviveEncoding(GameTestHelper helper) {
        List<Object> packets = List.of(
                new MagicPackets.TabChange(UUID.randomUUID(), MagicPackets.TabChange.Action.SPELL, 3, true),
                new MagicPackets.FocusAction(new BlockPos(1, 2, 3), MagicPackets.FocusAction.Action.BIND, -1, 2),
                new MagicPackets.GateTravel(new BlockPos(4, 5, 6), 9));
        for (Object packet : packets) {
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            Object back;
            if (packet instanceof MagicPackets.TabChange change) {
                change.encode(buf);
                back = MagicPackets.TabChange.decode(buf);
            } else if (packet instanceof MagicPackets.FocusAction action) {
                action.encode(buf);
                back = MagicPackets.FocusAction.decode(buf);
            } else {
                ((MagicPackets.GateTravel) packet).encode(buf);
                back = MagicPackets.GateTravel.decode(buf);
            }
            helper.assertTrue(back.equals(packet), packet.getClass().getSimpleName() + " changed on the way");
        }
        MagicViews.TabView view = new MagicViews.TabView(UUID.randomUUID(), true, 120, 5_000, 1, 2, 7, 210,
                List.of(List.of(new MagicViews.JournalRow(EnergyCategory.CRYSTALS, 30, 0)), List.of(), List.of()),
                List.of(new MagicViews.MageRow(UUID.randomUUID(), net.minecraft.network.chat.Component.literal("Ada"), 12, 130,
                        io.github.sekerator.kingdomworld.core.citizen.WorkStatus.WORKING)),
                List.of(new MagicViews.ResearchRow(Research.GATES, MagicViews.ResearchState.LOCKED, 2_000, 60, 0)), 3,
                List.of(new MagicViews.SpellRow(KingdomSpell.WARD, true, false, 5, 4)), 4, 64, true, true,
                List.of(new MagicViews.DeviceRow(2, MagicDevice.Kind.GATE, "minecraft:overworld", new BlockPos(1, 2, 3))), List.of(), 40, 400);
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        view.encode(buf);
        helper.assertTrue(MagicViews.TabView.decode(buf).equals(view), "The magic tab changed on the way");
        helper.succeed();
    }
}
