package io.github.sekerator.kingdomworld.common.life;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.construction.BuildingEffects;
import io.github.sekerator.kingdomworld.common.economy.CoinStacks;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawWork;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.LifeViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.common.network.packet.LifePackets;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.trade.GoodsStacks;
import io.github.sekerator.kingdomworld.common.trade.MarketStock;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.common.trade.StallActions;
import io.github.sekerator.kingdomworld.common.trade.TradeSettings;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.life.LifeRules;
import io.github.sekerator.kingdomworld.core.life.LifeState;
import io.github.sekerator.kingdomworld.core.life.Rumor;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The window of a tavern: a player who talks to an innkeeper buys food and drink from the storehouses at the prices of the
 * market (the money goes to the treasury), hears the rumors of the day and may pay to have a camp of robbers marked on the
 * atlas, and rents a room for a day: a bed of the tavern becomes the player's point of return, and the one before comes back
 * when the day is over.
 */
public final class TavernActions {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How close to the innkeeper a player trades. */
    private static final double REACH_SQR = 8.0 * 8.0;
    /** Most items bought at once. */
    public static final int MAX_BUY = 64;

    /**
     * The tavern a player talks to.
     */
    private record Talk(UUID kingdomId, long buildingId, int innkeeper) {
    }

    private static final Map<UUID, Talk> TALKS = new HashMap<>();

    private TavernActions() {
    }

    static void reset() {
        TALKS.clear();
    }

    /**
     * A player talks to a citizen: if it is an innkeeper at the counter of a tavern, the window of the tavern opens.
     *
     * @return whether the citizen was an innkeeper of a tavern
     */
    public static boolean open(ServerPlayer player, CitizenEntity body) {
        MinecraftServer server = player.server;
        if (!InnkeeperBehavior.INNKEEPER.id().equals(body.professionId()) || body.citizenId() == null) {
            return false;
        }
        Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(body.citizenId());
        Optional<WorkPost> post = citizen.flatMap(value -> io.github.sekerator.kingdomworld.common.citizen.work.PostWork.postOf(server, value));
        Optional<BuildingEffects.Effect> tavern = post.flatMap(value -> InnkeeperBehavior.tavernOf(server, value));
        if (tavern.isEmpty()) {
            return false;
        }
        TALKS.put(player.getUUID(), new Talk(tavern.get().building().kingdomId(), tavern.get().building().id(), body.getId()));
        send(player, true);
        return true;
    }

    private static void send(ServerPlayer player, boolean open) {
        Talk talk = TALKS.get(player.getUUID());
        if (talk == null) {
            return;
        }
        Optional<BuildingEffects.Effect> tavern = LifeWork.tavern(player.server, talk.kingdomId(), talk.buildingId());
        if (tavern.isEmpty()) {
            return;
        }
        view(player, tavern.get().building(), talk.innkeeper()).ifPresent(view ->
                KingdomWorldNetwork.sendToPlayer(player, new LifePackets.TavernOpen(view, open)));
    }

    /**
     * What the window of a tavern shows a player.
     */
    public static Optional<LifeViews.TavernView> view(ServerPlayer player, Building tavern, int innkeeperEntity) {
        MinecraftServer server = player.server;
        UUID kingdomId = tavern.kingdomId();
        Optional<TradeViews.Currency> currency = Purses.currency(server, kingdomId);
        if (currency.isEmpty()) {
            return Optional.empty();
        }
        LifeRules rules = LifeSettings.rules();
        List<LifeViews.Good> goods = new ArrayList<>();
        for (Map.Entry<String, Long> entry : KingdomStorage.countsById(server, kingdomId).entrySet()) {
            Item item = GoodsStacks.item(entry.getKey());
            if (item == null || goods.size() >= LifeViews.MAX_ROWS) {
                continue;
            }
            ItemStack sample = new ItemStack(item);
            if (!LifeWork.served(sample)) {
                continue;
            }
            goods.add(new LifeViews.Good(sample, price(server, kingdomId, player.getUUID(), item), entry.getValue()));
        }
        goods.sort(java.util.Comparator.comparing(good -> GoodsStacks.itemId(good.stack())));
        List<LifeViews.Told> rumors = new ArrayList<>();
        var marks = LifeWork.state(server).marks(player.getUUID());
        for (Rumor rumor : LifeRumors.today(server, tavern)) {
            rumors.add(new LifeViews.Told(LifeTexts.rumor(rumor, id -> Purses.units(server, id)), rumor.campId(), marks.contains(rumor.campId())));
        }
        var economy = KingdomWorldApi.state(server).economy();
        int innkeeper = 0;
        boolean female = false;
        if (player.level().getEntity(innkeeperEntity) instanceof CitizenEntity body && body.citizenId() != null) {
            Optional<Citizen> citizen = KingdomWorldApi.state(server).citizens().get(body.citizenId());
            innkeeper = citizen.map(Citizen::nameSeed).orElse(0);
            female = body.isFemale();
        }
        long left = LifeWork.state(server).room(player.getUUID()).filter(room -> beds(server, tavern).contains(room.bed()))
                .map(room -> Math.max(0, room.until() - KingdomWorldApi.gameTime(server))).orElse(0L);
        return Optional.of(new LifeViews.TavernView(kingdomId, currency.get(), innkeeper, female, LifeWork.open(server, tavern), goods, rumors,
                rules.rumorPrice(), freeBeds(server, tavern).size(), rules.roomPrice(), left,
                CoinStacks.count(player.getInventory(), kingdomId).value(Purses.units(server, kingdomId)),
                economy.accounts().balance(player.getUUID(), kingdomId)));
    }

    /**
     * The price of an item in a tavern: what the market asks for it, or the price of the market by its worth when the market
     * does not sell it; a player's reputation moves it like at the market.
     */
    static long price(MinecraftServer server, UUID kingdomId, UUID player, Item item) {
        // The king's price where the market sells the item, the price of the market by worth and stock otherwise; looking
        // does not open a market for the kingdom.
        long manual = KingdomWorldApi.state(server).trade().existing(kingdomId)
                .flatMap(market -> market.sell(GoodsStacks.itemId(new ItemStack(item)))).map(entry -> entry.price()).orElse(0L);
        long price = manual > 0 ? manual
                : Prices.autoSellPrice(MarketStock.worth(server, item).amount(), MarketStock.stock(server, kingdomId, item), TradeSettings.rules());
        return LawWork.priceFor(server, kingdomId, player, Math.max(1, price));
    }

    // ---------------------------------------------------------------- what is done in the window

    public static void handle(ServerPlayer player, LifePackets.TavernAction action) {
        Talk talk = TALKS.get(player.getUUID());
        MinecraftServer server = player.server;
        if (talk == null || !(player.level().getEntity(talk.innkeeper()) instanceof CitizenEntity body) || !body.isAlive()
                || body.distanceToSqr(player) > REACH_SQR) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(LifeTexts.KEY + "tavern.too_far"));
            return;
        }
        Optional<BuildingEffects.Effect> tavern = LifeWork.tavern(server, talk.kingdomId(), talk.buildingId());
        if (tavern.isEmpty()) {
            KingdomActions.reply(player, KingdomActions.Feedback.fail(LifeTexts.KEY + "tavern.closed"));
            return;
        }
        if (action.action() != LifePackets.TavernAction.Action.REFRESH) {
            KingdomActions.Feedback feedback;
            if (LifeWork.isReadOnly(server)) {
                feedback = KingdomActions.Feedback.error(KingdomError.READ_ONLY);
            } else if (!LifeWork.open(server, tavern.get().building())) {
                feedback = KingdomActions.Feedback.fail(LifeTexts.KEY + "tavern.closed");
            } else {
                feedback = switch (action.action()) {
                    case BUY -> buy(player, tavern.get().building(), action.item(), (int) Math.min(MAX_BUY, action.value()), action.account());
                    case MARK -> mark(player, tavern.get().building(), action.value(), action.account());
                    case ROOM -> rent(player, tavern.get().building(), action.account());
                    default -> null;
                };
            }
            if (feedback != null) {
                KingdomActions.reply(player, feedback);
            }
        }
        send(player, false);
    }

    private static Component money(MinecraftServer server, UUID kingdomId, long amount) {
        return MoneyTexts.amount(amount, Purses.units(server, kingdomId));
    }

    /**
     * The player buys food or drink the tavern serves from the storehouses; the money goes to the treasury.
     */
    public static KingdomActions.Feedback buy(ServerPlayer player, Building tavern, String itemId, int count, boolean account) {
        MinecraftServer server = player.server;
        UUID kingdomId = tavern.kingdomId();
        Item item = GoodsStacks.item(itemId);
        if (item == null || !LifeWork.served(new ItemStack(item)) || count < 1) {
            return KingdomActions.Feedback.error(KingdomError.NOT_FOR_SALE);
        }
        long cost = Prices.total(price(server, kingdomId, player.getUUID(), item), count);
        if (cost < 1) {
            return KingdomActions.Feedback.error(KingdomError.NOT_FOR_SALE);
        }
        List<ItemStack> wanted = new ArrayList<>();
        int rest = count;
        while (rest > 0) {
            int part = Math.min(rest, item.getMaxStackSize());
            wanted.add(new ItemStack(item, part));
            rest -= part;
        }
        if (!GoodsStacks.fits(player.getInventory(), wanted)) {
            return KingdomActions.Feedback.error(KingdomError.NO_ROOM);
        }
        WorldPos near = LifeWork.center(tavern);
        List<ItemStack> taken = KingdomStorage.take(server, kingdomId, stack -> stack.is(item) && GoodsStacks.plain(stack), count, near);
        int got = 0;
        for (ItemStack stack : taken) {
            got += stack.getCount();
        }
        if (got < count) {
            for (ItemStack stack : taken) {
                KingdomStorage.insert(server, kingdomId, stack, near);
            }
            return KingdomActions.Feedback.error(KingdomError.OUT_OF_STOCK);
        }
        Optional<KingdomActions.Feedback> refused = StallActions.payKingdom(player, kingdomId, cost, account, LedgerCategory.LIFE);
        if (refused.isPresent()) {
            for (ItemStack stack : taken) {
                KingdomStorage.insert(server, kingdomId, stack, near);
            }
            return refused.get();
        }
        for (ItemStack stack : taken) {
            GoodsStacks.give(player, stack);
        }
        LOGGER.debug("{} bought {} x{} in a tavern of {} for {}", player.getGameProfile().getName(), itemId, count, kingdomId, cost);
        return KingdomActions.Feedback.ok(LifeTexts.KEY + "tavern.bought", new ItemStack(item).getHoverName(), count, money(server, kingdomId, cost));
    }

    /**
     * The player pays to have a camp the innkeeper spoke of marked on the atlas.
     */
    public static KingdomActions.Feedback mark(ServerPlayer player, Building tavern, long campId, boolean account) {
        MinecraftServer server = player.server;
        boolean told = LifeRumors.today(server, tavern).stream().anyMatch(rumor -> rumor.campId() == campId && campId != 0);
        if (!told || RoadWork.state(server).camp(campId).filter(camp -> !camp.cleared()).isEmpty()) {
            return KingdomActions.Feedback.fail(LifeTexts.KEY + "tavern.no_such_camp");
        }
        LifeState life = LifeWork.state(server);
        if (life.marks(player.getUUID()).contains(campId)) {
            return KingdomActions.Feedback.fail(LifeTexts.KEY + "tavern.marked_already");
        }
        long cost = LifeSettings.rules().rumorPrice();
        Optional<KingdomActions.Feedback> refused = StallActions.payKingdom(player, tavern.kingdomId(), cost, account, LedgerCategory.LIFE);
        if (refused.isPresent()) {
            return refused.get();
        }
        life.mark(player.getUUID(), campId);
        return KingdomActions.Feedback.ok(LifeTexts.KEY + "tavern.marked", money(server, tavern.kingdomId(), cost));
    }

    /**
     * Beds of a tavern: the rooms for guests.
     */
    static List<WorldPos> beds(MinecraftServer server, Building tavern) {
        List<WorldPos> beds = new ArrayList<>();
        for (Site site : KingdomWorldApi.state(server).citizens().sites().sitesOf(tavern.kingdomId(), SiteKinds.BED)) {
            WorldPos pos = site.pos();
            if (pos.dimension().equals(tavern.dimension()) && tavern.box().contains(pos.x(), pos.y(), pos.z())) {
                beds.add(pos);
            }
        }
        return beds;
    }

    static List<WorldPos> freeBeds(MinecraftServer server, Building tavern) {
        List<WorldPos> free = new ArrayList<>(beds(server, tavern));
        long now = KingdomWorldApi.gameTime(server);
        for (LifeState.Room room : LifeWork.state(server).rooms().values()) {
            if (room.until() > now) {
                free.remove(room.bed());
            }
        }
        return free;
    }

    /**
     * The player rents a room for a day, or another day of the room they have here.
     */
    public static KingdomActions.Feedback rent(ServerPlayer player, Building tavern, boolean account) {
        MinecraftServer server = player.server;
        LifeState life = LifeWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        Optional<LifeState.Room> own = life.room(player.getUUID());
        WorldPos bed;
        if (own.isPresent() && beds(server, tavern).contains(own.get().bed())) {
            bed = own.get().bed();
        } else {
            List<WorldPos> free = freeBeds(server, tavern);
            if (free.isEmpty()) {
                return KingdomActions.Feedback.fail(LifeTexts.KEY + "tavern.no_room");
            }
            bed = free.get(0);
        }
        long cost = LifeSettings.rules().roomPrice();
        Optional<KingdomActions.Feedback> refused = StallActions.payKingdom(player, tavern.kingdomId(), cost, account, LedgerCategory.LIFE);
        if (refused.isPresent()) {
            return refused.get();
        }
        long until = Math.max(now, own.filter(room -> room.bed().equals(bed)).map(LifeState.Room::until).orElse(now)) + LifeRules.DAY;
        if (own.isPresent() && !own.get().bed().equals(bed)) {
            // The room of another tavern ends now; its point of return before comes back first.
            restore(player, own.get());
            life.leave(player.getUUID());
            own = Optional.empty();
        }
        WorldPos previous = own.map(LifeState.Room::previous).orElseGet(() -> respawn(player));
        float angle = own.map(LifeState.Room::previousAngle).orElse(player.getRespawnAngle());
        boolean forced = own.map(LifeState.Room::previousForced).orElse(player.isRespawnForced());
        life.rent(player.getUUID(), new LifeState.Room(tavern.kingdomId(), bed, until, previous, angle, forced));
        Optional<ResourceKey<Level>> dimension = Dimensions.key(bed.dimension());
        dimension.ifPresent(key -> player.setRespawnPosition(key, new BlockPos(bed.x(), bed.y(), bed.z()), 0.0f, true, false));
        return KingdomActions.Feedback.ok(LifeTexts.KEY + "tavern.rented", money(server, tavern.kingdomId(), cost));
    }

    @Nullable
    private static WorldPos respawn(ServerPlayer player) {
        BlockPos pos = player.getRespawnPosition();
        return pos == null ? null : new WorldPos(player.getRespawnDimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Gives a player the point of return they had before the room, if the room's bed is still their point.
     */
    static void restore(ServerPlayer player, LifeState.Room room) {
        WorldPos current = respawn(player);
        if (current == null || !current.equals(room.bed())) {
            return;
        }
        WorldPos previous = room.previous();
        if (previous == null) {
            player.setRespawnPosition(Level.OVERWORLD, null, 0.0f, false, false);
            return;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(previous.dimension()));
        player.setRespawnPosition(key, new BlockPos(previous.x(), previous.y(), previous.z()), room.previousAngle(), room.previousForced(), false);
    }

    /**
     * Rooms whose day is over end: the player's point of return before comes back (now, or when they come online).
     */
    static void endRooms(MinecraftServer server) {
        LifeState life = LifeWork.state(server);
        long now = KingdomWorldApi.gameTime(server);
        for (Map.Entry<UUID, LifeState.Room> entry : List.copyOf(life.rooms().entrySet())) {
            if (entry.getValue().until() > now) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                restore(player, entry.getValue());
                life.leave(entry.getKey());
                player.sendSystemMessage(Component.translatable(LifeTexts.KEY + "tavern.room_over"));
            }
        }
    }

    /**
     * A player came online: a room that ended while they were away ends now.
     */
    static void loggedIn(ServerPlayer player) {
        if (LifeWork.isReadOnly(player.server)) {
            return;
        }
        LifeState life = LifeWork.state(player.server);
        Optional<LifeState.Room> room = life.room(player.getUUID());
        if (room.isPresent() && room.get().until() <= KingdomWorldApi.gameTime(player.server)) {
            restore(player, room.get());
            life.leave(player.getUUID());
        }
    }

    static void forget(UUID player) {
        TALKS.remove(player);
    }

    static ServerLevel level(MinecraftServer server, WorldPos pos) {
        return Dimensions.key(pos.dimension()).map(server::getLevel).orElse(null);
    }
}
