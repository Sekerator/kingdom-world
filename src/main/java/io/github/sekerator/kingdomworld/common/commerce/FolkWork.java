package io.github.sekerator.kingdomworld.common.commerce;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.FolkSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.citizen.work.Equipment;
import io.github.sekerator.kingdomworld.common.citizen.work.FarmerBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehavior;
import io.github.sekerator.kingdomworld.common.citizen.work.ProfessionBehaviors;
import io.github.sekerator.kingdomworld.common.economy.EconomySettings;
import io.github.sekerator.kingdomworld.common.production.WorkshopBehavior;
import io.github.sekerator.kingdomworld.common.road.CityAreas;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.GatheringRules;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.TaskRisks;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.commerce.Business;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.road.CityCharter;
import io.github.sekerator.kingdomworld.core.road.FolkJobs;
import io.github.sekerator.kingdomworld.core.road.FolkWorkRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The work of the townsfolk of the cities of a kingdom of players: on the free seats of the posts of the crown in the square of
 * their city, and as hands in the workshops and shops of citizens. Once a day of the townsfolk of a city ({@link #day}) those who
 * left the city leave their work, the idle take the free seats while the treasury holds the days of wages the rules ask, and the
 * workshops take or let go a hand; every step of a kingdom the posts with townsfolk work as their named workers do far from
 * players, at the pace of the townsfolk, without a body and without a record ({@link #work}); on a payday the townsfolk of a post
 * are paid as one claim after the named workers, and those the treasury could not pay leave the post ({@link #afterPayday}).
 *
 * <p>The townsfolk set no status of a post: its window shows them on a line of their own. Their tools lie under the id of the post
 * ({@link WorkPost#folkId()}) and wear out as those of named workers do.
 *
 * <p>Server thread only.
 */
public final class FolkWork {
    /** The key of the progress of a workshop the time of its townsfolk is kept under. */
    public static final String FOLK_BANK = "kingdomworld:folk_work";
    /** The key of the progress of a farm the harvest of its townsfolk is kept under. */
    public static final String FOLK_FARM = "kingdomworld:folk_farm";
    /** The mood the townsfolk of each post work at: that of the named citizens of its city on its last day of the townsfolk. */
    private static final Map<WorldPos, Integer> MOODS = new HashMap<>();

    private FolkWork() {
    }

    public static void reset() {
        MOODS.clear();
    }

    /**
     * Whether the townsfolk of a kingdom work: a kingdom whose money stays in its cities, while the key is on.
     */
    public static boolean active(MinecraftServer server, UUID kingdomId) {
        return CityOf.active(server, kingdomId) && FolkSettings.work().atPosts();
    }

    // ---------------------------------------------------------------- the step of a kingdom

    /**
     * The townsfolk on the posts of carriers of a kingdom: they carry with its named carriers.
     */
    public static long carriers(MinecraftServer server, UUID kingdomId) {
        long folk = 0;
        for (WorkPost post : KingdomWorldApi.state(server).citizens().posts().postsOf(kingdomId, Professions.CARRIER.id())) {
            folk += post.folkWorkers();
        }
        return folk;
    }

    /**
     * One step of the townsfolk of a post: they gather, farm or make by the rules of its profession, at
     * {@link FolkWorkRules#postRatePercent()} of the pace of a named worker each and the mood of their city, as beginners. The
     * carriers carry with those of the kingdom. An accident sends one of them off the post, idle until the next day of the
     * townsfolk; they have no body to wound.
     */
    public static void work(MinecraftServer server, WorkPost post, int interval) {
        int folk = post.folkWorkers();
        if (folk <= 0 || interval <= 0 || Professions.CARRIER.id().equals(post.professionId())) {
            return;
        }
        Optional<ProfessionBehavior> behavior = ProfessionBehaviors.get(post.professionId());
        if (behavior.isEmpty()) {
            return;
        }
        double rate = FolkSettings.work().postRatePercent() / 100.0;
        double speed = EconomySettings.workSpeed(MOODS.getOrDefault(post.pos(), CitizenNeeds.NEUTRAL_MOOD));
        double pace = speed * folk * rate;
        if (pace <= 0.0) {
            return;
        }
        boolean worked;
        Equipment.Hands hands = Equipment.of(server, post.folkId());
        if (Professions.FARMER.id().equals(post.professionId())) {
            worked = farm(server, post, pace, folk, interval);
        } else if (Professions.isWorkshop(post.professionId())) {
            worked = WorkshopBehavior.makeAt(server, post, post.folkId(), FOLK_BANK, pace, 0, (int) Math.ceil(folk * rate), interval).made() > 0;
        } else {
            PostWork.Gathered gathered = PostWork.gather(server, post, behavior.get(), post.folkId(), pace, interval);
            worked = gathered.worked();
        }
        if (worked) {
            accident(server, post, hands, (int) Math.min(Integer.MAX_VALUE, Math.round(interval * (double) folk * rate)));
        }
    }

    /**
     * The townsfolk of a farm harvest what its fields give at their pace, by what was last seen of them: by a named farmer of the
     * post or by the townsfolk themselves while its place was loaded.
     *
     * @return whether they harvested
     */
    private static boolean farm(MinecraftServer server, WorkPost post, double pace, int folk, int interval) {
        if (!PostWork.onOwnLand(server, post)) {
            return false;
        }
        if (post.farmPlots() <= 0 || post.farmProduct().isEmpty()) {
            FarmerBehavior.surveyPost(server, post);
            if (post.farmPlots() <= 0 || post.farmProduct().isEmpty()) {
                return false;
            }
        }
        net.minecraft.world.item.Item item = PostWork.item(post.farmProduct());
        if (item == null) {
            return false;
        }
        double progress = post.progress().getOrDefault(FOLK_FARM, 0.0) + CitizenNeeds.farmOutput(post.farmPlots(), interval) * pace;
        int whole = (int) Math.min(Math.floor(progress), 64.0 * 9 * Math.max(1, folk));
        List<ItemStack> harvest = PostWork.stacks(Map.of(post.farmProduct(), whole));
        if (whole > 0 && !PostWork.fits(server, post, harvest)) {
            // Nowhere to bring all of the harvest: it waits in the fields.
            return false;
        }
        Map<String, Double> left = new java.util.LinkedHashMap<>(post.progress());
        left.put(FOLK_FARM, progress - whole);
        post.setProgress(left);
        for (ItemStack stack : harvest) {
            PostWork.deliver(server, post, stack);
        }
        if (whole > 0) {
            PostWork.record(server, post, post.farmProduct(), whole);
        }
        return whole > 0;
    }

    /**
     * The townsfolk of a post may have an accident, as likely as a beginner working as long as all of them; one of them leaves the
     * post, idle until the next day of the townsfolk.
     */
    private static void accident(MinecraftServer server, WorkPost post, Equipment.Hands hands, int exposure) {
        GatheringRules rules = GatheringSettings.rules();
        RandomSource random = server.overworld().getRandom();
        TaskRisks.Accident accident = TaskRisks.roll(rules.risk(post.professionId()), rules.riskMultiplier(), exposure,
                Equipment.armorPoints(hands), 0, random.nextDouble(), random.nextDouble());
        if (accident != TaskRisks.Accident.NONE) {
            post.setFolkWorkers(post.folkWorkers() - 1);
        }
    }

    // ---------------------------------------------------------------- the day of the townsfolk

    /**
     * What the posts of a kingdom offer its townsfolk on the days of its cities, worked out once a step: the posts each city may
     * fill and all the wages of the kingdom a day.
     */
    public static final class Plan {
        final Map<Long, List<WorkPost>> posts = new HashMap<>();
        final long funds;
        long dailyWages;

        Plan(long funds) {
            this.funds = funds;
        }
    }

    /**
     * Works out the posts of a kingdom by city and its wages a day; the townsfolk of a post that no longer takes them (another
     * profession, outside every square, off the land of the kingdom) leave it.
     */
    public static Plan plan(MinecraftServer server, UUID kingdomId) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Treasury> treasury = state.economy().treasury(kingdomId);
        Plan plan = new Plan(treasury.map(Treasury::funds).orElse(0L));
        var rules = EconomySettings.rules();
        for (Citizen citizen : state.citizens().registry().citizensOf(kingdomId)) {
            long wage = treasury.map(found -> found.wage(citizen.professionId(), rules)).orElse(0L);
            plan.dailyWages += new Economy.WageClaim(citizen.id(), citizen.professionId(),
                    io.github.sekerator.kingdomworld.common.army.ArmyStaff.wagePermille(server, citizen.id())).scaled(wage);
        }
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId)) {
            long city = -1;
            if (post.hasProfession() && FolkJobs.takes(post.professionId()) && PostWork.onOwnLand(server, post)) {
                city = CityAreas.at(server, kingdomId, post.pos().dimension(), post.pos().x(), post.pos().z()).map(CityAreas.Area::cityId).orElse(-1L);
            }
            if (city < 0) {
                post.setFolkWorkers(0);
                continue;
            }
            plan.posts.computeIfAbsent(city, c -> new ArrayList<>()).add(post);
            if (post.folkWorkers() > 0) {
                plan.dailyWages += Economy.WageClaim.folk(post.folkId(), post.professionId(), post.folkWorkers())
                        .scaled(treasury.map(found -> found.wage(post.professionId(), rules)).orElse(0L));
            }
        }
        return plan;
    }

    /**
     * The work of the townsfolk of a city on its day, after they ate, grew or left: those beyond the townsfolk leave their work
     * (hands first, then the posts), the idle take the free seats of its posts while the treasury holds the days of wages the
     * rules ask, then the workshops and shops of the city take a hand or let one go. What it came to is kept in the charter of
     * the city.
     *
     * @param folk the townsfolk of the city now
     * @param mood the mood of its named citizens: the townsfolk work at it
     */
    public static void day(MinecraftServer server, UUID kingdomId, CityCharter charter, int folk, int mood, Plan plan) {
        FolkWorkRules rules = FolkSettings.work();
        List<WorkPost> posts = plan.posts.getOrDefault(charter.cityId(), List.of());
        if (!rules.atPosts()) {
            // The key is off: the townsfolk leave the posts of the crown.
            for (WorkPost post : posts) {
                post.setFolkWorkers(0);
            }
        }
        List<Business> shops = Workshops.handPlaces(server, kingdomId, charter.cityId());
        int hands = 0;
        for (Business shop : shops) {
            hands += shop.folkHands();
        }
        int onPosts = 0;
        for (WorkPost post : posts) {
            onPosts += post.folkWorkers();
        }
        // Those who left the city leave their work: the hands of the workshops first, then the posts.
        int[] shrink = FolkJobs.shrink(folk, hands, onPosts);
        for (int i = shops.size() - 1; i >= 0 && shrink[0] > 0; i--) {
            Business shop = shops.get(i);
            int off = Math.min(shrink[0], shop.folkHands());
            shop.setFolkHands(shop.folkHands() - off);
            shrink[0] -= off;
            hands -= off;
        }
        for (int i = posts.size() - 1; i >= 0 && shrink[1] > 0; i--) {
            WorkPost post = posts.get(i);
            int off = Math.min(shrink[1], post.folkWorkers());
            post.setFolkWorkers(post.folkWorkers() - off);
            shrink[1] -= off;
            onPosts -= off;
        }
        int idle = Math.max(0, folk - hands - onPosts);
        if (rules.atPosts() && idle > 0 && !posts.isEmpty()) {
            Optional<Treasury> treasury = KingdomWorldApi.state(server).economy().treasury(kingdomId);
            var economyRules = EconomySettings.rules();
            int[] free = new int[posts.size()];
            long[] wages = new long[posts.size()];
            for (int i = 0; i < posts.size(); i++) {
                free[i] = posts.get(i).openSeats();
                String profession = posts.get(i).professionId();
                wages[i] = treasury.map(found -> found.wage(profession, economyRules)).orElse(0L);
            }
            int[] taken = FolkJobs.fill(idle, free, wages, plan.funds, plan.dailyWages, rules.wageReserveDays());
            for (int i = 0; i < posts.size(); i++) {
                if (taken[i] > 0) {
                    posts.get(i).setFolkWorkers(posts.get(i).folkWorkers() + taken[i]);
                    plan.dailyWages += taken[i] * wages[i];
                    idle -= taken[i];
                    onPosts += taken[i];
                }
            }
        }
        // The workshops and shops take one more hand or let one go, once a day each.
        hands += Workshops.changeHands(server, shops, idle, rules);
        idle = Math.max(0, folk - hands - onPosts);
        for (WorkPost post : posts) {
            MOODS.put(post.pos(), mood);
        }
        charter.setFolkWork(hands, idle);
        RoadWork.state(server).charterChanged();
    }

    // ---------------------------------------------------------------- the payday

    /**
     * After the payday of a kingdom: the townsfolk of a post the treasury could not pay in full leave it, one for every wage short;
     * what the townsfolk of each city were paid is what they earned today, the shares of the workshops come on top of it.
     */
    public static void afterPayday(MinecraftServer server, UUID kingdomId, Economy.Payday payday) {
        if (payday == Economy.Payday.NONE || !CityOf.active(server, kingdomId)) {
            return;
        }
        KingdomWorldState state = KingdomWorldApi.state(server);
        Optional<Treasury> treasury = state.economy().treasury(kingdomId);
        var rules = EconomySettings.rules();
        Map<Long, Long> earned = new HashMap<>();
        for (WorkPost post : state.citizens().posts().postsOf(kingdomId)) {
            Long paid = payday.pay().get(post.folkId());
            if (paid == null) {
                continue;
            }
            long city = payday.cityOf(post.folkId());
            if (city >= 0) {
                earned.merge(city, paid, Long::sum);
            }
            long wage = treasury.map(found -> found.wage(post.professionId(), rules)).orElse(0L);
            long due = wage * post.folkWorkers();
            if (wage > 0 && paid < due) {
                long short1 = (due - paid + wage - 1) / wage;
                post.setFolkWorkers((int) Math.max(0, post.folkWorkers() - short1));
            }
        }
        for (CityCharter charter : RoadWork.state(server).charters()) {
            if (charter.kingdomId().equals(kingdomId)) {
                charter.setFolkEarned(earned.getOrDefault(charter.cityId(), 0L));
            }
        }
        RoadWork.state(server).charterChanged();
    }

    /**
     * The townsfolk of a city earned a share of a workshop or a shop, after the tithe.
     */
    public static void earnedShare(MinecraftServer server, UUID kingdomId, long cityId, long amount) {
        if (amount <= 0) {
            return;
        }
        RoadWork.state(server).charter(kingdomId, cityId).ifPresent(charter -> {
            charter.setFolkEarned(Math.max(0, charter.folkEarned()) + amount);
            RoadWork.state(server).charterChanged();
        });
    }

    /**
     * The claims of the townsfolk of the posts of a kingdom and the city each is paid into, by the rule of the beds and homes.
     */
    public static void cities(MinecraftServer server, UUID kingdomId, Map<UUID, Long> into,
                              java.util.function.Function<WorldPos, Long> cityOf) {
        for (WorkPost post : KingdomWorldApi.state(server).citizens().posts().postsOf(kingdomId)) {
            if (post.folkWorkers() > 0) {
                into.put(post.folkId(), cityOf.apply(post.pos()));
            }
        }
    }
}
