package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenActions;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.WorkPostView;
import io.github.sekerator.kingdomworld.common.network.packet.WorkPostActionPacket;
import io.github.sekerator.kingdomworld.common.network.packet.WorkPostViewPacket;
import io.github.sekerator.kingdomworld.common.protection.ProtectionService;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.MineGoal;
import io.github.sekerator.kingdomworld.core.citizen.MinePlan;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with work posts: open the screen, change the settings, hire and dismiss workers. Members of the
 * post's kingdom see the screen; changing anything needs the right to manage citizens. The server checks every
 * request (rights, distance to the post, ranges of the values) and answers with the result and a fresh screen.
 */
public final class WorkPostActions {
    /** How far from a post a player may change it, squared blocks. */
    private static final double MAX_DISTANCE_SQR = 10.0 * 10.0;
    private static final String KEY = "kingdomworld.post.";

    private WorkPostActions() {
    }

    // ---------------------------------------------------------------- a post appears

    /**
     * A new post was found on kingdom land: its zone gets the default radius of its profession, or less where the land
     * is smaller and the profession needs its zone on the land, and a survey estimates what it yields.
     */
    public static void onPostFound(ServerLevel level, WorldPos pos) {
        MinecraftServer server = level.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<WorkPost> post = state.citizens().posts().get(pos);
        if (post.isEmpty()) {
            return;
        }
        GatheringRules rules = GatheringSettings.rules();
        String profession = post.get().professionId();
        int inside = WorkZone.largestRadiusWithin(pos, rules.defaultRadius(profession),
                chunk -> post.get().kingdomId().equals(state.territory().ownerOf(pos.dimension(), chunk)));
        state.citizens().setPostRadius(post.get(), rules.radiusForNewPost(profession, inside));
        PostContainers.Container container = PostContainers.find(level, pos);
        post.get().setHasContainer(container != null);
        PostWork.refreshStatus(server, post.get());
        ZoneSurveys.request(pos);
    }

    // ---------------------------------------------------------------- the screen

    /**
     * A player used a post block: members of its kingdom see its screen.
     */
    public static void open(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.server;
        WorldPos at = new WorldPos(Dimensions.id(player.level()), pos.getX(), pos.getY(), pos.getZ());
        Optional<WorkPost> post = PostWork.post(server, at);
        if (post.isEmpty()) {
            player.displayClientMessage(Component.translatable(KEY + (KingdomWorldApi.isReadOnly(server) ? "read_only" : "outside")), true);
            return;
        }
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), post.get().kingdomId())) {
            KingdomWorldApi.kingdom(server, post.get().kingdomId()).ifPresent(kingdom -> player.displayClientMessage(
                    Component.translatable(KEY + "foreign", Component.literal(ProtectionService.shortName(kingdom.name()))
                            .withStyle(style -> style.withColor(TextColor.fromRgb(kingdom.color())))), true));
            return;
        }
        send(player, post.get(), true);
    }

    public static void send(ServerPlayer player, WorkPost post, boolean open) {
        KingdomWorldNetwork.sendToPlayer(player, new WorkPostViewPacket(view(player, post), open));
    }

    /**
     * The screen of a post for a member of its kingdom.
     */
    public static WorkPostView view(ServerPlayer player, WorkPost post) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        PostWork.refreshStatus(server, post);
        ServerLevel level = PostWork.level(server, post.pos());
        List<String> animals = new ArrayList<>();
        boolean container = post.hasContainer();
        if (level != null && level.isLoaded(new BlockPos(post.pos().x(), post.pos().y(), post.pos().z()))) {
            container = PostContainers.find(level, post.pos()) != null;
            post.setHasContainer(container);
            if (post.professionId().equals(Professions.HERDER.id())) {
                animals.addAll(HerderBehavior.kindsIn(level, post));
            }
        }
        if (!post.animal().isEmpty() && !animals.contains(post.animal()) && animals.size() < WorkPostView.MAX_ANIMALS) {
            animals.add(0, post.animal());
        }
        List<WorkPostView.Worker> workers = new ArrayList<>();
        for (UUID id : post.workers()) {
            state.citizens().get(id).ifPresent(citizen -> {
                if (workers.size() < WorkPostView.MAX_WORKERS) {
                    workers.add(new WorkPostView.Worker(citizen.id(), citizen.nameSeed(), citizen.gender() == Gender.FEMALE,
                            citizen.workStatus()));
                }
            });
        }
        int unemployed = 0;
        for (Citizen citizen : state.citizens().registry().citizensOf(post.kingdomId())) {
            if (citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                unemployed++;
            }
        }
        GatheringRules rules = GatheringSettings.rules();
        List<WorkPostView.Station> stations = new ArrayList<>();
        for (String kind : io.github.sekerator.kingdomworld.core.production.Stations.ALL) {
            int count = post.stations().getOrDefault(kind, 0);
            if (count > 0) {
                stations.add(new WorkPostView.Station(kind, count));
            }
        }
        net.minecraft.world.item.Item current = PostWork.item(post.current());
        net.minecraft.network.chat.Component site = net.minecraft.network.chat.Component.empty();
        int progress = (int) Math.round(post.currentProgress() * 100);
        if (post.professionId().equals(io.github.sekerator.kingdomworld.core.citizen.Professions.BUILDER.id())) {
            // A builder's post names the construction it works on now and how far that is.
            var candidates = io.github.sekerator.kingdomworld.common.construction.ConstructionWork.candidates(server, post);
            if (!candidates.isEmpty()) {
                var construction = candidates.get(0);
                site = io.github.sekerator.kingdomworld.common.construction.ConstructionWork.template(construction)
                        .map(io.github.sekerator.kingdomworld.common.construction.BuildingTemplate::name)
                        .orElse(net.minecraft.network.chat.Component.literal(construction.templateId()));
                progress = io.github.sekerator.kingdomworld.common.construction.ConstructionWork.plan(construction)
                        .map(plan -> plan.percent(construction)).orElse(0);
                site = net.minecraft.network.chat.Component.translatable("kingdomworld.screen.post.site", site,
                        construction.anchor().coordinates(), progress);
            }
        }
        WorkPostView.Goal goal = WorkPostView.Goal.NONE;
        if (level != null && post.professionId().equals(Professions.MINER.id())) {
            MineGoal asked = post.mineGoal();
            List<String> ores = new ArrayList<>(MineOres.kinds());
            if (!asked.anything() && !ores.contains(asked.ore()) && ores.size() < WorkPostView.MAX_ORES) {
                ores.add(0, asked.ore());
            }
            int lowest = Math.min(post.pos().y(), level.getMinBuildHeight() + MinePlan.BOTTOM_MARGIN);
            goal = new WorkPostView.Goal(ores, asked.ore(), asked.amount(), MinerBehavior.stock(level, post), asked.autoDepth(),
                    MinerBehavior.depth(level, post), lowest, post.pos().y());
        }
        return new WorkPostView(new BlockPos(post.pos().x(), post.pos().y(), post.pos().z()), post.kingdomId(),
                state.membership().hasPermission(player.getUUID(), post.kingdomId(), Permission.MANAGE_CITIZENS),
                post.professionId(), post.radius(), Math.max(post.radius(), rules.maxRadius(post.professionId())), post.seats(),
                Math.max(post.seats(), rules.maxSeats() + io.github.sekerator.kingdomworld.common.construction.BuildingEffects.extraSeats(server,
                        post.pos())), post.animal(), animals, post.herdSize(), Math.max(post.herdSize(), rules.maxHerd()),
                post.status(), container, post.yield().measured(), (int) Math.min(1_000_000, Math.round(post.yield().total())),
                post.producedOn(PostWork.gameDay(server)), workers, unemployed, post.auto(), stations,
                current == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(current),
                progress, site, goal);
    }

    // ---------------------------------------------------------------- changes

    /**
     * Handles a change from the screen of a post and refreshes the screen.
     */
    public static void handle(ServerPlayer player, WorkPostActionPacket packet) {
        MinecraftServer server = player.server;
        WorldPos at = new WorldPos(Dimensions.id(player.level()), packet.pos().getX(), packet.pos().getY(), packet.pos().getZ());
        Optional<WorkPost> post = PostWork.post(server, at);
        if (post.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST));
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(packet.pos())) > MAX_DISTANCE_SQR) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "too_far"));
            return;
        }
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), post.get().kingdomId())) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NO_PERMISSION));
            return;
        }
        KingdomActions.Feedback feedback = switch (packet.action()) {
            case REFRESH -> null;
            case PROFESSION -> setProfession(player, at, packet.text());
            case RADIUS -> setRadius(player, at, packet.value());
            case SEATS -> setSeats(player, at, packet.value());
            case ANIMAL -> setAnimal(player, at, packet.text());
            case HERD -> setHerdSize(player, at, packet.value());
            case HIRE -> hire(player, at);
            case DISMISS -> packet.target() == null ? KingdomActions.Feedback.error(KingdomError.NOT_A_WORKER)
                    : dismiss(player, at, packet.target());
            case AUTO -> setAuto(player, at, packet.value() != 0);
            case GOAL_ORE -> setGoalOre(player, at, packet.text());
            case GOAL_AMOUNT -> setGoalAmount(player, at, packet.value());
            case GOAL_DEPTH -> setGoalDepth(player, at, packet.value() == 0, packet.value() - WorkPostActionPacket.DEPTH_OFFSET);
        };
        if (feedback != null) {
            KingdomActions.reply(player, feedback);
        }
        PostWork.post(server, at).ifPresent(current -> send(player, current, false));
    }

    private static KingdomActions.Feedback result(Outcome outcome, String key, Object... args) {
        return outcome.ok() ? KingdomActions.Feedback.ok(key, args) : KingdomActions.Feedback.error(outcome.error());
    }

    public static KingdomActions.Feedback setProfession(ServerPlayer player, WorldPos pos, String professionId) {
        Outcome outcome = KingdomWorldApi.change(player.server, "set the profession of a work post",
                state -> state.setPostProfession(player.getUUID(), pos, professionId));
        if (outcome.ok()) {
            ZoneSurveys.forget(pos);
            MinerBehavior.forget(pos);
            ZoneSurveys.request(pos);
            PostWork.post(player.server, pos).ifPresent(post -> PostWork.refreshStatus(player.server, post));
        }
        return result(outcome, KEY + "profession_set", CitizenTexts.profession(professionId));
    }

    public static KingdomActions.Feedback setRadius(ServerPlayer player, WorldPos pos, int radius) {
        Outcome outcome = KingdomWorldApi.change(player.server, "set the radius of a work post",
                state -> state.setPostRadius(player.getUUID(), pos, radius, GatheringSettings.rules().maxRadius(
                        state.citizens().posts().get(pos).map(WorkPost::professionId).orElse(""))));
        if (outcome.ok()) {
            ZoneSurveys.request(pos);
        }
        return result(outcome, KEY + "radius_set", radius);
    }

    public static KingdomActions.Feedback setSeats(ServerPlayer player, WorldPos pos, int seats) {
        Outcome outcome = KingdomWorldApi.change(player.server, "set the seats of a work post",
                state -> state.setPostSeats(player.getUUID(), pos, seats, GatheringSettings.rules().maxSeats()
                        + io.github.sekerator.kingdomworld.common.construction.BuildingEffects.extraSeats(player.server, pos)));
        return result(outcome, KEY + "seats_set", seats);
    }

    public static KingdomActions.Feedback setAnimal(ServerPlayer player, WorldPos pos, String animal) {
        Optional<WorkPost> post = PostWork.post(player.server, pos);
        ServerLevel level = PostWork.level(player.server, pos);
        EntityType<?> type = HerderBehavior.type(animal);
        if (post.isEmpty() || level == null || type == null || !HerderBehavior.kindsIn(level, post.get()).contains(animal)) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_ANIMAL);
        }
        Outcome outcome = KingdomWorldApi.change(player.server, "choose the animals of a work post",
                state -> state.setPostAnimal(player.getUUID(), pos, animal));
        if (outcome.ok()) {
            ZoneSurveys.request(pos);
        }
        return result(outcome, KEY + "animal_set", type.getDescription());
    }

    public static KingdomActions.Feedback setHerdSize(ServerPlayer player, WorldPos pos, int size) {
        Outcome outcome = KingdomWorldApi.change(player.server, "set the herd of a work post",
                state -> state.setPostHerdSize(player.getUUID(), pos, size, GatheringSettings.rules().maxHerd()));
        return result(outcome, KEY + "herd_set", size);
    }

    public static KingdomActions.Feedback setAuto(ServerPlayer player, WorldPos pos, boolean auto) {
        Outcome outcome = KingdomWorldApi.change(player.server, "switch the auto mode of a workshop",
                state -> state.setPostAuto(player.getUUID(), pos, auto));
        return result(outcome, auto ? KEY + "auto_on" : KEY + "auto_off");
    }

    /**
     * Asks a miner's post for a kind of ore, or for anything with an empty id. Another kind is another mine.
     */
    public static KingdomActions.Feedback setGoalOre(ServerPlayer player, WorldPos pos, String ore) {
        if (ore == null || !ore.isEmpty() && !MineOres.exists(ore)) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Outcome outcome = setGoal(player, pos, goal -> goal.withOre(ore));
        return result(outcome, ore.isEmpty() ? KEY + "goal_anything" : KEY + "goal_ore_set", MineOres.name(ore));
    }

    public static KingdomActions.Feedback setGoalAmount(ServerPlayer player, WorldPos pos, int amount) {
        if (amount < 0 || amount > MineGoal.MAX_AMOUNT) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Outcome outcome = setGoal(player, pos, goal -> goal.withAmount(amount));
        return result(outcome, amount == 0 ? KEY + "goal_no_limit" : KEY + "goal_amount_set", amount);
    }

    /**
     * Sets the depth of a mine: the one of its kind of ore, or a height, which the world and the level of the post
     * limit. Another depth is another mine.
     */
    public static KingdomActions.Feedback setGoalDepth(ServerPlayer player, WorldPos pos, boolean auto, int depth) {
        Optional<WorkPost> post = PostWork.post(player.server, pos);
        ServerLevel level = PostWork.level(player.server, pos);
        if (post.isEmpty() || level == null) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST);
        }
        if (!auto && (depth < MineGoal.MIN_DEPTH || depth > MineGoal.MAX_DEPTH)) {
            return KingdomActions.Feedback.error(KingdomError.VALUE_OUT_OF_RANGE);
        }
        int height = auto ? 0 : MinePlan.clampDepth(depth, pos.y(), level.getMinBuildHeight());
        Outcome outcome = setGoal(player, pos, goal -> auto ? goal.withAutoDepth() : goal.withDepth(height));
        return result(outcome, KEY + "goal_depth_set", MinerBehavior.depth(level, post.get()));
    }

    private static Outcome setGoal(ServerPlayer player, WorldPos pos, java.util.function.UnaryOperator<MineGoal> change) {
        Optional<WorkPost> post = PostWork.post(player.server, pos);
        if (post.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_POST);
        }
        MineGoal before = post.get().mineGoal();
        MineGoal goal = change.apply(before);
        Outcome outcome = KingdomWorldApi.change(player.server, "set the goal of a mine",
                state -> state.setPostMineGoal(player.getUUID(), pos, goal.ore(), goal.amount(), goal.autoDepth(), goal.depth()));
        if (outcome.ok() && !goal.sameMine(before)) {
            // A new mine: what it gives is estimated anew.
            ZoneSurveys.forget(pos);
            ZoneSurveys.request(pos);
        }
        return outcome;
    }

    public static KingdomActions.Feedback hire(ServerPlayer player, WorldPos pos) {
        KingdomWorldState.Hiring[] hiring = new KingdomWorldState.Hiring[1];
        Outcome outcome = KingdomWorldApi.change(player.server, "hire a worker for a work post", state -> {
            hiring[0] = state.hire(player.getUUID(), pos);
            return hiring[0].outcome();
        });
        if (!outcome.ok() || hiring[0] == null || hiring[0].citizen().isEmpty()) {
            return KingdomActions.Feedback.error(outcome.ok() ? KingdomError.NO_UNEMPLOYED : outcome.error());
        }
        Citizen citizen = hiring[0].citizen().get();
        CitizenActions.afterProfessionChange(player.server, citizen);
        return KingdomActions.Feedback.ok(KEY + "hired", CitizenTexts.name(citizen),
                CitizenTexts.profession(citizen.professionId(), citizen.gender()));
    }

    public static KingdomActions.Feedback dismiss(ServerPlayer player, WorldPos pos, UUID citizenId) {
        Outcome outcome = KingdomWorldApi.change(player.server, "dismiss a worker of a work post",
                state -> state.dismiss(player.getUUID(), pos, citizenId));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Optional<Citizen> citizen = KingdomWorldApi.state(player.server).citizens().get(citizenId);
        citizen.ifPresent(c -> CitizenActions.afterProfessionChange(player.server, c));
        return citizen.map(c -> KingdomActions.Feedback.ok(KEY + "dismissed", CitizenTexts.name(c)))
                .orElseGet(() -> KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN));
    }
}
