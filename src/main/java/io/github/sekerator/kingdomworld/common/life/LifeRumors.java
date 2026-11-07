package io.github.sekerator.kingdomworld.common.life;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.MarketStock;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.DeathRecord;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.law.Wanted;
import io.github.sekerator.kingdomworld.core.life.DaySchedule;
import io.github.sekerator.kingdomworld.core.life.LifeState;
import io.github.sekerator.kingdomworld.core.life.Rumor;
import io.github.sekerator.kingdomworld.core.life.Rumors;
import io.github.sekerator.kingdomworld.core.road.BanditCamp;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.trade.Market;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * What innkeepers talk about. Every rumor is made from the world as it is: camps of robbers around the city, contracts on the
 * board of the guild, news of the kingdoms (foundings, the dead of battles and robberies, festivals and fairs), where the
 * markets sell cheap and whom the guards look for. The talk of a tavern changes once a game day.
 *
 * <p>Other parts of the mod and other mods add their own kinds of rumors with {@link #register}: the wars and weddings of later
 * stages come in this way.
 */
public final class LifeRumors {
    /** How far from a tavern its innkeeper hears of camps, in blocks. */
    public static final int CAMP_RANGE = 1_500;
    /** How many days news stays news. */
    public static final int NEWS_DAYS = 10;

    /**
     * A kind of rumors.
     */
    @FunctionalInterface
    public interface RumorSource {
        /**
         * What is true and worth telling in a tavern of a kingdom.
         *
         * @param tavern the middle of the tavern
         */
        List<Rumor> rumors(MinecraftServer server, UUID kingdomId, WorldPos tavern);
    }

    private static final Map<String, RumorSource> SOURCES = new LinkedHashMap<>();
    /** The rumors of each tavern today: building id to the day and the rumors. */
    private static final Map<Long, Map.Entry<Long, List<Rumor>>> TOLD = new HashMap<>();

    static {
        register(Rumors.CAMP, LifeRumors::camps);
        register(Rumors.CONTRACT, LifeRumors::contracts);
        register(Rumors.NEWS, LifeRumors::news);
        register(Rumors.PRICE, LifeRumors::prices);
        register(Rumors.WANTED, LifeRumors::wanted);
    }

    private LifeRumors() {
    }

    /**
     * Adds a kind of rumors.
     *
     * @throws IllegalArgumentException if a kind of the id is registered already
     */
    public static synchronized void register(String id, RumorSource source) {
        if (SOURCES.containsKey(id)) {
            throw new IllegalArgumentException("Rumors '" + id + "' are registered already");
        }
        SOURCES.put(id, source);
    }

    static void reset() {
        TOLD.clear();
    }

    /**
     * Everything the innkeeper of a tavern could tell, in a stable order.
     */
    public static List<Rumor> candidates(MinecraftServer server, UUID kingdomId, WorldPos tavern) {
        List<Rumor> all = new ArrayList<>();
        List<RumorSource> sources;
        synchronized (LifeRumors.class) {
            sources = List.copyOf(SOURCES.values());
        }
        for (RumorSource source : sources) {
            try {
                all.addAll(source.rumors(server, kingdomId, tavern));
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().warn("A source of rumors failed", e);
            }
        }
        return all;
    }

    /**
     * The rumors of a tavern today.
     */
    public static List<Rumor> today(MinecraftServer server, Building tavern) {
        long today = LifeWork.today(server);
        Map.Entry<Long, List<Rumor>> told = TOLD.get(tavern.id());
        if (told != null && told.getKey() == today) {
            return told.getValue();
        }
        List<Rumor> rumors = Rumors.pick(candidates(server, tavern.kingdomId(), LifeWork.center(tavern)), today, tavern.id(), LifeSettings.rules());
        TOLD.put(tavern.id(), Map.entry(today, rumors));
        return rumors;
    }

    /**
     * Forgets what the taverns told today, so that they make up new talk.
     */
    public static void refresh() {
        TOLD.clear();
    }

    // ---------------------------------------------------------------- the sources

    private static List<Rumor> camps(MinecraftServer server, UUID kingdomId, WorldPos tavern) {
        List<BanditCamp> near = new ArrayList<>();
        for (BanditCamp camp : RoadWork.state(server).camps()) {
            if (!camp.cleared() && camp.dimension().equals(tavern.dimension())
                    && Math.abs(camp.x() - tavern.x()) <= CAMP_RANGE && Math.abs(camp.z() - tavern.z()) <= CAMP_RANGE) {
                near.add(camp);
            }
        }
        near.sort(Comparator.comparingDouble(camp -> Math.pow(camp.x() - tavern.x(), 2) + Math.pow(camp.z() - tavern.z(), 2)));
        List<Rumor> result = new ArrayList<>();
        for (BanditCamp camp : near.subList(0, Math.min(3, near.size()))) {
            result.add(new Rumor(Rumors.CAMP, LifeTexts.KEY + "rumor.camp", List.of(Rumor.Arg.key(Rumors.strength(camp.size())),
                    Rumor.Arg.plain(Rumors.roughDistance(tavern.x(), tavern.z(), camp.x(), camp.z())),
                    Rumor.Arg.key(Rumors.direction(tavern.x(), tavern.z(), camp.x(), camp.z()))), camp.id()));
        }
        return result;
    }

    private static List<Rumor> contracts(MinecraftServer server, UUID kingdomId, WorldPos tavern) {
        List<Contract> open = new ArrayList<>();
        for (Contract contract : GuildWork.state(server).board(kingdomId)) {
            if (contract.status() == Contract.Status.OPEN && contract.reward() > 0) {
                open.add(contract);
            }
        }
        open.sort(Comparator.comparingLong(Contract::reward).reversed());
        List<Rumor> result = new ArrayList<>();
        for (Contract contract : open.subList(0, Math.min(3, open.size()))) {
            result.add(new Rumor(Rumors.CONTRACT, LifeTexts.KEY + "rumor.contract", List.of(
                    Rumor.Arg.key("kingdomworld.guild.contract.kind." + contract.kind().name().toLowerCase(java.util.Locale.ROOT)),
                    Rumor.Arg.money(contract.reward(), contract.currency()))));
        }
        return result;
    }

    private static List<Rumor> news(MinecraftServer server, UUID kingdomId, WorldPos tavern) {
        List<Rumor> result = new ArrayList<>();
        long now = KingdomWorldApi.gameTime(server);
        long today = DaySchedule.day(now);
        var registry = KingdomWorldApi.state(server).citizens().registry();
        LifeState life = LifeWork.state(server);
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            Optional<Capital> capital = RoadWork.capital(server, kingdom.id());
            if (!kingdom.id().equals(kingdomId) && today - DaySchedule.day(kingdom.createdGameTime()) < NEWS_DAYS && capital.isPresent()
                    && capital.get().dimension().equals(tavern.dimension())) {
                result.add(new Rumor(Rumors.NEWS, LifeTexts.KEY + "rumor.founded", List.of(Rumor.Arg.plain(kingdom.name()),
                        Rumor.Arg.key(Rumors.direction(tavern.x(), tavern.z(), capital.get().x(), capital.get().z())))));
            }
            boolean robbed = false;
            boolean battle = false;
            for (DeathRecord death : registry.chronicle(kingdom.id())) {
                if (DaySchedule.day(death.gameTime()) < today - NEWS_DAYS) {
                    break;
                }
                robbed |= death.cause() == CauseOfDeath.ROBBERS;
                battle |= death.cause() == CauseOfDeath.BATTLE;
            }
            if (robbed) {
                result.add(new Rumor(Rumors.NEWS, LifeTexts.KEY + "rumor.robbed", List.of(Rumor.Arg.plain(kingdom.name()))));
            }
            if (battle) {
                result.add(new Rumor(Rumors.NEWS, LifeTexts.KEY + "rumor.battle", List.of(Rumor.Arg.plain(kingdom.name()))));
            }
            if (!kingdom.id().equals(kingdomId)) {
                for (LifeState.Event event : life.events(kingdom.id())) {
                    if (event.day() < today - NEWS_DAYS) {
                        break;
                    }
                    String place = event.place().isEmpty() ? kingdom.name() : event.place();
                    result.add(new Rumor(Rumors.NEWS, LifeTexts.KEY + "rumor." + event.kind(), List.of(Rumor.Arg.plain(place))));
                    break;
                }
            }
        }
        return result;
    }

    private static List<Rumor> prices(MinecraftServer server, UUID kingdomId, WorldPos tavern) {
        record Deal(Kingdom kingdom, String item, double ratio) {
        }
        List<Deal> found = new ArrayList<>();
        var trade = KingdomWorldApi.state(server).trade();
        for (Kingdom kingdom : KingdomWorldApi.kingdoms(server)) {
            Optional<Market> market = trade.existing(kingdom.id());
            if (market.isEmpty()) {
                continue;
            }
            for (Market.SellEntry entry : market.get().sells()) {
                Item item = GoodsStacks.item(entry.item());
                if (item == null || MarketStock.free(server, kingdom.id(), item) - entry.keep() <= 0) {
                    continue;
                }
                double worth = MarketStock.worth(server, item).amount();
                OptionalLong price = StallActions.marketPrice(server, kingdom.id(), item);
                if (worth > 0 && price.isPresent()) {
                    found.add(new Deal(kingdom, entry.item(), price.getAsLong() / worth));
                }
            }
        }
        found.sort(Comparator.comparingDouble(Deal::ratio));
        List<Rumor> result = new ArrayList<>();
        for (Deal deal : found) {
            if (result.size() >= 2) {
                break;
            }
            if (deal.ratio() <= 0.9) {
                result.add(new Rumor(Rumors.PRICE, LifeTexts.KEY + "rumor.cheap", List.of(Rumor.Arg.plain(deal.kingdom().name()),
                        Rumor.Arg.item(deal.item()))));
            }
        }
        for (int i = found.size() - 1; i >= 0 && result.size() < 3; i--) {
            Deal deal = found.get(i);
            if (deal.ratio() >= 1.4) {
                result.add(new Rumor(Rumors.PRICE, LifeTexts.KEY + "rumor.dear", List.of(Rumor.Arg.plain(deal.kingdom().name()),
                        Rumor.Arg.item(deal.item()))));
            }
        }
        return result;
    }

    private static List<Rumor> wanted(MinecraftServer server, UUID kingdomId, WorldPos tavern) {
        List<Rumor> result = new ArrayList<>();
        for (Wanted wanted : LawWork.state(server).wantedIn(kingdomId)) {
            if (result.size() >= 3) {
                break;
            }
            result.add(wanted.bounty() > 0
                    ? new Rumor(Rumors.WANTED, LifeTexts.KEY + "rumor.wanted_bounty", List.of(Rumor.Arg.plain(wanted.name()),
                    Rumor.Arg.money(wanted.bounty(), kingdomId)))
                    : new Rumor(Rumors.WANTED, LifeTexts.KEY + "rumor.wanted", List.of(Rumor.Arg.plain(wanted.name()))));
        }
        return result;
    }
}
