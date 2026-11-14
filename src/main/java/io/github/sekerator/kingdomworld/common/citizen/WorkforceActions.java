package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.PostContainers;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.life.LifeWork;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.Workforce;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The window of the workers of a kingdom: what every citizen is doing and lacks, every post with its workers and its container,
 * and what the king does there without walking to the posts: dismiss a citizen, put it on any post with a free seat (moving it
 * from other work in one step) or give it a profession without a post, rename a post or take everybody off it. Everything
 * needs the right to manage citizens, the same as giving professions at the throne. The window gets one page at a time when it
 * asks; the numbers of citizens far from players come from their abstract work.
 */
public final class WorkforceActions {
    private static final String KEY = "kingdomworld.workforce.";
    /** Rows a window asks for when it opens before it knows its height. */
    public static final int DEFAULT_PAGE = 8;
    /** How far from a settlement without a city of its own a post still belongs to it, in blocks. */
    private static final int SETTLEMENT_REACH = 48;

    private WorkforceActions() {
    }

    // ---------------------------------------------------------------- opening and pages

    /**
     * {@code /kingdom workers}: the window for the player's own kingdom.
     */
    public static KingdomActions.Feedback open(ServerPlayer player) {
        Optional<UUID> kingdom = ownKingdom(player);
        if (kingdom.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER);
        }
        if (!mayManage(player, kingdom.get())) {
            return KingdomActions.Feedback.fail(KEY + "no_right");
        }
        send(player, kingdom.get(), WorkforceView.Query.of(WorkforceView.Mode.CITIZENS, DEFAULT_PAGE), true);
        return KingdomActions.Feedback.ok(KEY + "opened");
    }

    /**
     * What a click on a citizen opens: the window of its post, or the window of the workers at the citizen.
     *
     * @param post    the post of the citizen, {@code null} if it has none
     * @param workers the page of the window of the workers with the citizen, {@code null} if the post opens
     */
    public record ClickTarget(UUID kingdomId, @Nullable WorkPost post, @Nullable WorkforceView.Query workers) {
    }

    /**
     * What a click of a player on a citizen opens: for one who manages the citizens of the citizen's kingdom, the window of the
     * citizen's post, as if the block of the post was clicked, or, for a citizen without a post (without work, of a profession
     * without a post, an adventurer, a soldier), the window of the workers with the citizen chosen in the list; nothing for
     * anybody else.
     */
    public static Optional<ClickTarget> clickTarget(ServerPlayer player, CitizenEntity body) {
        UUID citizenId = body.citizenId();
        Optional<Citizen> citizen = citizenId == null ? Optional.empty() : KingdomWorldApi.state(player.server).citizens().get(citizenId);
        if (citizen.isEmpty() || !KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), citizen.get().kingdomId())
                || !mayManage(player, citizen.get().kingdomId())) {
            return Optional.empty();
        }
        UUID kingdomId = citizen.get().kingdomId();
        Optional<WorkPost> post = PostWork.postOf(player.server, citizen.get()).filter(found -> found.kingdomId().equals(kingdomId));
        if (post.isPresent()) {
            return Optional.of(new ClickTarget(kingdomId, post.get(), null));
        }
        return Optional.of(new ClickTarget(kingdomId, null, focus(citizen.get().id(), DEFAULT_PAGE)));
    }

    /**
     * A player clicked a citizen: the window of {@link #clickTarget} opens.
     *
     * @return whether a window opened; otherwise the player is told who the citizen is, as before
     */
    public static boolean openFor(ServerPlayer player, CitizenEntity body) {
        Optional<ClickTarget> target = clickTarget(player, body);
        if (target.isEmpty()) {
            return false;
        }
        if (target.get().post() != null) {
            io.github.sekerator.kingdomworld.common.citizen.work.WorkPostActions.send(player, target.get().post(), true);
        } else {
            send(player, target.get().kingdomId(), target.get().workers(), true);
        }
        return true;
    }

    /**
     * The list of citizens at the page of one of them, which the window chooses: the window of the workers opened by a click on
     * the citizen.
     */
    public static WorkforceView.Query focus(UUID citizenId, int pageSize) {
        return new WorkforceView.Query(WorkforceView.Mode.CITIZENS, "", "", false, 0, pageSize, citizenId, null);
    }

    /**
     * A page asked for by the window, the key or the throne.
     *
     * @param kingdomId the kingdom, {@code null} for the player's own
     */
    public static void request(ServerPlayer player, @Nullable UUID kingdomId, boolean open, WorkforceView.Query query) {
        Optional<UUID> kingdom = kingdomId != null ? Optional.of(kingdomId) : ownKingdom(player);
        if (kingdom.isEmpty() || !KingdomWorldApi.state(player.server).membership().isMember(player.getUUID(), kingdom.get())) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        if (!mayManage(player, kingdom.get())) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "no_right"));
            return;
        }
        send(player, kingdom.get(), query, open);
    }

    private static Optional<UUID> ownKingdom(ServerPlayer player) {
        return KingdomWorldApi.kingdomOf(player.server, player.getUUID()).map(Kingdom::id);
    }

    private static boolean mayManage(ServerPlayer player, UUID kingdomId) {
        return KingdomWorldApi.state(player.server).membership().hasPermission(player.getUUID(), kingdomId, Permission.MANAGE_CITIZENS);
    }

    static void send(ServerPlayer player, UUID kingdomId, WorkforceView.Query query, boolean open) {
        KingdomWorldNetwork.sendToPlayer(player, new WorkforcePackets.View(view(player, kingdomId, query), open));
    }

    /**
     * One page of the window for a player who manages the citizens of the kingdom.
     */
    public static WorkforceView view(ServerPlayer player, UUID kingdomId, WorkforceView.Query query) {
        MinecraftServer server = player.server;
        KingdomWorldState state = KingdomWorldApi.state(server);
        String kingdomName = KingdomWorldApi.kingdom(server, kingdomId).map(Kingdom::name).orElse("");
        List<Citizen> people = state.citizens().registry().citizensOf(kingdomId);
        List<WorkPost> posts = state.citizens().posts().postsOf(kingdomId);
        Map<WorldPos, Integer> numbers = numbers(posts);
        Places places = Places.of(server, kingdomId);
        WorkforceView.Header header = header(server, kingdomId, people, posts, places);
        Viewer viewer = new Viewer(Dimensions.id(player.level()), player.blockPosition());
        WorkforceView.Query asked = query;
        return switch (query.mode()) {
            case CITIZENS -> {
                List<Citizen> rows = new ArrayList<>();
                for (Citizen citizen : people) {
                    if (matches(citizen, query, places)) {
                        rows.add(citizen);
                    }
                }
                rows.sort(Workforce.problemsFirst());
                WorkforceView.Query page = clamp(onPageOf(query, rows), rows.size());
                List<WorkforceView.CitizenRow> shown = new ArrayList<>();
                for (Citizen citizen : slice(rows, page)) {
                    shown.add(citizenRow(server, citizen, ownStanding(citizen), places, numbers, viewer.distance(citizen.workplace()), viewer));
                }
                yield new WorkforceView(page, kingdomId, kingdomName, header, rows.size(), shown, List.of(), List.of(), null, null);
            }
            case POSTS -> {
                List<WorkPost> rows = new ArrayList<>();
                for (WorkPost post : posts) {
                    if (matches(post, query, places)) {
                        rows.add(post);
                    }
                }
                rows.sort(postOrder());
                WorkforceView.Query page = clamp(query, rows.size());
                List<WorkforceView.PostRow> shown = new ArrayList<>();
                for (WorkPost post : slice(rows, page)) {
                    shown.add(postRow(server, post, numbers, places, viewer.distance(post.pos())));
                }
                yield new WorkforceView(page, kingdomId, kingdomName, header, rows.size(), List.of(), shown, List.of(), null, null);
            }
            case CHOOSE_POST -> {
                Optional<Citizen> citizen = query.citizen() == null ? Optional.empty() : state.citizens().get(query.citizen())
                        .filter(found -> found.kingdomId().equals(kingdomId));
                if (citizen.isEmpty()) {
                    yield new WorkforceView(asked.withPage(0), kingdomId, kingdomName, header, 0, List.of(), List.of(), List.of(), null, null);
                }
                Citizen who = citizen.get();
                WorldPos from = who.lastPos();
                List<WorkPost> rows = new ArrayList<>();
                for (WorkPost post : posts) {
                    if (post.hasProfession() && post.freeSeats() > 0 && !post.pos().equals(who.workplace())) {
                        rows.add(post);
                    }
                }
                rows.sort(Comparator.comparingDouble(post -> post.pos().dimension().equals(from.dimension())
                        ? post.pos().distanceSqr(from) : Double.MAX_VALUE));
                WorkforceView.Query page = clamp(query, rows.size());
                List<WorkforceView.PostRow> shown = new ArrayList<>();
                for (WorkPost post : slice(rows, page)) {
                    shown.add(postRow(server, post, numbers, places, distance(from, post.pos())));
                }
                List<String> professions = new ArrayList<>();
                if (!Professions.isMilitary(who.professionId())) {
                    for (Profession profession : Workforce.professionsWithoutPost()) {
                        if (professions.size() < WorkforceView.MAX_PROFESSIONS) {
                            professions.add(profession.id());
                        }
                    }
                }
                yield new WorkforceView(page, kingdomId, kingdomName, header, rows.size(), List.of(), shown, professions,
                        citizenRow(server, who, ownStanding(who), places, numbers, viewer.distance(who.workplace()), viewer), null);
            }
            case CHOOSE_CITIZEN -> {
                Optional<WorkPost> post = query.post() == null ? Optional.empty()
                        : PostWork.post(server, worldPos(query.post())).filter(found -> found.kingdomId().equals(kingdomId));
                if (post.isEmpty()) {
                    yield new WorkforceView(asked.withPage(0), kingdomId, kingdomName, header, 0, List.of(), List.of(), List.of(), null, null);
                }
                List<Citizen> rows = Workforce.candidates(people, post.get());
                WorkforceView.Query page = clamp(query, rows.size());
                List<WorkforceView.CitizenRow> shown = new ArrayList<>();
                for (Citizen citizen : slice(rows, page)) {
                    shown.add(citizenRow(server, citizen, Workforce.candidate(citizen, post.get()), places, numbers,
                            distance(citizen.lastPos(), post.get().pos()), viewer));
                }
                yield new WorkforceView(page, kingdomId, kingdomName, header, rows.size(), shown, List.of(), List.of(), null,
                        postRow(server, post.get(), numbers, places, viewer.distance(post.get().pos())));
            }
        };
    }

    /**
     * Where the window is looked at from.
     */
    private record Viewer(String dimension, BlockPos pos) {
        int distance(@Nullable WorldPos target) {
            if (target == null || !target.dimension().equals(dimension)) {
                return WorkforceView.FAR;
            }
            return (int) Math.round(Math.sqrt(target.distanceSqr(new WorldPos(dimension, pos.getX(), pos.getY(), pos.getZ()))));
        }
    }

    private static int distance(WorldPos from, WorldPos to) {
        return from.dimension().equals(to.dimension()) ? (int) Math.round(Math.sqrt(from.distanceSqr(to))) : WorkforceView.FAR;
    }

    private static WorldPos worldPos(WorkforceView.PostRef ref) {
        return new WorldPos(ref.dimension(), ref.pos().getX(), ref.pos().getY(), ref.pos().getZ());
    }

    private static WorkforceView.PostRef ref(WorldPos pos) {
        return new WorkforceView.PostRef(pos.dimension(), new BlockPos(pos.x(), pos.y(), pos.z()));
    }

    /**
     * The page of the citizen the list was opened at, if it is in the list; the page asked for otherwise.
     */
    private static WorkforceView.Query onPageOf(WorkforceView.Query query, List<Citizen> rows) {
        if (query.citizen() == null) {
            return query;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(query.citizen())) {
                return query.withPage(i / query.pageSize());
            }
        }
        return query;
    }

    /**
     * The page asked for, moved back to the last one when the list got shorter.
     */
    private static WorkforceView.Query clamp(WorkforceView.Query query, int total) {
        int pages = Math.max(1, (total + query.pageSize() - 1) / query.pageSize());
        return query.page() < pages ? query : query.withPage(pages - 1);
    }

    private static <T> List<T> slice(List<T> rows, WorkforceView.Query page) {
        int from = Math.min(rows.size(), page.page() * page.pageSize());
        return rows.subList(from, Math.min(rows.size(), from + page.pageSize()));
    }

    /**
     * The number of every post among the posts of its kingdom with the same profession, in the order they were found.
     */
    private static Map<WorldPos, Integer> numbers(List<WorkPost> posts) {
        Map<String, Integer> counters = new HashMap<>();
        Map<WorldPos, Integer> numbers = new HashMap<>();
        for (WorkPost post : posts) {
            numbers.put(post.pos(), counters.merge(post.professionId(), 1, Integer::sum));
        }
        return numbers;
    }

    private static Comparator<WorkPost> postOrder() {
        List<Profession> order = Professions.REGISTRY.all();
        return Comparator.comparing((WorkPost post) -> !post.status().isProblem())
                .thenComparingInt(post -> {
                    for (int i = 0; i < order.size(); i++) {
                        if (order.get(i).id().equals(post.professionId())) {
                            return i;
                        }
                    }
                    return order.size();
                });
    }

    // ---------------------------------------------------------------- filters

    private static boolean matches(Citizen citizen, WorkforceView.Query query, Places places) {
        if (!query.profession().isEmpty() && !query.profession().equals(citizen.professionId())) {
            return false;
        }
        if (query.problems() && !Workforce.hasProblem(citizen)) {
            return false;
        }
        return placeMatches(query.place(), places.of(citizen));
    }

    private static boolean matches(WorkPost post, WorkforceView.Query query, Places places) {
        if (!query.profession().isEmpty() && !query.profession().equals(post.professionId())) {
            return false;
        }
        if (query.problems() && !post.status().isProblem()) {
            return false;
        }
        return placeMatches(query.place(), places.of(post.pos()));
    }

    /**
     * Whether a citizen or post of a place passes the filter: everything for an empty one, those outside every settlement and
     * city for {@code -}, otherwise those of the place.
     */
    private static boolean placeMatches(String asked, String place) {
        if (asked.isEmpty()) {
            return true;
        }
        return asked.equals("-") ? place.isEmpty() : asked.equals(place);
    }

    /**
     * The settlements and cities of a kingdom, and which of them a citizen or a post belongs to.
     */
    private record Places(List<WorkforceView.Place> list, List<Area> areas, Map<Integer, String> settlementKeys,
                          List<io.github.sekerator.kingdomworld.core.npc.Settlement> loose) {
        private record Area(String key, String dimension, int x, int z, int reach) {
        }

        static Places of(MinecraftServer server, UUID kingdomId) {
            List<WorkforceView.Place> list = new ArrayList<>();
            List<Area> areas = new ArrayList<>();
            Map<Long, io.github.sekerator.kingdomworld.core.npc.Settlement> byCity = new HashMap<>();
            List<io.github.sekerator.kingdomworld.core.npc.Settlement> settlements = new ArrayList<>();
            for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement
                    : io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server).settlementsOf(kingdomId)) {
                if (settlement.tier() == io.github.sekerator.kingdomworld.core.land.Tier.CAPITAL
                        || settlement.status() == io.github.sekerator.kingdomworld.core.npc.Settlement.Status.RUINED) {
                    continue;
                }
                settlements.add(settlement);
                if (settlement.cityId() != 0) {
                    byCity.put(settlement.cityId(), settlement);
                }
            }
            Optional<io.github.sekerator.kingdomworld.core.npc.NpcNames.Name> realm = io.github.sekerator.kingdomworld.common.npc.NpcWork.name(kingdomId);
            for (io.github.sekerator.kingdomworld.common.road.CityWalls.Place city : io.github.sekerator.kingdomworld.common.road.CityWalls.places(server)) {
                if (!city.kingdomId().equals(kingdomId) || list.size() >= WorkforceView.MAX_PLACES) {
                    continue;
                }
                String key = "c" + city.cityId();
                String latin = city.name();
                String cyrillic = city.name();
                io.github.sekerator.kingdomworld.core.npc.Settlement settlement = byCity.get(city.cityId());
                if (city.cityId() == 0 && realm.isPresent()) {
                    latin = realm.get().latin();
                    cyrillic = realm.get().cyrillic();
                } else if (settlement != null) {
                    latin = settlement.name().latin();
                    cyrillic = settlement.name().cyrillic();
                }
                list.add(new WorkforceView.Place(key, latin, cyrillic));
                areas.add(new Area(key, city.dimension(), city.x(), city.z(),
                        io.github.sekerator.kingdomworld.common.road.CityWalls.area(server, city)));
            }
            Map<Integer, String> keys = new HashMap<>();
            List<io.github.sekerator.kingdomworld.core.npc.Settlement> loose = new ArrayList<>();
            for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement : settlements) {
                String cityKey = "c" + settlement.cityId();
                if (settlement.cityId() != 0 && areas.stream().anyMatch(area -> area.key().equals(cityKey))) {
                    keys.put(settlement.id(), cityKey);
                    continue;
                }
                if (list.size() >= WorkforceView.MAX_PLACES) {
                    continue;
                }
                String key = "s" + settlement.id();
                keys.put(settlement.id(), key);
                loose.add(settlement);
                list.add(new WorkforceView.Place(key, settlement.name().latin(), settlement.name().cyrillic()));
            }
            return new Places(list, areas, keys, loose);
        }

        String of(Citizen citizen) {
            if (citizen.settlement() != 0) {
                String key = settlementKeys.get(citizen.settlement());
                if (key != null) {
                    return key;
                }
            }
            return of(citizen.home() != null ? citizen.home() : citizen.lastPos());
        }

        String of(WorldPos pos) {
            String best = "";
            double bestDistance = Double.MAX_VALUE;
            for (Area area : areas) {
                if (!area.dimension().equals(pos.dimension()) || Math.abs(area.x() - pos.x()) > area.reach()
                        || Math.abs(area.z() - pos.z()) > area.reach()) {
                    continue;
                }
                double distance = Math.pow(area.x() - pos.x(), 2) + Math.pow(area.z() - pos.z(), 2);
                if (distance < bestDistance) {
                    best = area.key();
                    bestDistance = distance;
                }
            }
            if (!best.isEmpty()) {
                return best;
            }
            for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement : loose) {
                double distance = Math.pow(settlement.x() - pos.x(), 2) + Math.pow(settlement.z() - pos.z(), 2);
                if (distance <= (double) SETTLEMENT_REACH * SETTLEMENT_REACH && distance < bestDistance) {
                    best = "s" + settlement.id();
                    bestDistance = distance;
                }
            }
            return best;
        }
    }

    // ---------------------------------------------------------------- rows

    private static WorkforceView.Header header(MinecraftServer server, UUID kingdomId, List<Citizen> people, List<WorkPost> posts, Places places) {
        Workforce.Counts counts = Workforce.counts(people);
        Map<String, Integer> lacking = new LinkedHashMap<>();
        Map<String, WorkforceView.Shortage> kinds = new HashMap<>();
        for (Citizen citizen : people) {
            for (WorkforceView.Lack lack : lacks(citizen)) {
                String key = lack.need().id() + "/" + lack.detail();
                lacking.merge(key, 1, Integer::sum);
                kinds.putIfAbsent(key, new WorkforceView.Shortage(lack.need(), lack.detail(), 0));
            }
        }
        List<WorkforceView.Shortage> shortages = new ArrayList<>();
        lacking.forEach((key, count) -> shortages.add(new WorkforceView.Shortage(kinds.get(key).need(), kinds.get(key).detail(), count)));
        shortages.sort(Comparator.comparingInt(WorkforceView.Shortage::count).reversed()
                .thenComparing(shortage -> shortage.need().ordinal()));
        int empty = 0;
        int free = 0;
        for (WorkPost post : posts) {
            if (!post.hasProfession()) {
                empty++;
            } else {
                free += post.freeSeats();
            }
        }
        int foodDays = CitizenActions.summary(server, kingdomId).foodDays();
        return new WorkforceView.Header(counts.working(), counts.unemployed(), counts.withoutPost(), counts.soldiers(), foodDays,
                shortages.size() > WorkforceView.MAX_SHORTAGES ? shortages.subList(0, WorkforceView.MAX_SHORTAGES) : shortages,
                places.list(), posts.size(), empty, free);
    }

    /**
     * How a citizen stands to its own work, for the list of citizens: a soldier, without work, without the post its
     * profession needs, or at work.
     */
    private static Workforce.Candidate ownStanding(Citizen citizen) {
        if (Professions.isMilitary(citizen.professionId())) {
            return Workforce.Candidate.SOLDIER;
        }
        if (Professions.UNEMPLOYED.id().equals(citizen.professionId())) {
            return Workforce.Candidate.UNEMPLOYED;
        }
        return Workforce.withoutPost(citizen) ? Workforce.Candidate.IDLE_SAME : Workforce.Candidate.OTHER_WORK;
    }

    private static List<WorkforceView.Lack> lacks(Citizen citizen) {
        List<WorkforceView.Lack> result = new ArrayList<>();
        for (Workforce.Need need : Workforce.needs(citizen)) {
            String detail = need == Workforce.Need.TOOL || need == Workforce.Need.BETTER_TOOL ? tool(citizen.professionId()) : "";
            result.add(new WorkforceView.Lack(need, detail));
        }
        return result;
    }

    /**
     * The kind of tool the work of a profession needs, as the texts of the window name it: {@code axe}, {@code pickaxe},
     * {@code fishing_rod}, {@code shears}, {@code weapon}, or empty.
     */
    static String tool(String professionId) {
        Equipment.ToolNeed need = ProfessionBehaviors.get(professionId).map(ProfessionBehavior::tool).orElse(Equipment.ToolNeed.NONE);
        return switch (need) {
            case NONE -> "";
            case PICKAXE_IF_ANY -> "pickaxe";
            default -> need.name().toLowerCase(Locale.ROOT);
        };
    }

    private static WorkforceView.CitizenRow citizenRow(MinecraftServer server, Citizen citizen, Workforce.Candidate candidate, Places places,
                                                       Map<WorldPos, Integer> numbers, int distance, Viewer viewer) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        WorkforceView.Workplace workplace = null;
        WorldPos at = citizen.workplace();
        if (at != null) {
            Optional<WorkPost> post = state.citizens().posts().get(at);
            if (post.isPresent()) {
                workplace = new WorkforceView.Workplace(ref(at), true, post.get().name(), numbers.getOrDefault(at, 0), post.get().professionId(),
                        viewer.distance(at));
            } else if (state.citizens().sites().get(at).map(Site::kind).isPresent()) {
                workplace = new WorkforceView.Workplace(ref(at), false, "", 0, citizen.professionId(), viewer.distance(at));
            }
        }
        return new WorkforceView.CitizenRow(citizen.id(), citizen.nameSeed(), citizen.gender() == Gender.FEMALE, citizen.professionId(),
                citizen.state(), Math.round(citizen.health()), Math.round(citizen.maxHealth()), citizen.mood(), places.of(citizen), workplace,
                doing(server, citizen), citizen.workStatus(), lacks(citizen), candidate, distance);
    }

    /**
     * What a citizen is doing: by the goals of its body near players, by its day and its work far from them.
     */
    static Workforce.Doing doing(MinecraftServer server, Citizen citizen) {
        Workforce.Doing far = Workforce.doing(citizen, LifeWork.phase(server, citizen.id()), LifeWork.isSick(citizen.id()),
                LifeWork.festivalRest(server, citizen.kingdomId()));
        CitizenEntity body = CitizenBodies.body(citizen.id());
        if (body == null || far == Workforce.Doing.HEALING || far == Workforce.Doing.SERVING) {
            return far;
        }
        Workforce.Doing near = body.doing();
        if (near == null) {
            return far;
        }
        if ((near == Workforce.Doing.WORKING || near == Workforce.Doing.PREPARING)
                && (Workforce.withoutPost(citizen) || citizen.workStatus().isProblem())) {
            // A worker whose work stands still says why rather than what it tries.
            return Workforce.Doing.STANDING;
        }
        return near;
    }

    private static WorkforceView.PostRow postRow(MinecraftServer server, WorkPost post, Map<WorldPos, Integer> numbers, Places places, int distance) {
        PostWork.refreshStatus(server, post);
        ServerLevel level = PostWork.level(server, post.pos());
        BlockPos pos = new BlockPos(post.pos().x(), post.pos().y(), post.pos().z());
        boolean loaded = level != null && level.isLoaded(pos);
        boolean container = post.hasContainer();
        List<WorkforceView.Content> contents = new ArrayList<>();
        int items = 0;
        if (loaded) {
            PostContainers.Container found = PostContainers.find(level, pos);
            container = found != null;
            if (found != null) {
                Map<String, Integer> held = PostContainers.contents(found.handler());
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(held.entrySet());
                sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
                for (Map.Entry<String, Integer> entry : sorted) {
                    items += entry.getValue();
                    ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                    if (item != null && contents.size() < WorkforceView.MAX_CONTENTS) {
                        contents.add(new WorkforceView.Content(new ItemStack(item), entry.getValue()));
                    }
                }
            }
        }
        return new WorkforceView.PostRow(ref(post.pos()), post.name(), numbers.getOrDefault(post.pos(), 0), post.professionId(),
                places.of(post.pos()), distance, post.workers().size(), post.seats(), post.status(), loaded, container, contents, items);
    }

    // ---------------------------------------------------------------- actions

    /**
     * What the king does in the window; answered with the result and the page the window shows.
     */
    public static void act(ServerPlayer player, WorkforcePackets.Action action) {
        MinecraftServer server = player.server;
        UUID kingdomId = action.kingdomId();
        if (!KingdomWorldApi.state(server).membership().isMember(player.getUUID(), kingdomId)) {
            KingdomActions.reply(player, KingdomActions.Feedback.error(KingdomError.NOT_A_MEMBER));
            return;
        }
        if (!mayManage(player, kingdomId)) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(KEY + "no_right"));
            return;
        }
        KingdomActions.Feedback feedback = switch (action.kind()) {
            case DISMISS -> action.citizen() == null ? KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN)
                    : dismiss(player, kingdomId, action.citizen());
            case ASSIGN -> action.citizen() == null || action.post() == null ? KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST)
                    : assign(player, kingdomId, action.citizen(), worldPos(action.post()));
            case PROFESSION -> action.citizen() == null ? KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN)
                    : giveProfession(player, kingdomId, action.citizen(), action.text());
            case RENAME -> action.post() == null ? KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST)
                    : rename(player, kingdomId, worldPos(action.post()), action.text());
            case CLEAR -> action.post() == null ? KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST)
                    : clear(player, kingdomId, worldPos(action.post()));
        };
        KingdomActions.reply(player, feedback);
        send(player, kingdomId, action.query(), false);
    }

    private static Optional<Citizen> citizenOf(MinecraftServer server, UUID kingdomId, UUID citizenId) {
        return KingdomWorldApi.state(server).citizens().get(citizenId).filter(citizen -> citizen.kingdomId().equals(kingdomId));
    }

    private static Optional<WorkPost> postOf(MinecraftServer server, UUID kingdomId, WorldPos pos) {
        return PostWork.post(server, pos).filter(post -> post.kingdomId().equals(kingdomId));
    }

    /**
     * The name of a post as the window and the messages show it: the one it was given, or its profession and number.
     */
    public static Component postName(MinecraftServer server, WorkPost post) {
        return post.name().isEmpty() ? Component.translatable(KEY + "post_name", CitizenTexts.profession(post.professionId()),
                KingdomWorldApi.state(server).citizens().postNumber(post)) : Component.literal(post.name());
    }

    /**
     * A citizen is without work again; a soldier leaves only through the army.
     */
    public static KingdomActions.Feedback dismiss(ServerPlayer player, UUID kingdomId, UUID citizenId) {
        MinecraftServer server = player.server;
        if (citizenOf(server, kingdomId, citizenId).isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN);
        }
        Outcome outcome = KingdomWorldApi.change(server, "dismiss a citizen from the window of the workers",
                state -> state.setProfession(player.getUUID(), citizenId, Professions.UNEMPLOYED.id()));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Citizen citizen = KingdomWorldApi.state(server).citizens().get(citizenId).orElse(null);
        if (citizen == null) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN);
        }
        CitizenActions.afterProfessionChange(server, citizen);
        return KingdomActions.Feedback.ok(KEY + "dismissed", CitizenTexts.name(citizen));
    }

    /**
     * A citizen goes to work at a post, leaving whatever it did, a seat at another post as well.
     */
    public static KingdomActions.Feedback assign(ServerPlayer player, UUID kingdomId, UUID citizenId, WorldPos pos) {
        MinecraftServer server = player.server;
        if (citizenOf(server, kingdomId, citizenId).isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN);
        }
        Optional<WorkPost> post = postOf(server, kingdomId, pos);
        if (post.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST);
        }
        WorldPos before = KingdomWorldApi.state(server).citizens().get(citizenId).map(Citizen::workplace).orElse(null);
        Outcome outcome = KingdomWorldApi.change(server, "put a citizen on a work post", state -> state.assign(player.getUUID(), pos, citizenId));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        Citizen citizen = KingdomWorldApi.state(server).citizens().get(citizenId).orElse(null);
        if (citizen == null) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN);
        }
        CitizenActions.afterProfessionChange(server, citizen);
        PostWork.refreshStatus(server, post.get());
        if (before != null) {
            PostWork.post(server, before).ifPresent(old -> PostWork.refreshStatus(server, old));
        }
        return KingdomActions.Feedback.ok(KEY + "assigned", CitizenTexts.name(citizen), postName(server, post.get()),
                CitizenTexts.profession(citizen.professionId(), citizen.gender()));
    }

    /**
     * A citizen gets a profession that needs no post (an adventurer; a farmer, who finds a composter).
     */
    public static KingdomActions.Feedback giveProfession(ServerPlayer player, UUID kingdomId, UUID citizenId, String professionId) {
        if (citizenOf(player.server, kingdomId, citizenId).isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_CITIZEN);
        }
        if (Workforce.professionsWithoutPost().stream().noneMatch(profession -> profession.id().equals(professionId))) {
            return KingdomActions.Feedback.error(KingdomError.NOT_A_POST_PROFESSION);
        }
        return CitizenActions.setProfession(player, citizenId, professionId);
    }

    public static KingdomActions.Feedback rename(ServerPlayer player, UUID kingdomId, WorldPos pos, String name) {
        MinecraftServer server = player.server;
        if (postOf(server, kingdomId, pos).isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST);
        }
        Outcome outcome = KingdomWorldApi.change(server, "rename a work post", state -> state.renamePost(player.getUUID(), pos, name));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error());
        }
        WorkPost post = PostWork.post(server, pos).orElse(null);
        if (post == null) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST);
        }
        return KingdomActions.Feedback.ok(post.name().isEmpty() ? KEY + "renamed_default" : KEY + "renamed", postName(server, post));
    }

    /**
     * Everybody at a post is without work again.
     */
    public static KingdomActions.Feedback clear(ServerPlayer player, UUID kingdomId, WorldPos pos) {
        MinecraftServer server = player.server;
        Optional<WorkPost> post = postOf(server, kingdomId, pos);
        if (post.isEmpty()) {
            return KingdomActions.Feedback.error(KingdomError.UNKNOWN_POST);
        }
        List<UUID> workers = List.copyOf(post.get().workers());
        Outcome outcome = KingdomWorldApi.change(server, "take everybody off a work post", state -> state.clearPost(player.getUUID(), pos));
        if (!outcome.ok()) {
            return KingdomActions.Feedback.error(outcome.error() == KingdomError.UNCHANGED ? KingdomError.NOT_A_WORKER : outcome.error());
        }
        for (UUID worker : workers) {
            KingdomWorldApi.state(server).citizens().get(worker).ifPresent(citizen -> CitizenActions.afterProfessionChange(server, citizen));
        }
        PostWork.refreshStatus(server, post.get());
        return KingdomActions.Feedback.ok(KEY + "cleared", postName(server, post.get()),
                io.github.sekerator.kingdomworld.common.life.LifeTexts.citizens(workers.size()));
    }
}
