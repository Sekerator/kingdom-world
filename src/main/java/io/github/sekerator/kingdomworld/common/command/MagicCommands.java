package io.github.sekerator.kingdomworld.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildActions;
import io.github.sekerator.kingdomworld.common.magic.MagicRegistries;
import io.github.sekerator.kingdomworld.common.magic.MagicSavedData;
import io.github.sekerator.kingdomworld.common.magic.MagicSettings;
import io.github.sekerator.kingdomworld.common.magic.MagicWells;
import io.github.sekerator.kingdomworld.common.magic.MagicWork;
import io.github.sekerator.kingdomworld.common.magic.PlayerSpells;
import io.github.sekerator.kingdomworld.common.magic.StaffItem;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.magic.KingdomMagic;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.magic.PlayerSpell;
import io.github.sekerator.kingdomworld.core.magic.Research;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Operator commands of magic under {@code /kingdomworld admin magic}: the energy of kingdoms (show, add, set, dissolve crystals
 * of the storehouses), the spells and research of the towers, the spells players learned, a staff with them, a pulse of the
 * wells for the magic of other mods, and the state of the data. Everything goes through the same calls the screens use.
 */
public final class MagicCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ADMIN = "commands.kingdomworld.admin.magic.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            name -> Component.translatable(ADMIN + "unknown", name));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> SPELLS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(KingdomSpell.values()).map(KingdomSpell::id), builder);
    private static final SuggestionProvider<CommandSourceStack> PLAYER_SPELLS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(PlayerSpell.values()).map(PlayerSpell::id), builder);
    private static final SuggestionProvider<CommandSourceStack> RESEARCH = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Research.values()).map(Research::id), builder);

    private MagicCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("magic")
                .then(Commands.literal("energy")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(MagicCommands::info)
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000_000_000L))
                                                .executes(context -> energy(context, true))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0, 1_000_000_000_000L))
                                                .executes(context -> energy(context, false))))))
                .then(Commands.literal("crystals")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64 * 54)).executes(MagicCommands::crystals)))))
                .then(Commands.literal("dissolve")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 4_096)).executes(MagicCommands::dissolve))))
                .then(Commands.literal("spell")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("spell", StringArgumentType.word()).suggests(SPELLS)
                                        .then(Commands.literal("on").executes(context -> spell(context, true)))
                                        .then(Commands.literal("off").executes(context -> spell(context, false))))))
                .then(Commands.literal("ward")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer()).executes(MagicCommands::ward)))))
                .then(Commands.literal("research")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("research", StringArgumentType.word()).suggests(RESEARCH)
                                        .then(Commands.literal("done").executes(context -> research(context, "done")))
                                        .then(Commands.literal("undo").executes(context -> research(context, "undo")))
                                        .then(Commands.literal("start").executes(context -> research(context, "start"))))))
                .then(Commands.literal("learn")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("spell", StringArgumentType.word()).suggests(PLAYER_SPELLS)
                                        .executes(context -> learn(context, true))
                                        .then(Commands.literal("forget").executes(context -> learn(context, false))))))
                .then(Commands.literal("staff")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(MagicCommands::staff)))
                .then(Commands.literal("cast")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("spell", StringArgumentType.word()).suggests(PLAYER_SPELLS).executes(MagicCommands::cast))))
                .then(Commands.literal("wells")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(MagicCommands::wells))))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())));
    }

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = KingdomNames.normalize(StringArgumentType.getString(context, "kingdom"));
        return KingdomWorldApi.findKingdom(context.getSource().getServer(), name).orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        return profiles.iterator().next();
    }

    private static <T> T named(CommandContext<CommandSourceStack> context, String argument, java.util.function.Function<String, Optional<T>> byId)
            throws CommandSyntaxException {
        String text = StringArgumentType.getString(context, argument);
        return byId.apply(text).orElseThrow(() -> UNKNOWN.create(text));
    }

    private static boolean refused(CommandSourceStack source, Outcome outcome) {
        if (outcome.ok()) {
            return false;
        }
        source.sendFailure(io.github.sekerator.kingdomworld.common.kingdom.KingdomActions.errorMessage(outcome.error()));
        return true;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        MinecraftServer server = source.getServer();
        MagicState magic = MagicWork.state(server);
        Optional<KingdomMagic> mine = magic.find(kingdom.id());
        List<String> spells = new ArrayList<>();
        mine.ifPresent(m -> m.enabled().forEach(spell -> spells.add(spell.id())));
        List<String> done = new ArrayList<>();
        mine.ifPresent(m -> m.done().forEach(research -> done.add(research.id())));
        long crystals = Arrays.stream(GuildActions.crystals(server, kingdom.id())).sum();
        long crystalEnergy = GuildActions.energy(server, kingdom.id());
        long energy = magic.energy(kingdom.id());
        long capacity = magic.capacity(kingdom.id(), MagicSettings.rules());
        String current = mine.flatMap(KingdomMagic::current).map(Research::id).orElse("-");
        String devices = "foci " + magic.devicesOf(kingdom.id(), MagicDevice.Kind.FOCUS).size() + ", accumulators "
                + magic.devicesOf(kingdom.id(), MagicDevice.Kind.ACCUMULATOR).size() + ", gates "
                + magic.devicesOf(kingdom.id(), MagicDevice.Kind.GATE).size() + ", generators "
                + magic.devicesOf(kingdom.id(), MagicDevice.Kind.GENERATOR).size();
        source.sendSuccess(() -> Component.translatable(ADMIN + "info", kingdom.name(), energy, capacity, crystals, crystalEnergy,
                done.isEmpty() ? "-" : String.join(",", done), current, spells.isEmpty() ? "-" : String.join(",", spells), devices), false);
        return (int) Math.min(Integer.MAX_VALUE, energy);
    }

    private static int energy(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        long amount = LongArgumentType.getLong(context, "amount");
        MinecraftServer server = source.getServer();
        long day = TreasuryActions.day(server);
        Outcome outcome = MagicWork.change(server, "set energy", magic -> {
            magic.adminSet(kingdom.id(), add ? magic.energy(kingdom.id()) + amount : amount, day);
            return Outcome.DONE;
        });
        if (refused(source, outcome)) {
            return 0;
        }
        long now = MagicWork.state(server).energy(kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "energy_set", kingdom.name(), now), true);
        return 1;
    }

    /**
     * Puts crystals of a rank into the storehouses of a kingdom; what does not fit is not made.
     */
    private static int crystals(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        io.github.sekerator.kingdomworld.core.guild.Rank rank = named(context, "rank", io.github.sekerator.kingdomworld.core.guild.Rank::byId);
        int count = IntegerArgumentType.getInteger(context, "count");
        int put = 0;
        while (put < count) {
            int part = Math.min(64, count - put);
            ItemStack rest = io.github.sekerator.kingdomworld.common.storage.KingdomStorage.insert(source.getServer(), kingdom.id(),
                    io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem.stack(rank, part), null);
            put += part - rest.getCount();
            if (!rest.isEmpty()) {
                break;
            }
        }
        int stored = put;
        source.sendSuccess(() -> Component.translatable(ADMIN + "crystals", stored, rank.label(), kingdom.name()), true);
        return stored;
    }

    private static int dissolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        int count = IntegerArgumentType.getInteger(context, "count");
        long gained = MagicWork.dissolveFromStorage(source.getServer(), kingdom.id(), count);
        long now = MagicWork.state(source.getServer()).energy(kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "dissolved", kingdom.name(), gained, now), true);
        return (int) Math.min(Integer.MAX_VALUE, gained);
    }

    private static int spell(CommandContext<CommandSourceStack> context, boolean on) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        KingdomSpell spell = named(context, "spell", KingdomSpell::byId);
        MinecraftServer server = source.getServer();
        long units = MagicWork.units(server, kingdom.id(), spell);
        Outcome outcome = MagicWork.change(server, "switch a spell", magic -> magic.setSpell(null, kingdom.id(), spell, on, units,
                TreasuryActions.day(server), MagicSettings.rules(), KingdomWorldApi.state(server)));
        if (refused(source, outcome)) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + (on ? "spell_on" : "spell_off"), spell.id(), kingdom.name()), true);
        return 1;
    }

    private static int ward(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        int x = IntegerArgumentType.getInteger(context, "chunkX");
        int z = IntegerArgumentType.getInteger(context, "chunkZ");
        MinecraftServer server = source.getServer();
        String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(source.getLevel());
        long chunk = io.github.sekerator.kingdomworld.core.territory.ChunkKey.of(x, z);
        boolean own = kingdom.id().equals(KingdomWorldApi.state(server).territory().ownerOf(dimension, chunk));
        Outcome outcome = MagicWork.change(server, "change the ward", magic -> magic.toggleWard(null, kingdom.id(), dimension, chunk, own,
                MagicSettings.rules(), KingdomWorldApi.state(server)));
        if (refused(source, outcome)) {
            return 0;
        }
        int size = MagicWork.state(server).find(kingdom.id()).map(m -> m.ward().size()).orElse(0);
        source.sendSuccess(() -> Component.translatable(ADMIN + "ward", kingdom.name(), x, z, size), true);
        return size;
    }

    private static int research(CommandContext<CommandSourceStack> context, String what) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        Research research = named(context, "research", Research::byId);
        MinecraftServer server = source.getServer();
        Outcome outcome = MagicWork.change(server, "change research", magic -> {
            switch (what) {
                case "start" -> {
                    return magic.startResearch(null, kingdom.id(), research, TreasuryActions.day(server), MagicSettings.rules(),
                            KingdomWorldApi.state(server));
                }
                case "undo" -> magic.adminResearch(kingdom.id(), research, false);
                default -> magic.adminResearch(kingdom.id(), research, true);
            }
            return Outcome.DONE;
        });
        if (refused(source, outcome)) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "research." + what, research.id(), kingdom.name()), true);
        return 1;
    }

    private static int learn(CommandContext<CommandSourceStack> context, boolean known) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = profile(context);
        PlayerSpell spell = named(context, "spell", PlayerSpell::byId);
        Outcome outcome = MagicWork.change(source.getServer(), "teach a spell", magic -> {
            magic.adminLearn(profile.getId(), spell, known);
            return Outcome.DONE;
        });
        if (refused(source, outcome)) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + (known ? "learned" : "forgotten"), profile.getName(), spell.id()), true);
        return 1;
    }

    private static int staff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = profile(context);
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(profile.getId());
        if (player == null) {
            source.sendFailure(Component.translatable(ADMIN + "offline", profile.getName()));
            return 0;
        }
        int slots = MagicSettings.rules().staffSlots;
        ItemStack staff = new ItemStack(MagicRegistries.STAFF.get());
        int slot = 0;
        for (PlayerSpell spell : MagicWork.state(source.getServer()).learned(profile.getId())) {
            if (slot < slots) {
                StaffItem.setSlot(staff, slot++, spell, slots);
            }
        }
        GoodsStacks.give(player, staff);
        int filled = slot;
        source.sendSuccess(() -> Component.translatable(ADMIN + "staff", profile.getName(), filled), true);
        return 1;
    }

    private static int cast(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = profile(context);
        PlayerSpell spell = named(context, "spell", PlayerSpell::byId);
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(profile.getId());
        if (player == null) {
            source.sendFailure(Component.translatable(ADMIN + "offline", profile.getName()));
            return 0;
        }
        var feedback = PlayerSpells.cast(player, spell);
        if (feedback.success()) {
            source.sendSuccess(feedback::message, false);
            return 1;
        }
        source.sendFailure(feedback.message());
        return 0;
    }

    /**
     * One pulse of the wells at a place for a kingdom, with a stand-in player of the kingdom whose mana is empty; the result
     * goes to the log. For checking the integrations on a server without players.
     */
    private static int wells(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = source.getLevel();
        MinecraftServer server = source.getServer();
        if (!MagicWells.any()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "wells.none"), false);
            LOGGER.info("KingdomWorld magic well check: no wells (Iron's Spells 'n Spellbooks and Ars Nouveau are not installed)");
            return 0;
        }
        FakePlayer stand = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("kingdomworld-well".getBytes()), "[KingdomWorld well]"));
        stand.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        long before = MagicWork.state(server).energy(kingdom.id());
        List<MagicWork.WellReport> reports = MagicWork.pulse(server, level, pos, kingdom.id(), List.of(stand));
        for (MagicWork.WellReport report : reports) {
            LOGGER.info("KingdomWorld magic well check at {}: {} +{} for {} unit(s) of energy, {} target(s)", pos.toShortString(), report.well(),
                    Math.round(report.given()), report.energy(), report.targets());
        }
        long spent = before - MagicWork.state(server).energy(kingdom.id());
        int count = reports.size();
        source.sendSuccess(() -> Component.translatable(ADMIN + "wells", kingdom.name(), count, spent), false);
        return count;
    }

    private static int status(CommandSourceStack source) {
        MagicSavedData data = MagicSavedData.get(source.getServer());
        if (data.isReadOnly()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "status.read_only", data.readOnlyReason().orElse("?")), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "status.ok", MagicSavedData.FORMAT_VERSION), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        MagicSavedData data = MagicSavedData.get(source.getServer());
        try {
            Optional<java.nio.file.Path> backup = data.unlock(source.getServer());
            if (backup.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(ADMIN + "unlock.not_needed"), false);
                return 0;
            }
            source.sendSuccess(() -> Component.translatable(ADMIN + "unlocked", backup.get().toString()), true);
            return 1;
        } catch (java.io.IOException e) {
            source.sendFailure(Component.translatable(ADMIN + "unlock.failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }
}
