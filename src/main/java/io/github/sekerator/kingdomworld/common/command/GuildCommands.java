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
import io.github.sekerator.kingdomworld.common.guild.GuildSavedData;
import io.github.sekerator.kingdomworld.common.guild.GuildSettings;
import io.github.sekerator.kingdomworld.common.guild.GuildTexts;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.guild.MonsterRanks;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.GuildMember;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.MonsterRanking;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.guild.Squad;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Operator commands of the guild under {@code /kingdomworld admin guild}: ranks, points and crystals of players, contracts
 * (post for a kingdom, take, count kills, run out, call off), squads (form, send, bring back), adventurers, the ranks of the
 * monsters of the game and of the mods, the money in escrow and the energy of crystals, and the state of the guild's data.
 * Everything goes through the same calls the screens use.
 */
public final class GuildCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.guild.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final DynamicCommandExceptionType NO_RANK = new DynamicCommandExceptionType(
            name -> Component.translatable(ADMIN + "unknown_rank", name));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> RANKS = (context, builder) ->
            SharedSuggestionProvider.suggest(java.util.Arrays.stream(Rank.values()).map(Rank::id), builder);
    private static final SuggestionProvider<CommandSourceStack> KINDS = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("hunt", "gather", "bounty"), builder);

    private GuildCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("guild")
                .then(Commands.literal("rank")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                        .executes(GuildCommands::rank))))
                .then(Commands.literal("points")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", LongArgumentType.longArg(-GuildMember.MAX_POINTS, GuildMember.MAX_POINTS))
                                        .executes(GuildCommands::points))))
                .then(Commands.literal("info")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(GuildCommands::info)))
                .then(Commands.literal("join")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(GuildCommands::join))))
                .then(Commands.literal("crystal")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                        .executes(context -> crystal(context, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> crystal(context, IntegerArgumentType.getInteger(context, "count")))))))
                .then(Commands.literal("contract")
                        .then(Commands.literal("list")
                                .executes(context -> contracts(context.getSource(), null))
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> contracts(context.getSource(), kingdom(context, "kingdom")))))
                        .then(Commands.literal("post")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                                                .then(Commands.argument("target", StringArgumentType.string())
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, Contract.MAX_COUNT))
                                                                .then(Commands.argument("reward", LongArgumentType.longArg(1))
                                                                        .executes(context -> post(context, 2))
                                                                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                                                                .executes(context -> post(context,
                                                                                        IntegerArgumentType.getInteger(context, "days"))))))))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(GuildCommands::take))))
                        .then(Commands.literal("kill")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("monster", ResourceLocationArgument.id())
                                                .executes(context -> kill(context, 1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 4_096))
                                                        .executes(context -> kill(context, IntegerArgumentType.getInteger(context, "count")))))))
                        .then(Commands.literal("expire")
                                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(GuildCommands::expire)))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("id", LongArgumentType.longArg(1)).executes(GuildCommands::cancel))))
                .then(Commands.literal("squad")
                        .then(Commands.literal("list")
                                .executes(context -> squads(context.getSource(), null))
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> squads(context.getSource(), kingdom(context, "kingdom")))))
                        .then(Commands.literal("form")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("size", IntegerArgumentType.integer(1, 16)).executes(GuildCommands::form))))
                        .then(Commands.literal("send")
                                .then(Commands.argument("squad", LongArgumentType.longArg(1))
                                        .then(Commands.argument("contract", LongArgumentType.longArg(1)).executes(GuildCommands::send))))
                        .then(Commands.literal("return").executes(context -> bringBack(context.getSource()))))
                .then(Commands.literal("hire")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(GuildCommands::hire))))
                .then(Commands.literal("monsters")
                        .executes(context -> monsters(context.getSource(), ""))
                        .then(Commands.argument("filter", StringArgumentType.greedyString())
                                .executes(context -> monsters(context.getSource(), StringArgumentType.getString(context, "filter")))))
                .then(Commands.literal("money").executes(context -> money(context.getSource())))
                .then(Commands.literal("energy")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS).executes(GuildCommands::energy)))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())));
    }

    // ---------------------------------------------------------------- helpers

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        String name = KingdomNames.normalize(StringArgumentType.getString(context, argument));
        return KingdomWorldApi.findKingdom(context.getSource().getServer(), name).orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        return profiles.iterator().next();
    }

    private static Rank rankArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String text = StringArgumentType.getString(context, "rank");
        return Rank.byId(text).orElseThrow(() -> NO_RANK.create(text));
    }

    private static boolean writable(CommandSourceStack source) {
        if (GuildWork.isReadOnly(source.getServer())) {
            source.sendFailure(KingdomActions.errorMessage(KingdomError.READ_ONLY));
            return false;
        }
        return true;
    }

    private static int refused(CommandSourceStack source, Outcome outcome) {
        source.sendFailure(Component.translatable(ADMIN + "failed", KingdomActions.errorMessage(outcome.error())));
        return 0;
    }

    private static String kingdomName(MinecraftServer server, UUID id) {
        return KingdomWorldApi.kingdom(server, id).map(Kingdom::name).orElse("?");
    }

    // ---------------------------------------------------------------- players

    private static int rank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = profile(context);
        Rank rank = rankArgument(context);
        if (!writable(source)) {
            return 0;
        }
        GuildWork.state(source.getServer()).setRank(profile.getId(), rank, KingdomWorldApi.gameTime(source.getServer()));
        source.sendSuccess(() -> Component.translatable(ADMIN + "rank.set", profile.getName(), rank.label()), true);
        return 1;
    }

    private static int points(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = profile(context);
        long amount = LongArgumentType.getLong(context, "amount");
        if (!writable(source)) {
            return 0;
        }
        Optional<GuildMember> member = GuildWork.state(source.getServer()).addPoints(profile.getId(), amount);
        if (member.isEmpty()) {
            source.sendFailure(Component.translatable(ADMIN + "info.none", profile.getName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "points.set", profile.getName(), member.get().points()), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = profile(context);
        GuildState guild = GuildWork.state(source.getServer());
        Optional<GuildMember> member = guild.member(profile.getId());
        if (member.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "info.none", profile.getName()), false);
            return 0;
        }
        GuildMember m = member.get();
        int held = guild.takenBy(profile.getId()).size();
        source.sendSuccess(() -> Component.translatable(ADMIN + "info", profile.getName(), m.rank().label(), m.points(), m.kills(), m.contracts(),
                held), false);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = profile(context);
        Kingdom kingdom = kingdom(context, "kingdom");
        MinecraftServer server = source.getServer();
        Outcome outcome = GuildWork.change(server, "join the guild", guild -> guild.join(profile.getId(), kingdom.id(),
                KingdomWorldApi.gameTime(server), TreasuryActions.day(server), GuildSettings.rules(), KingdomWorldApi.state(server)));
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "joined", profile.getName(), kingdom.name()), true);
        return 1;
    }

    private static int crystal(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile profile = profile(context);
        Rank rank = rankArgument(context);
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(profile.getId());
        if (player == null) {
            source.sendFailure(Component.translatable(ADMIN + "offline", profile.getName()));
            return 0;
        }
        GoodsStacks.give(player, MonsterCrystalItem.stack(rank, count));
        source.sendSuccess(() -> Component.translatable(ADMIN + "crystal.given", profile.getName(), GuildTexts.crystals(count), rank.label()),
                true);
        return count;
    }

    // ---------------------------------------------------------------- contracts

    private static int contracts(CommandSourceStack source, Kingdom kingdom) {
        MinecraftServer server = source.getServer();
        GuildState guild = GuildWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        List<Contract> shown = new ArrayList<>();
        for (Contract contract : guild.contracts()) {
            if (kingdom == null || contract.branch().equals(kingdom.id())) {
                shown.add(contract);
            }
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "contract.header", shown.size()), false);
        for (Contract contract : shown) {
            Component taker = contract.player() != null
                    ? Component.literal(io.github.sekerator.kingdomworld.common.kingdom.PlayerNames.name(server, contract.player()))
                    : contract.squad() != 0 ? Component.translatable(ADMIN + "contract.squad", contract.squad()) : Component.literal("-");
            source.sendSuccess(() -> Component.translatable(ADMIN + "contract.entry", contract.id(), contract.kind().id(),
                    kingdomName(server, contract.branch()), contract.target().isEmpty() ? contract.targetRank().id() : contract.target(),
                    contract.progress(), contract.count(), contract.reward(), contract.status().id(), taker, contract.deadline() - now), false);
        }
        return shown.size();
    }

    private static int post(CommandContext<CommandSourceStack> context, int days) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context, "kingdom");
        Optional<Contract.Kind> kind = Contract.Kind.byId(StringArgumentType.getString(context, "kind").toLowerCase(Locale.ROOT))
                .filter(Contract.Kind::postable);
        String target = StringArgumentType.getString(context, "target");
        int count = IntegerArgumentType.getInteger(context, "count");
        long reward = LongArgumentType.getLong(context, "reward");
        if (kind.isEmpty()) {
            return refused(source, Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE));
        }
        Rank targetRank;
        Optional<Rank> byRank = Rank.byId(target);
        if (kind.get() == Contract.Kind.GATHER) {
            if (GoodsStacks.item(target) == null) {
                return refused(source, Outcome.fail(KingdomError.BAD_ITEM));
            }
            targetRank = Rank.F;
        } else if (kind.get() == Contract.Kind.HUNT && byRank.isPresent()) {
            targetRank = byRank.get();
            target = "";
        } else {
            EntityType<?> type = GuildTexts.monsterType(target);
            if (type == null) {
                return refused(source, Outcome.fail(KingdomError.UNKNOWN_MONSTER));
            }
            targetRank = MonsterRanks.of(type, server.overworld()).rank();
        }
        GuildState.Posting posting = new GuildState.Posting(kingdom.id(), Contract.Poster.KINGDOM, kingdom.id(), kind.get(), target, targetRank,
                count, reward, targetRank, days, true);
        GuildState.Made[] made = new GuildState.Made[1];
        Outcome outcome = GuildWork.change(server, "post a contract", guild -> {
            made[0] = guild.post(null, posting, KingdomWorldApi.gameTime(server), TreasuryActions.day(server), GuildSettings.rules(),
                    KingdomWorldApi.state(server));
            return made[0].outcome();
        });
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        Contract contract = made[0].contract().orElseThrow();
        source.sendSuccess(() -> Component.translatable(ADMIN + "contract.posted", contract.id(), kingdom.name(), GuildTexts.task(contract),
                contract.reward()), true);
        return (int) contract.id();
    }

    private static int take(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        GameProfile profile = profile(context);
        long id = LongArgumentType.getLong(context, "id");
        Outcome outcome = GuildWork.change(server, "take a contract", guild -> guild.take(profile.getId(), id, KingdomWorldApi.gameTime(server),
                GuildSettings.rules()));
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "contract.taken", profile.getName(), id), true);
        return 1;
    }

    private static int kill(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        GameProfile profile = profile(context);
        String monster = ResourceLocationArgument.getId(context, "monster").toString();
        EntityType<?> type = GuildTexts.monsterType(monster);
        if (type == null) {
            return refused(source, Outcome.fail(KingdomError.UNKNOWN_MONSTER));
        }
        if (!writable(source)) {
            return 0;
        }
        MonsterRanking.Ranked ranked = MonsterRanks.of(type, server.overworld());
        int moved = 0;
        int finished = 0;
        GuildState guild = GuildWork.state(server);
        for (int i = 0; i < count; i++) {
            for (GuildState.Progress progress : guild.recordKill(profile.getId(), monster, ranked.rank(), ranked.boss(),
                    KingdomWorldApi.gameTime(server), TreasuryActions.day(server), KingdomWorldApi.state(server))) {
                moved++;
                if (progress.finished()) {
                    finished++;
                }
            }
        }
        int movedTotal = moved;
        int finishedTotal = finished;
        source.sendSuccess(() -> Component.translatable(ADMIN + "contract.killed", count, monster, profile.getName(), movedTotal, finishedTotal),
                true);
        return finished;
    }

    private static int expire(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long id = LongArgumentType.getLong(context, "id");
        if (!writable(source)) {
            return 0;
        }
        GuildState guild = GuildWork.state(server);
        if (!guild.runOut(id, KingdomWorldApi.gameTime(server))) {
            return refused(source, Outcome.fail(KingdomError.UNKNOWN_CONTRACT));
        }
        GuildWork.tick(server);
        boolean gone = guild.contract(id).isEmpty();
        source.sendSuccess(() -> Component.translatable(gone ? ADMIN + "contract.expired" : ADMIN + "contract.waits", id), true);
        return gone ? 1 : 0;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long id = LongArgumentType.getLong(context, "id");
        Optional<Contract>[] closed = new Optional[]{Optional.empty()};
        Outcome outcome = GuildWork.change(server, "call off a contract", guild -> {
            Outcome check = guild.checkCancel(null, id, KingdomWorldApi.state(server));
            if (check.ok()) {
                closed[0] = guild.cancel(null, id, TreasuryActions.day(server), KingdomWorldApi.state(server));
            }
            return check;
        });
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "contract.cancelled", id), true);
        return 1;
    }

    // ---------------------------------------------------------------- squads and adventurers

    private static int squads(CommandSourceStack source, Kingdom kingdom) {
        MinecraftServer server = source.getServer();
        GuildState guild = GuildWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        List<Squad> shown = new ArrayList<>();
        for (Squad squad : guild.squads()) {
            if (kingdom == null || squad.kingdomId().equals(kingdom.id())) {
                shown.add(squad);
            }
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "squad.header", shown.size()), false);
        for (Squad squad : shown) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "squad.entry", squad.id(), kingdomName(server, squad.kingdomId()),
                    squad.members().size(), guild.squadRank(squad).label(), squad.state().id(), squad.contractId(),
                    squad.away() ? squad.returns() - now : 0), false);
        }
        return shown.size();
    }

    private static int form(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context, "kingdom");
        int size = IntegerArgumentType.getInteger(context, "size");
        GuildState guild = GuildWork.state(server);
        List<UUID> members = new ArrayList<>();
        for (Citizen citizen : KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id())) {
            if (members.size() < size && citizen.professionId().equals(Professions.ADVENTURER.id()) && guild.squadOf(citizen.id()).isEmpty()) {
                members.add(citizen.id());
            }
        }
        GuildState.Formed[] formed = new GuildState.Formed[1];
        Outcome outcome = GuildWork.change(server, "form a squad", state -> {
            formed[0] = state.formSquad(null, kingdom.id(), members, GuildSettings.rules(), KingdomWorldApi.state(server));
            return formed[0].outcome();
        });
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        Squad squad = formed[0].squad().orElseThrow();
        source.sendSuccess(() -> Component.translatable(ADMIN + "squad.formed", squad.id(), kingdom.name(), squad.members().size()), true);
        return (int) squad.id();
    }

    private static int send(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long squad = LongArgumentType.getLong(context, "squad");
        long contract = LongArgumentType.getLong(context, "contract");
        Outcome outcome = GuildWork.send(server, null, squad, contract);
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        long back = GuildWork.state(server).squad(squad).map(Squad::returns).orElse(0L) - KingdomWorldApi.gameTime(server);
        source.sendSuccess(() -> Component.translatable(ADMIN + "squad.sent", squad, contract, back), true);
        return 1;
    }

    private static int bringBack(CommandSourceStack source) {
        if (!writable(source)) {
            return 0;
        }
        int count = GuildWork.bringBackAll(source.getServer());
        source.sendSuccess(() -> Component.translatable(ADMIN + "squad.returned", count), true);
        return count;
    }

    private static int hire(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context, "kingdom");
        int count = IntegerArgumentType.getInteger(context, "count");
        int[] hired = {0};
        Outcome outcome = KingdomWorldApi.change(server, "make adventurers", state -> {
            for (Citizen citizen : List.copyOf(state.citizens().registry().citizensOf(kingdom.id()))) {
                if (hired[0] < count && citizen.professionId().equals(Professions.UNEMPLOYED.id())
                        && state.setProfession(null, citizen.id(), Professions.ADVENTURER.id()).ok()) {
                    hired[0]++;
                }
            }
            return Outcome.DONE;
        });
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "hired", hired[0], kingdom.name()), true);
        return hired[0];
    }

    // ---------------------------------------------------------------- monsters, money, energy, data

    private static int monsters(CommandSourceStack source, String filter) {
        MinecraftServer server = source.getServer();
        String wanted = filter.strip().toLowerCase(Locale.ROOT);
        List<EntityType<?>> types = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            String id = MonsterRanks.id(type);
            boolean monster = type.getCategory() == MobCategory.MONSTER || type.is(MonsterRanks.BOSSES);
            if (monster && (wanted.isEmpty() || id.contains(wanted))) {
                types.add(type);
            }
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "monsters.header", types.size()), false);
        for (EntityType<?> type : types) {
            MonsterRanking.Ranked ranked;
            try {
                ranked = MonsterRanks.of(type, server.overworld());
            } catch (RuntimeException e) {
                source.sendFailure(Component.translatable(ADMIN + "monsters.error", MonsterRanks.id(type), String.valueOf(e)));
                continue;
            }
            MonsterRanking.Ranked shown = ranked;
            source.sendSuccess(() -> Component.translatable(ADMIN + "monsters.entry", MonsterRanks.id(type), shown.rank().label(),
                    Math.round(shown.score()), shown.boss() ? Component.translatable(ADMIN + "monsters.boss") : Component.empty()), false);
        }
        return types.size();
    }

    private static int money(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        GuildState guild = GuildWork.state(server);
        Map<UUID, Long> held = new java.util.LinkedHashMap<>();
        for (Contract contract : guild.contracts()) {
            held.merge(contract.currency(), contract.reward(), Long::sum);
        }
        held.values().removeIf(value -> value == 0);
        if (held.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "money.none"), false);
            return 0;
        }
        KingdomWorldState world = KingdomWorldApi.state(server);
        held.forEach((currency, amount) -> source.sendSuccess(() -> Component.translatable(ADMIN + "money.entry", kingdomName(server, currency),
                amount, world.economy().supply(currency)), false));
        return held.size();
    }

    private static int energy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context, "kingdom");
        long[] crystals = GuildActions.crystals(source.getServer(), kingdom.id());
        long count = 0;
        for (long value : crystals) {
            count += value;
        }
        long total = count;
        long energy = GuildActions.energy(source.getServer(), kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "energy", kingdom.name(), total, energy), false);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int status(CommandSourceStack source) {
        GuildSavedData data = GuildSavedData.get(source.getServer());
        if (data.isReadOnly()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "status.read_only", data.readOnlyReason().orElse("?")), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "status.ok", GuildSavedData.FORMAT_VERSION), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        GuildSavedData data = GuildSavedData.get(source.getServer());
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
