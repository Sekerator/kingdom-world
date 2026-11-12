package io.github.sekerator.kingdomworld.common.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyActions;
import io.github.sekerator.kingdomworld.common.army.ArmyGear;
import io.github.sekerator.kingdomworld.common.army.ArmySavedData;
import io.github.sekerator.kingdomworld.common.army.ArmySettings;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.core.army.ArmySquad;
import io.github.sekerator.kingdomworld.core.army.ArmyState;
import io.github.sekerator.kingdomworld.core.army.MercenaryBand;
import io.github.sekerator.kingdomworld.core.army.Order;
import io.github.sekerator.kingdomworld.core.army.Soldier;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.combat.Side;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Operator commands of the army under {@code /kingdomworld admin army}: recruiting citizens, their gear, squads and their
 * orders, mercenaries on offer, hired and paid, battles far from players for checks, and the state of the army's data.
 * Everything goes through the same calls the screens use.
 */
public final class ArmyCommands {
    private static final String ADMIN = "commands.kingdomworld.admin.army.";
    private static final DynamicCommandExceptionType NO_KINGDOM = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.kingdomworld.economy.no_kingdom", name));
    private static final SuggestionProvider<CommandSourceStack> KINGDOMS = (context, builder) ->
            SharedSuggestionProvider.suggest(KingdomWorldApi.kingdoms(context.getSource().getServer()).stream()
                    .map(Kingdom::name).map(name -> name.contains(" ") ? "\"" + name + "\"" : name), builder);
    private static final SuggestionProvider<CommandSourceStack> ROLES = (context, builder) ->
            SharedSuggestionProvider.suggest(List.of("warrior", "archer"), builder);

    private ArmyCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("army")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("unlock").executes(context -> unlock(context.getSource())))
                .then(Commands.literal("recruit")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> recruit(context, "warrior"))
                                        .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                                .executes(context -> recruit(context, StringArgumentType.getString(context, "role")))))))
                .then(Commands.literal("dismiss")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(ArmyCommands::dismissAll)))
                .then(Commands.literal("garrison")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(ArmyCommands::garrison)))
                .then(Commands.literal("staff")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(ArmyCommands::staff)))
                .then(Commands.literal("branch")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .then(Commands.literal("guard").then(Commands.argument("count", IntegerArgumentType.integer(1, 1_024))
                                        .executes(context -> branch(context, io.github.sekerator.kingdomworld.core.army.Branch.GUARD))))
                                .then(Commands.literal("army").then(Commands.argument("count", IntegerArgumentType.integer(1, 1_024))
                                        .executes(context -> branch(context, io.github.sekerator.kingdomworld.core.army.Branch.ARMY))))))
                .then(Commands.literal("weapons")
                        .then(Commands.argument("namespace", StringArgumentType.word()).executes(ArmyCommands::weapons)))
                .then(Commands.literal("soldiers")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> soldiers(context, false))))
                .then(Commands.literal("gear")
                        .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                .executes(context -> soldiers(context, true))))
                .then(Commands.literal("squad")
                        .then(Commands.literal("list")
                                .executes(context -> squads(context.getSource(), null))
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> squads(context.getSource(), kingdom(context)))))
                        .then(Commands.literal("form")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("size", IntegerArgumentType.integer(1, 32)).executes(ArmyCommands::form))))
                        .then(Commands.literal("disband")
                                .then(Commands.argument("squad", LongArgumentType.longArg(1)).executes(ArmyCommands::disband))))
                .then(Commands.literal("order")
                        .then(Commands.argument("squad", LongArgumentType.longArg(1))
                                .then(Commands.literal("none").executes(context -> order(context, Order.NONE)))
                                .then(Commands.literal("hold")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> order(context, Order.hold(pos(context))))))
                                .then(Commands.literal("patrol")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ArmyCommands::patrol)))
                                .then(Commands.literal("guard")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(0, Order.MAX_RADIUS))
                                                        .executes(context -> order(context, Order.guard(pos(context),
                                                                IntegerArgumentType.getInteger(context, "radius")))))))
                                .then(Commands.literal("follow")
                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                .executes(context -> order(context, Order.follow(profile(context).getId())))))
                                .then(Commands.literal("escort")
                                        .then(Commands.argument("deal", LongArgumentType.longArg(1))
                                                .executes(context -> order(context, Order.escort(LongArgumentType.getLong(context, "deal"))))))))
                .then(Commands.literal("mercenary")
                        .then(Commands.literal("offers")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> offers(context, false))
                                        .then(Commands.literal("new").executes(context -> offers(context, true)))))
                        .then(Commands.literal("hire")
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .then(Commands.argument("band", LongArgumentType.longArg(1)).executes(ArmyCommands::hire))))
                        .then(Commands.literal("release")
                                .then(Commands.argument("band", LongArgumentType.longArg(1)).executes(ArmyCommands::release)))
                        .then(Commands.literal("list")
                                .executes(context -> hired(context.getSource(), null))
                                .then(Commands.argument("kingdom", StringArgumentType.string()).suggests(KINGDOMS)
                                        .executes(context -> hired(context.getSource(), kingdom(context))))))
                .then(Commands.literal("battle")
                        .then(Commands.argument("squad", LongArgumentType.longArg(1))
                                .then(Commands.argument("monsters", IntegerArgumentType.integer(1, 1_000))
                                        .executes(context -> battle(context, ArmySettings.rules().monsterStrength()))
                                        .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0.1, 1_000_000))
                                                .executes(context -> battle(context, DoubleArgumentType.getDouble(context, "strength")))))));
    }

    // ---------------------------------------------------------------- helpers

    private static Kingdom kingdom(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = KingdomNames.normalize(StringArgumentType.getString(context, "kingdom"));
        return KingdomWorldApi.findKingdom(context.getSource().getServer(), name).orElseThrow(() -> NO_KINGDOM.create(name));
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(context, "player").iterator().next();
    }

    private static WorldPos pos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        return new WorldPos(Dimensions.id(context.getSource().getLevel()), pos.getX(), pos.getY(), pos.getZ());
    }

    private static int refused(CommandSourceStack source, Outcome outcome) {
        source.sendFailure(KingdomActions.errorMessage(outcome.error()));
        return 0;
    }

    private static String weaponOf(MinecraftServer server, UUID citizen) {
        var stack = ArmyWork.hands(server, citizen).get(EquipmentSlot.MAINHAND);
        return stack.isEmpty() ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ---------------------------------------------------------------- soldiers

    private static int recruit(CommandContext<CommandSourceStack> context, String role) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        int count = IntegerArgumentType.getInteger(context, "count");
        String profession = "archer".equals(role) ? Professions.ARCHER.id() : Professions.WARRIOR.id();
        int recruited = 0;
        Outcome last = Outcome.DONE;
        for (Citizen citizen : List.copyOf(KingdomWorldApi.state(server).citizens().registry().citizensOf(kingdom.id()))) {
            if (recruited >= count) {
                break;
            }
            if (!citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                continue;
            }
            last = ArmyWork.recruit(server, null, kingdom.id(), citizen.id(), profession);
            if (last.ok()) {
                recruited++;
            }
        }
        if (recruited == 0 && !last.ok()) {
            return refused(source, last);
        }
        int done = recruited;
        source.sendSuccess(() -> Component.translatable(ADMIN + "recruited", done, profession, kingdom.name()), true);
        return recruited;
    }

    /**
     * The garrison of a kingdom in one line: the guard and the army, their officers, how many soldiers stand at every post,
     * how far the answer to every alarm has gone, and where the throne stands.
     */
    private static int garrison(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        io.github.sekerator.kingdomworld.core.army.ArmyState army = ArmyWork.state(server);
        int guards = army.branchOf(kingdom.id(), io.github.sekerator.kingdomworld.core.army.Branch.GUARD).size();
        int ofArmy = army.branchOf(kingdom.id(), io.github.sekerator.kingdomworld.core.army.Branch.ARMY).size();
        int officers = 0;
        for (io.github.sekerator.kingdomworld.core.army.Soldier soldier : army.soldiersOf(kingdom.id())) {
            officers += soldier.post().officer() ? 1 : 0;
        }
        java.util.Map<String, Integer> posts = new java.util.TreeMap<>();
        for (io.github.sekerator.kingdomworld.core.army.ArmySquad squad : army.squadsOf(kingdom.id())) {
            army.duty(squad.id()).ifPresent(duty -> posts.put(duty, squad.members().size()));
        }
        StringBuilder standing = new StringBuilder();
        posts.forEach((duty, count) -> standing.append(standing.length() > 0 ? " " : "").append(duty).append('=').append(count));
        StringBuilder alarms = new StringBuilder();
        for (io.github.sekerator.kingdomworld.core.law.Alarm alarm : io.github.sekerator.kingdomworld.common.law.LawAlarms.active(server,
                kingdom.id())) {
            alarms.append(alarms.length() > 0 ? " " : "").append(alarm.name()).append('=')
                    .append(io.github.sekerator.kingdomworld.common.army.ArmyRelief.step(kingdom.id(), alarm.offender()));
        }
        io.github.sekerator.kingdomworld.core.citizen.WorldPos throne = ArmyWork.capitalOf(server, kingdom.id());
        int count = posts.size();
        source.sendSuccess(() -> Component.translatable(ADMIN + "garrison", kingdom.name(), guards, ofArmy, officers, count,
                standing.length() == 0 ? "-" : standing.toString(), alarms.length() == 0 ? "-" : alarms.toString(),
                throne == null ? 0 : throne.x(), throne == null ? 0 : throne.y(), throne == null ? 0 : throne.z()), false);
        return guards + ofArmy;
    }

    /**
     * Sees to the staff of a kingdom now, as a kingdom of the world does once a day: replaces fallen soldiers, fills the
     * posts of officers and issues the kits, paid from its treasury.
     */
    private static int staff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Kingdom kingdom = kingdom(context);
        io.github.sekerator.kingdomworld.common.army.ArmyStaff.Report report =
                io.github.sekerator.kingdomworld.common.army.ArmyStaff.muster(source.getServer(), kingdom.id(), false);
        source.sendSuccess(() -> Component.translatable(ADMIN + "staff", kingdom.name(), report.recruited(), report.promoted(), report.issued(),
                report.paid()), true);
        return report.recruited() + report.promoted() + report.issued();
    }

    /**
     * Moves fighters of a kingdom to the guard or to the army.
     */
    private static int branch(CommandContext<CommandSourceStack> context, io.github.sekerator.kingdomworld.core.army.Branch branch)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        int count = IntegerArgumentType.getInteger(context, "count");
        int moved = 0;
        for (io.github.sekerator.kingdomworld.core.army.Soldier soldier : ArmyWork.state(server).branchOf(kingdom.id(), branch.other())) {
            if (moved >= count) {
                break;
            }
            if (soldier.post() == io.github.sekerator.kingdomworld.core.army.Post.FIGHTER
                    && io.github.sekerator.kingdomworld.common.army.ArmyStaff.setBranch(server, null, soldier.citizenId(), branch).ok()) {
                moved++;
            }
        }
        int done = moved;
        source.sendSuccess(() -> Component.translatable(ADMIN + "branch", done, kingdom.name(), branch.id()), true);
        return moved;
    }

    private static int dismissAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        int dismissed = 0;
        for (Soldier soldier : List.copyOf(ArmyWork.state(server).soldiersOf(kingdom.id()))) {
            if (ArmyWork.dismiss(server, null, soldier.citizenId()).ok()) {
                dismissed++;
            }
        }
        int done = dismissed;
        source.sendSuccess(() -> Component.translatable(ADMIN + "dismissed", done, kingdom.name()), true);
        return dismissed;
    }

    private static int soldiers(CommandContext<CommandSourceStack> context, boolean equip) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        List<Soldier> soldiers = ArmyWork.state(server).soldiersOf(kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "soldiers.header", kingdom.name(), soldiers.size()), false);
        for (Soldier soldier : soldiers) {
            Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(soldier.citizenId());
            if (citizen.isEmpty()) {
                continue;
            }
            List<ArmyGear.Choice> taken = equip ? ArmyWork.equip(server, citizen.get()) : List.of();
            String weapon = weaponOf(server, soldier.citizenId());
            int armor = 0;
            for (EquipmentSlot slot : io.github.sekerator.kingdomworld.common.citizen.work.Equipment.ARMOR) {
                armor += (int) Math.round(ArmyGear.armorDefense(ArmyWork.hands(server, soldier.citizenId()).get(slot), slot));
            }
            int armorPoints = armor;
            source.sendSuccess(() -> Component.translatable(ADMIN + "soldiers.line", citizen.get().shortId(), citizen.get().professionId(),
                    soldier.rank().id(), soldier.experience(), weapon, armorPoints, taken.size(),
                    citizen.get().state() == CitizenState.WOUNDED ? "wounded" : "healthy"), false);
        }
        return soldiers.size();
    }

    /**
     * The weapons of a mod as soldiers judge them, best first.
     */
    private static int weapons(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String namespace = StringArgumentType.getString(context, "namespace");
        record Judged(String id, String kind, double score) {
        }
        List<Judged> judged = new ArrayList<>();
        for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues()) {
            net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
            if (key == null || !key.getNamespace().equals(namespace)) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
            double melee = ArmyGear.score(stack, io.github.sekerator.kingdomworld.core.army.GearChoice.Slot.MELEE);
            double ranged = ArmyGear.score(stack, io.github.sekerator.kingdomworld.core.army.GearChoice.Slot.RANGED);
            if (melee > 0 || ranged > 0) {
                judged.add(new Judged(key.toString(), ranged > melee ? "ranged" : "melee", Math.max(melee, ranged)));
            }
        }
        judged.sort(java.util.Comparator.comparingDouble(Judged::score).reversed());
        source.sendSuccess(() -> Component.translatable(ADMIN + "weapons.header", namespace, judged.size()), false);
        for (Judged one : judged.subList(0, Math.min(20, judged.size()))) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "weapons.line", one.id(), one.kind(),
                    String.format(java.util.Locale.ROOT, "%.1f", one.score())), false);
        }
        return judged.size();
    }

    // ---------------------------------------------------------------- squads

    private static int squads(CommandSourceStack source, Kingdom only) {
        MinecraftServer server = source.getServer();
        List<ArmySquad> squads = new ArrayList<>(only == null ? ArmyWork.state(server).squads() : ArmyWork.state(server).squadsOf(only.id()));
        source.sendSuccess(() -> Component.translatable(ADMIN + "squads.header", squads.size()), false);
        for (ArmySquad squad : squads) {
            String kingdom = KingdomWorldApi.kingdom(server, squad.kingdomId()).map(Kingdom::name).orElse("?");
            int members = squad.mercenaries() ? ArmyWork.state(server).band(squad.band()).map(b -> b.fighters().size()).orElse(0)
                    : squad.members().size();
            String place = squad.order().anchor().map(WorldPos::coordinates).orElse("-");
            long power = Math.round(ArmyWork.power(server, squad));
            source.sendSuccess(() -> Component.translatable(ADMIN + "squads.line", squad.id(), kingdom, squad.mercenaries() ? "mercenaries" : "soldiers",
                    members, squad.order().kind().id(), place, power, ArmyWork.onTheRoad(server, squad) ? "on the road" : "at home"), false);
        }
        return squads.size();
    }

    private static int form(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        int size = IntegerArgumentType.getInteger(context, "size");
        ArmyState army = ArmyWork.state(server);
        List<UUID> free = new ArrayList<>();
        for (Soldier soldier : army.soldiersOf(kingdom.id())) {
            if (free.size() < size && army.squadOf(soldier.citizenId()).isEmpty()) {
                free.add(soldier.citizenId());
            }
        }
        if (ArmyWork.isReadOnly(server)) {
            return refused(source, Outcome.fail(KingdomError.READ_ONLY));
        }
        ArmyState.Formed formed = army.formSquad(null, kingdom.id(), free, ArmySettings.rules(), KingdomWorldApi.state(server));
        if (!formed.outcome().ok()) {
            return refused(source, formed.outcome());
        }
        ArmySquad squad = formed.squad().orElseThrow();
        source.sendSuccess(() -> Component.translatable(ADMIN + "formed", squad.id(), kingdom.name(), squad.members().size()), true);
        return (int) squad.id();
    }

    private static int disband(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long id = LongArgumentType.getLong(context, "squad");
        Outcome outcome = ArmyWork.change(source.getServer(), "disband a squad",
                army -> army.disband(null, id, KingdomWorldApi.state(source.getServer())));
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "disbanded", id), true);
        return 1;
    }

    private static int order(CommandContext<CommandSourceStack> context, Order order) {
        CommandSourceStack source = context.getSource();
        long id = LongArgumentType.getLong(context, "squad");
        Outcome outcome = ArmyWork.order(source.getServer(), null, id, order);
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "ordered", id, order.kind().id(),
                order.anchor().map(WorldPos::coordinates).orElse("-")), true);
        return 1;
    }

    private static int patrol(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long id = LongArgumentType.getLong(context, "squad");
        Order current = ArmyWork.state(context.getSource().getServer()).squad(id).map(ArmySquad::order).orElse(Order.NONE);
        return order(context, current.withPatrolPoint(pos(context)));
    }

    // ---------------------------------------------------------------- mercenaries

    private static int offers(CommandContext<CommandSourceStack> context, boolean fresh) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        ArmyState army = ArmyWork.state(server);
        if (fresh) {
            if (ArmyWork.isReadOnly(server)) {
                return refused(source, Outcome.fail(KingdomError.READ_ONLY));
            }
            army.refreshOffers(kingdom.id(), ArmyWork.day(server), ArmySettings.rules(), java.util.concurrent.ThreadLocalRandom.current()::nextDouble);
        }
        List<MercenaryBand> offers = army.offersOf(kingdom.id());
        source.sendSuccess(() -> Component.translatable(ADMIN + "offers.header", kingdom.name(), offers.size()), false);
        for (MercenaryBand band : offers) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "offers.line", band.id(), band.fighters().size(), band.captain().rank().id(),
                    Math.round(band.power()), band.dailyPrice(), ArmyState.advance(band, ArmySettings.rules())), false);
        }
        return offers.size();
    }

    private static int hire(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Kingdom kingdom = kingdom(context);
        long id = LongArgumentType.getLong(context, "band");
        if (ArmyWork.isReadOnly(server)) {
            return refused(source, Outcome.fail(KingdomError.READ_ONLY));
        }
        ArmyState.Formed formed = ArmyWork.state(server).hire(null, kingdom.id(), id, ArmyWork.day(server), ArmySettings.rules(),
                KingdomWorldApi.state(server));
        if (!formed.outcome().ok()) {
            return refused(source, formed.outcome());
        }
        long squad = formed.squad().orElseThrow().id();
        long paidUntil = ArmyWork.state(server).band(id).map(MercenaryBand::paidUntil).orElse(0L);
        source.sendSuccess(() -> Component.translatable(ADMIN + "hired", id, kingdom.name(), squad, paidUntil), true);
        return (int) squad;
    }

    private static int release(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long id = LongArgumentType.getLong(context, "band");
        Outcome outcome = ArmyWork.change(source.getServer(), "release mercenaries",
                army -> army.release(null, id, KingdomWorldApi.state(source.getServer())));
        if (!outcome.ok()) {
            return refused(source, outcome);
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "released", id), true);
        return 1;
    }

    private static int hired(CommandSourceStack source, Kingdom only) {
        MinecraftServer server = source.getServer();
        long today = ArmyWork.day(server);
        List<MercenaryBand> bands = new ArrayList<>();
        for (MercenaryBand band : ArmyWork.state(server).bands()) {
            if (band.hired() && (only == null || band.kingdomId().equals(only.id()))) {
                bands.add(band);
            }
        }
        source.sendSuccess(() -> Component.translatable(ADMIN + "bands.header", bands.size()), false);
        for (MercenaryBand band : bands) {
            String kingdom = KingdomWorldApi.kingdom(server, band.kingdomId()).map(Kingdom::name).orElse("?");
            long squad = ArmyWork.state(server).squadOfBand(band.id()).map(ArmySquad::id).orElse(0L);
            source.sendSuccess(() -> Component.translatable(ADMIN + "bands.line", band.id(), kingdom, band.fighters().size(), squad,
                    band.paidUntil(), band.daysLeft(today), band.dailyPrice()), false);
        }
        return bands.size();
    }

    // ---------------------------------------------------------------- battles, data

    private static int battle(CommandContext<CommandSourceStack> context, double strength) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        long id = LongArgumentType.getLong(context, "squad");
        int monsters = IntegerArgumentType.getInteger(context, "monsters");
        Optional<ArmySquad> squad = ArmyWork.state(server).squad(id);
        if (squad.isEmpty()) {
            return refused(source, Outcome.fail(KingdomError.UNKNOWN_SQUAD));
        }
        if (ArmyWork.isReadOnly(server)) {
            return refused(source, Outcome.fail(KingdomError.READ_ONLY));
        }
        ArmyWork.Fought fought = ArmyWork.fight(server, squad.get(), Side.crowd(Side.Kind.MONSTERS, monsters * strength, monsters), null);
        ArmyWork.announce(server, squad.get(), fought, Component.translatable(ADMIN + "battle.what", monsters));
        source.sendSuccess(() -> Component.translatable(ADMIN + "battle", id, fought.won() ? "won" : "lost",
                String.format(java.util.Locale.ROOT, "%.2f", fought.result().chance()), fought.kills(), fought.wounded(), fought.fallen().size()),
                true);
        return fought.won() ? 1 : 0;
    }

    private static int status(CommandSourceStack source) {
        ArmySavedData data = ArmySavedData.get(source.getServer());
        if (data.isReadOnly()) {
            source.sendSuccess(() -> Component.translatable(ADMIN + "status.read_only", data.readOnlyReason().orElse("?")), false);
            return 0;
        }
        ArmyState army = data.state();
        source.sendSuccess(() -> Component.translatable(ADMIN + "status.ok", ArmySavedData.FORMAT_VERSION, army.soldiers().size(),
                army.squads().size(), army.bands().size()), false);
        return 1;
    }

    private static int unlock(CommandSourceStack source) {
        ArmySavedData data = ArmySavedData.get(source.getServer());
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
