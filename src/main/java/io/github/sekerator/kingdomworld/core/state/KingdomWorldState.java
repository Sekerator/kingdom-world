package io.github.sekerator.kingdomworld.core.state;

import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Citizens;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkZone;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Construction;
import io.github.sekerator.kingdomworld.core.construction.ConstructionBook;
import io.github.sekerator.kingdomworld.core.construction.ConstructionRules;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.economy.Treasury;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomRegistry;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.MembershipRegistry;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.production.Order;
import io.github.sekerator.kingdomworld.core.production.OrderBook;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ClaimRules;
import io.github.sekerator.kingdomworld.core.territory.KingdomStats;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.github.sekerator.kingdomworld.core.trade.Goods;
import io.github.sekerator.kingdomworld.core.trade.Market;
import io.github.sekerator.kingdomworld.core.trade.Prices;
import io.github.sekerator.kingdomworld.core.trade.TradeBook;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * All kingdom state of one world: the kingdoms, their members, their land, their citizens and their money. Operations that touch more
 * than one part (founding, dissolving, claiming, changes of ruler) go through this class so the parts stay
 * consistent; single-part reads go to the parts directly.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class KingdomWorldState {
    private final KingdomRegistry kingdoms = new KingdomRegistry();
    private final MembershipRegistry membership = new MembershipRegistry();
    private final TerritoryIndex territory = new TerritoryIndex();
    private final Citizens citizens = new Citizens();
    private final OrderBook orders = new OrderBook();
    private final Economy economy = new Economy();
    private final TradeBook trade = new TradeBook();
    private final ConstructionBook construction = new ConstructionBook();

    /**
     * Called after every change of any part, used to mark the data for saving.
     */
    public void setChangeListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        kingdoms.setChangeListener(listener);
        membership.setChangeListener(listener);
        territory.setChangeListener(listener);
        citizens.setChangeListener(listener);
        orders.setChangeListener(listener);
        economy.setChangeListener(listener);
        trade.setChangeListener(listener);
        construction.setChangeListener(listener);
    }

    public KingdomRegistry kingdoms() {
        return kingdoms;
    }

    public MembershipRegistry membership() {
        return membership;
    }

    public TerritoryIndex territory() {
        return territory;
    }

    public Citizens citizens() {
        return citizens;
    }

    /**
     * The orders of the king of every kingdom.
     */
    public OrderBook orders() {
        return orders;
    }

    /**
     * Treasuries, accounts and currencies.
     */
    public Economy economy() {
        return economy;
    }

    /**
     * Markets, deals between kingdoms and caravans.
     */
    public TradeBook trade() {
        return trade;
    }

    /**
     * Constructions and the register of finished buildings.
     */
    public ConstructionBook construction() {
        return construction;
    }

    // ---------------------------------------------------------------- queries

    public Optional<Kingdom> kingdomOf(UUID playerId) {
        return membership.kingdomOf(playerId).flatMap(kingdoms::get);
    }

    public Optional<Kingdom> kingdomAt(String dimension, long chunk) {
        UUID owner = territory.ownerOf(dimension, chunk);
        return owner == null ? Optional.empty() : kingdoms.get(owner);
    }

    /**
     * The kingdom whose throne stands exactly at the given block.
     */
    public Optional<Kingdom> kingdomWithThroneAt(String dimension, int x, int y, int z) {
        UUID owner = territory.ownerOf(dimension, ChunkKey.of(x >> 4, z >> 4));
        if (owner == null) {
            return Optional.empty();
        }
        Optional<Territory> land = territory.territory(owner);
        if (land.isEmpty() || !land.get().capital().isAt(dimension, x, y, z)) {
            return Optional.empty();
        }
        return kingdoms.get(owner);
    }

    public KingdomStats stats(UUID kingdomId) {
        int members = membership.kingdom(kingdomId).map(KingdomMembers::size).orElse(0);
        boolean rulerIsMember = membership.kingdom(kingdomId).flatMap(KingdomMembers::king).isPresent();
        int chunks = territory.territory(kingdomId).map(Territory::size).orElse(0);
        return new KingdomStats(kingdomId, members, members - (rulerIsMember ? 1 : 0), chunks);
    }

    public int chunkLimit(UUID kingdomId, KingdomRules rules) {
        return rules.chunkLimit().maxChunks(stats(kingdomId));
    }

    // ---------------------------------------------------------------- founding and dissolving

    /**
     * Founds a kingdom.
     *
     * @param founder  the player who becomes king, or {@code null} for a kingdom without a ruler
     * @param capital  where the throne stands, or {@code null} for a kingdom without land
     * @param gameTime current game time in ticks
     */
    public CreateKingdomResult found(UUID id, String rawName, int color, UUID founder, Capital capital, long gameTime,
                                     KingdomRules rules) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rules, "rules");
        if (founder != null && membership.kingdomOf(founder).isPresent()) {
            return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.ALREADY_IN_KINGDOM);
        }
        Set<Long> initialChunks = Set.of();
        if (capital != null) {
            String dimension = capital.dimension();
            long chunk = capital.chunk();
            if (!rules.allowedDimensions().contains(dimension)) {
                return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.DIMENSION_NOT_ALLOWED);
            }
            if (territory.ownerOf(dimension, chunk) != null) {
                return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.INSIDE_OTHER_KINGDOM);
            }
            if (territory.hasForeignLandWithin(dimension, chunk, rules.minChunksFromOtherKingdom(), null)) {
                return new CreateKingdomResult.Rejected(CreateKingdomResult.Error.TOO_CLOSE_TO_OTHER_KINGDOM);
            }
            initialChunks = ClaimRules.initialClaim(territory, dimension, chunk, rules.initialClaimRadius(), rules.claimBufferChunks());
        }
        CreateKingdomResult result = kingdoms.create(id, rawName, color, founder, gameTime);
        if (result instanceof CreateKingdomResult.Created) {
            membership.register(id, founder, gameTime);
            if (capital != null) {
                territory.establish(id, capital, initialChunks);
            }
            economy.open(id, Math.floorDiv(gameTime, io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY));
        }
        return result;
    }

    /**
     * Removes a kingdom with its members, pending requests, land, citizens and treasury; its currency becomes old.
     */
    public Optional<Kingdom> dissolve(UUID kingdomId) {
        return dissolve(kingdomId, 0L);
    }

    /**
     * Removes a kingdom; the game time is remembered with its old currency.
     */
    public Optional<Kingdom> dissolve(UUID kingdomId, long gameTime) {
        Optional<Kingdom> kingdom = kingdoms.get(kingdomId);
        if (kingdom.isEmpty()) {
            return Optional.empty();
        }
        long day = Math.floorDiv(gameTime, io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY);
        for (Caravan caravan : new ArrayList<>(trade.caravans())) {
            if (caravan.buyer().equals(kingdomId)) {
                // The buyer is gone: its payment goes back to where it came from, and the goods back to the seller.
                economy.refundEscrow(caravan.escrowId(), day);
                trade.putCaravan(caravan.withState(Caravan.State.RETURNING));
            } else if (caravan.seller().equals(kingdomId)) {
                // The seller is gone: the buyer gets its money back, the goods are lost.
                economy.refundEscrow(caravan.escrowId(), day);
                trade.removeCaravan(caravan.id());
            }
        }
        trade.closeMarket(kingdomId);
        construction.removeKingdom(kingdomId);
        economy.retire(kingdomId, kingdom.get().name(), kingdom.get().color(), gameTime);
        citizens.removeKingdom(kingdomId);
        orders.removeKingdom(kingdomId);
        territory.remove(kingdomId);
        membership.unregister(kingdomId);
        kingdoms.remove(kingdomId);
        return kingdom;
    }

    /**
     * Forgets a kingdom whose saved data could not be restored completely, without making its currency old.
     */
    public void discard(UUID kingdomId) {
        economy.forget(kingdomId);
        trade.forget(kingdomId);
        construction.removeKingdom(kingdomId);
        citizens.removeKingdom(kingdomId);
        orders.removeKingdom(kingdomId);
        territory.remove(kingdomId);
        membership.unregister(kingdomId);
        kingdoms.remove(kingdomId);
    }

    // ---------------------------------------------------------------- territory

    /**
     * Claims a chunk for a kingdom.
     *
     * @param actor the claiming player, who needs {@link Permission#TERRITORY}; {@code null} for an
     *              administrator, who ignores rights and the chunk limit but not the other rules
     */
    public Outcome claim(UUID actor, UUID kingdomId, String dimension, long chunk, KingdomRules rules) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.TERRITORY)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        int limit = actor == null ? Integer.MAX_VALUE : chunkLimit(kingdomId, rules);
        Optional<KingdomError> error = ClaimRules.checkClaim(territory, kingdomId, dimension, chunk, limit, rules.claimBufferChunks());
        if (error.isPresent()) {
            return Outcome.fail(error.get());
        }
        territory.assign(kingdomId, chunk);
        return Outcome.DONE;
    }

    /**
     * Gives up a chunk of a kingdom.
     *
     * @param actor the player, who needs {@link Permission#TERRITORY}; {@code null} for an administrator
     */
    public Outcome unclaim(UUID actor, UUID kingdomId, String dimension, long chunk) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.TERRITORY)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        Optional<KingdomError> error = ClaimRules.checkUnclaim(territory, kingdomId, dimension, chunk);
        if (error.isPresent()) {
            return Outcome.fail(error.get());
        }
        territory.release(kingdomId, chunk);
        // Beds and workplaces on land the kingdom gave up are no longer its own.
        citizens.removeSitesInChunk(kingdomId, dimension, chunk);
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- citizens

    /**
     * Gives a citizen a profession.
     *
     * @param actor the player, who needs {@link Permission#MANAGE_CITIZENS} in the citizen's kingdom; {@code null}
     *              for an administrator
     */
    public Outcome setProfession(UUID actor, UUID citizenId, String professionId) {
        Optional<Citizen> citizen = citizens.get(citizenId);
        if (citizen.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_CITIZEN);
        }
        if (actor != null && !membership.hasPermission(actor, citizen.get().kingdomId(), Permission.MANAGE_CITIZENS)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        if (!Professions.REGISTRY.isKnown(professionId)) {
            return Outcome.fail(KingdomError.UNKNOWN_PROFESSION);
        }
        if (citizen.get().professionId().equals(professionId)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        citizens.setProfession(citizen.get(), professionId);
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- work posts

    /**
     * The post at a position, if the actor may change it.
     */
    private Outcome checkPost(UUID actor, WorldPos pos) {
        Optional<WorkPost> post = citizens.posts().get(pos);
        if (post.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_POST);
        }
        if (actor != null && !membership.hasPermission(actor, post.get().kingdomId(), Permission.MANAGE_CITIZENS)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    /**
     * Sets the profession of a work post; workers of another profession leave it.
     *
     * @param actor the player, who needs {@link Permission#MANAGE_CITIZENS} in the post's kingdom; {@code null} for
     *              an administrator
     */
    public Outcome setPostProfession(UUID actor, WorldPos pos, String professionId) {
        Outcome check = checkPost(actor, pos);
        if (!check.ok()) {
            return check;
        }
        if (!Professions.REGISTRY.isKnown(professionId)) {
            return Outcome.fail(KingdomError.UNKNOWN_PROFESSION);
        }
        if (!professionId.equals(Professions.UNEMPLOYED.id()) && !Professions.worksAtPosts(professionId)) {
            return Outcome.fail(KingdomError.NOT_A_POST_PROFESSION);
        }
        WorkPost post = citizens.posts().get(pos).orElseThrow();
        if (post.professionId().equals(professionId)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        citizens.setPostProfession(post, professionId);
        return Outcome.DONE;
    }

    /**
     * Sets the radius of a post's zone; the zone must lie on the kingdom's land.
     */
    public Outcome setPostRadius(UUID actor, WorldPos pos, int radius, int maxRadius) {
        Outcome check = checkPost(actor, pos);
        if (!check.ok()) {
            return check;
        }
        if (radius < 1 || radius > maxRadius) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        WorkPost post = citizens.posts().get(pos).orElseThrow();
        if (post.radius() == radius) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        // Growing a zone must not reach beyond the land; shrinking is always allowed.
        if (radius > post.radius() && !WorkZone.around(pos, radius, 0, 0)
                .liesWithin(chunk -> post.kingdomId().equals(territory.ownerOf(pos.dimension(), chunk)))) {
            return Outcome.fail(KingdomError.ZONE_OUTSIDE_LAND);
        }
        citizens.setPostRadius(post, radius);
        return Outcome.DONE;
    }

    /**
     * Sets the seats of a post; the workers who came last leave it if there are too many.
     */
    public Outcome setPostSeats(UUID actor, WorldPos pos, int seats, int maxSeats) {
        Outcome check = checkPost(actor, pos);
        if (!check.ok()) {
            return check;
        }
        if (seats < 1 || seats > maxSeats) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        WorkPost post = citizens.posts().get(pos).orElseThrow();
        if (post.seats() == seats) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        citizens.setPostSeats(post, seats);
        return Outcome.DONE;
    }

    /**
     * Chooses the animals of a herder's post; whether the id names an animal is checked by the caller.
     */
    public Outcome setPostAnimal(UUID actor, WorldPos pos, String animal) {
        Outcome check = checkPost(actor, pos);
        if (!check.ok()) {
            return check;
        }
        if (animal == null || animal.length() > WorkPost.MAX_ANIMAL_LENGTH) {
            return Outcome.fail(KingdomError.UNKNOWN_ANIMAL);
        }
        WorkPost post = citizens.posts().get(pos).orElseThrow();
        if (post.animal().equals(animal)) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        citizens.setPostAnimal(post, animal);
        return Outcome.DONE;
    }

    /**
     * Sets the head of animals a herder keeps.
     */
    public Outcome setPostHerdSize(UUID actor, WorldPos pos, int size, int maxHerd) {
        Outcome check = checkPost(actor, pos);
        if (!check.ok()) {
            return check;
        }
        if (size < 2 || size > maxHerd) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        WorkPost post = citizens.posts().get(pos).orElseThrow();
        if (post.herdSize() == size) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        citizens.setPostHerdSize(post, size);
        return Outcome.DONE;
    }

    /**
     * Switches a workshop between working on its own when nothing is ordered and making only what is ordered.
     */
    public Outcome setPostAuto(UUID actor, WorldPos pos, boolean auto) {
        Outcome check = checkPost(actor, pos);
        if (!check.ok()) {
            return check;
        }
        WorkPost post = citizens.posts().get(pos).orElseThrow();
        if (!Professions.hasAutoMode(post.professionId())) {
            return Outcome.fail(KingdomError.NOT_A_POST_PROFESSION);
        }
        if (post.auto() == auto) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        post.setAuto(auto);
        return Outcome.DONE;
    }

    /**
     * The result of hiring a worker for a post.
     *
     * @param citizen the citizen who now works there, if any
     */
    public record Hiring(Outcome outcome, Optional<Citizen> citizen) {
        static Hiring failed(KingdomError error) {
            return new Hiring(Outcome.fail(error), Optional.empty());
        }
    }

    /**
     * Gives the citizen without work nearest to a post the post's profession and a seat there.
     */
    public Hiring hire(UUID actor, WorldPos pos) {
        Outcome check = checkPost(actor, pos);
        if (!check.ok()) {
            return new Hiring(check, Optional.empty());
        }
        WorkPost post = citizens.posts().get(pos).orElseThrow();
        if (!post.hasProfession()) {
            return Hiring.failed(KingdomError.NOT_A_POST_PROFESSION);
        }
        if (post.freeSeats() <= 0) {
            return Hiring.failed(KingdomError.NO_FREE_SEAT);
        }
        Citizen best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Citizen citizen : citizens.registry().citizensOf(post.kingdomId())) {
            if (!citizen.professionId().equals(Professions.UNEMPLOYED.id())) {
                continue;
            }
            double distance = citizen.lastPos().distanceSqr(pos);
            if (best == null || distance < bestDistance) {
                best = citizen;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return Hiring.failed(KingdomError.NO_UNEMPLOYED);
        }
        citizens.setProfession(best, post.professionId());
        citizens.assignWorkplace(best, pos);
        return new Hiring(Outcome.DONE, Optional.of(best));
    }

    /**
     * A worker leaves a post and is without work again.
     */
    public Outcome dismiss(UUID actor, WorldPos pos, UUID citizenId) {
        Outcome check = checkPost(actor, pos);
        if (!check.ok()) {
            return check;
        }
        WorkPost post = citizens.posts().get(pos).orElseThrow();
        Optional<Citizen> citizen = citizens.get(citizenId);
        if (citizen.isEmpty() || !post.workers().contains(citizenId)) {
            return Outcome.fail(KingdomError.NOT_A_WORKER);
        }
        citizens.setProfession(citizen.get(), Professions.UNEMPLOYED.id());
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- orders

    /**
     * The result of giving an order.
     */
    public record Ordering(Outcome outcome, Optional<Order> order) {
        static Ordering failed(KingdomError error) {
            return new Ordering(Outcome.fail(error), Optional.empty());
        }
    }

    /**
     * Gives an order of the king.
     *
     * @param actor     the player, who needs {@link Permission#MANAGE_PRODUCTION}; {@code null} for an administrator
     * @param orderable whether citizens can make an item
     * @param maxOrders orders the kingdom may have at most
     */
    public Ordering order(UUID actor, UUID kingdomId, String item, Order.Kind kind, int amount, Order.Priority priority, long gameTime,
                          int maxOrders, Predicate<String> orderable) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Ordering.failed(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.MANAGE_PRODUCTION)) {
            return Ordering.failed(KingdomError.NO_PERMISSION);
        }
        if (amount < 1 || amount > Order.MAX_AMOUNT) {
            return Ordering.failed(KingdomError.VALUE_OUT_OF_RANGE);
        }
        if (!orderable.test(item)) {
            return Ordering.failed(KingdomError.NOT_ORDERABLE);
        }
        if (orders.count(kingdomId) >= maxOrders) {
            return Ordering.failed(KingdomError.TOO_MANY_ORDERS);
        }
        return new Ordering(Outcome.DONE, Optional.of(orders.add(kingdomId, item, kind, amount, priority, gameTime)));
    }

    /**
     * An order of the king the actor may change; sub-orders follow their parent and are not changed by hand.
     */
    private Outcome checkOrder(UUID actor, UUID kingdomId, long id) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.MANAGE_PRODUCTION)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        Optional<Order> order = orders.get(kingdomId, id);
        if (order.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_ORDER);
        }
        if (order.get().isSubOrder()) {
            return Outcome.fail(KingdomError.SUB_ORDER);
        }
        return Outcome.DONE;
    }

    /**
     * Cancels an order with its sub-orders.
     */
    public Outcome cancelOrder(UUID actor, UUID kingdomId, long id) {
        Outcome check = checkOrder(actor, kingdomId, id);
        if (check.ok()) {
            orders.remove(kingdomId, id);
        }
        return check;
    }

    public Outcome pauseOrder(UUID actor, UUID kingdomId, long id, boolean paused) {
        Outcome check = checkOrder(actor, kingdomId, id);
        if (!check.ok()) {
            return check;
        }
        Order order = orders.get(kingdomId, id).orElseThrow();
        if (order.paused() == paused) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        orders.setPaused(order, paused);
        return Outcome.DONE;
    }

    public Outcome setOrderPriority(UUID actor, UUID kingdomId, long id, Order.Priority priority) {
        Outcome check = checkOrder(actor, kingdomId, id);
        if (!check.ok()) {
            return check;
        }
        Order order = orders.get(kingdomId, id).orElseThrow();
        if (order.priority() == priority) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        orders.setPriority(order, priority);
        for (Order sub : orders.ordersOf(kingdomId)) {
            if (sub.isSubOrder()) {
                orders.setPriority(sub, rootOf(kingdomId, sub).priority());
            }
        }
        return Outcome.DONE;
    }

    private Order rootOf(UUID kingdomId, Order order) {
        Order current = order;
        for (int i = 0; i < OrderBook.MAX_SUB_ORDERS && current.isSubOrder(); i++) {
            Optional<Order> parent = orders.get(kingdomId, current.parent());
            if (parent.isEmpty()) {
                break;
            }
            current = parent.get();
        }
        return current;
    }

    public Outcome setOrderAmount(UUID actor, UUID kingdomId, long id, int amount) {
        Outcome check = checkOrder(actor, kingdomId, id);
        if (!check.ok()) {
            return check;
        }
        if (amount < 1 || amount > Order.MAX_AMOUNT) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        Order order = orders.get(kingdomId, id).orElseThrow();
        if (order.amount() == amount) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        orders.setAmount(order, amount);
        return Outcome.DONE;
    }

    public Outcome setOrderKind(UUID actor, UUID kingdomId, long id, Order.Kind kind) {
        Outcome check = checkOrder(actor, kingdomId, id);
        if (!check.ok()) {
            return check;
        }
        Order order = orders.get(kingdomId, id).orElseThrow();
        if (order.kind() == kind) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        orders.setKind(order, kind);
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- money

    /**
     * Whether the actor may manage the treasury of a kingdom; {@code null} is an administrator.
     */
    private Outcome checkTreasury(UUID actor, UUID kingdomId) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.MANAGE_TREASURY)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    /**
     * The treasury pays a player onto the player's account.
     *
     * @param actor the player, who needs {@link Permission#MANAGE_TREASURY}; {@code null} for an administrator
     */
    public Outcome payFromTreasury(UUID actor, UUID kingdomId, UUID player, long amount, long day) {
        Outcome check = checkTreasury(actor, kingdomId);
        return check.ok() ? economy.treasuryToPlayer(kingdomId, player, amount, day) : check;
    }

    /**
     * Coins of the vault go into the hands of the king or the treasurer. The caller hands out exactly these coins.
     *
     * @param actor the king or the treasurer with {@link Permission#MANAGE_TREASURY}; {@code null} for an administrator
     */
    public Outcome takeVaultCoins(UUID actor, UUID kingdomId, io.github.sekerator.kingdomworld.core.economy.Denomination denomination,
                                  long count, long day) {
        Outcome check = checkTreasury(actor, kingdomId);
        if (!check.ok()) {
            return check;
        }
        if (actor != null) {
            Role role = membership.memberOf(actor).map(Member::role).orElse(null);
            if (role != Role.KING && role != Role.TREASURER) {
                return Outcome.fail(KingdomError.NO_PERMISSION);
            }
        }
        return economy.takeFromVault(kingdomId, denomination, count, day);
    }

    /**
     * Sets the daily tax of the subjects.
     */
    public Outcome setPollTax(UUID actor, UUID kingdomId, long amount, long max) {
        Outcome check = checkTreasury(actor, kingdomId);
        return check.ok() ? economy.setPollTax(kingdomId, amount, max) : check;
    }

    /**
     * Sets the daily wage of a profession.
     */
    public Outcome setWage(UUID actor, UUID kingdomId, String profession, long amount, EconomyRules rules) {
        Outcome check = checkTreasury(actor, kingdomId);
        if (!check.ok()) {
            return check;
        }
        if (!Professions.REGISTRY.isKnown(profession)) {
            return Outcome.fail(KingdomError.UNKNOWN_PROFESSION);
        }
        return economy.setWage(kingdomId, profession, amount, rules.maxWage(), rules);
    }

    /**
     * Renames the currency of a kingdom; only its king may.
     *
     * @param actor the king; {@code null} for an administrator
     */
    public Outcome renameCurrency(UUID actor, UUID kingdomId, String name) {
        Optional<Kingdom> kingdom = kingdoms.get(kingdomId);
        if (kingdom.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !actor.equals(kingdom.get().rulerId())) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return economy.renameCurrency(kingdomId, name);
    }

    /**
     * The daily payday of a kingdom: the tax of its subjects (the king pays none) and the wages of its citizens. The
     * pay of every citizen is remembered for its mood.
     */
    public Economy.Payday payday(UUID kingdomId, long day, EconomyRules rules) {
        Optional<Kingdom> kingdom = kingdoms.get(kingdomId);
        Optional<Treasury> treasury = economy.treasury(kingdomId);
        if (kingdom.isEmpty() || treasury.isEmpty()) {
            return Economy.Payday.NONE;
        }
        List<UUID> taxpayers = new ArrayList<>();
        membership.kingdom(kingdomId).ifPresent(members -> {
            for (Member member : members.members().values()) {
                if (member.role() != Role.KING) {
                    taxpayers.add(member.playerId());
                }
            }
        });
        List<Economy.WageClaim> claims = new ArrayList<>();
        List<Citizen> people = citizens.registry().citizensOf(kingdomId);
        for (Citizen citizen : people) {
            claims.add(new Economy.WageClaim(citizen.id(), citizen.professionId()));
        }
        Economy.Payday payday = economy.payday(kingdomId, day, taxpayers, claims, rules);
        for (Citizen citizen : people) {
            Integer percent = payday.percents().get(citizen.id());
            if (percent != null) {
                citizen.setPayPercent(percent);
            }
        }
        return payday;
    }

    // ---------------------------------------------------------------- the market

    /**
     * Whether the actor may manage the market of a kingdom; {@code null} is an administrator or the kingdom itself.
     */
    private Outcome checkMarket(UUID actor, UUID kingdomId) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.MANAGE_MARKET)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    public Outcome putSell(UUID actor, UUID kingdomId, Market.SellEntry entry, TradeRules rules) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.putSell(kingdomId, entry, rules) : check;
    }

    public Outcome removeSell(UUID actor, UUID kingdomId, String item) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.removeSell(kingdomId, item) : check;
    }

    public Outcome putBuy(UUID actor, UUID kingdomId, Market.BuyEntry entry, TradeRules rules) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.putBuy(kingdomId, entry, rules) : check;
    }

    public Outcome removeBuy(UUID actor, UUID kingdomId, String item) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.removeBuy(kingdomId, item) : check;
    }

    public Outcome putExport(UUID actor, UUID kingdomId, Market.ExportRule rule, TradeRules rules) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.putExport(kingdomId, rule, rules) : check;
    }

    public Outcome removeExport(UUID actor, UUID kingdomId, String item) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.removeExport(kingdomId, item) : check;
    }

    public Outcome setOpenToOutsiders(UUID actor, UUID kingdomId, boolean open, TradeRules rules) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.setOpenToOutsiders(kingdomId, open, rules) : check;
    }

    public Outcome setForeignCurrency(UUID actor, UUID kingdomId, boolean allowed, TradeRules rules) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.setForeignCurrency(kingdomId, allowed, rules) : check;
    }

    public Outcome setExchangeFee(UUID actor, UUID kingdomId, int permille, TradeRules rules) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? trade.setExchangeFee(kingdomId, permille, rules) : check;
    }

    /**
     * Sets the tax on the sales of players' lots, within the config.
     */
    public Outcome setSalesTax(UUID actor, UUID kingdomId, int permille, TradeRules rules) {
        Outcome check = checkMarket(actor, kingdomId);
        return check.ok() ? economy.setSalesTax(kingdomId, permille, rules.maxSalesTaxPercent() * 10) : check;
    }

    /**
     * Orders goods from abroad: the treasury pays now, the goods arrive at {@code arrival}.
     *
     * @param cost what the external market asks for them
     */
    public Outcome orderImport(UUID actor, UUID kingdomId, String item, int count, long cost, long now, long arrival, TradeRules rules) {
        Outcome check = checkMarket(actor, kingdomId);
        if (!check.ok()) {
            return check;
        }
        if (count < 1 || count > Market.MAX_COUNT || cost < 1) {
            return Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE);
        }
        long day = Math.floorDiv(now, io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY);
        Market market = trade.market(kingdomId, rules);
        if ((long) market.stats(day).imported() + count > rules.importMaxPerDay()) {
            return Outcome.fail(KingdomError.IMPORT_LIMIT);
        }
        Outcome paid = economy.importPayment(kingdomId, cost, day);
        if (!paid.ok()) {
            return paid;
        }
        trade.addImport(kingdomId, item, count, cost, now, arrival, rules);
        trade.recordImport(kingdomId, day, count, cost, rules);
        return Outcome.DONE;
    }

    /**
     * Whether a player may put a lot up on a kingdom's market: subjects only, and not more lots than allowed.
     */
    public Outcome checkLot(UUID player, UUID kingdomId, TradeRules rules) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (!membership.isMember(player, kingdomId)) {
            return Outcome.fail(KingdomError.NOT_SUBJECT);
        }
        if (trade.existing(kingdomId).map(market -> market.lotsOf(player)).orElse(0) >= rules.maxLotsPerPlayer()) {
            return Outcome.fail(KingdomError.TOO_MANY_LOTS);
        }
        return Outcome.DONE;
    }

    /**
     * Puts the goods of a player up for sale; the caller took them from the player in the same moment.
     */
    public Outcome listLot(UUID player, UUID kingdomId, Goods goods, long price, long now, TradeRules rules) {
        Outcome check = checkLot(player, kingdomId, rules);
        return check.ok() ? trade.addLot(kingdomId, player, goods, price, now, rules) : check;
    }

    /**
     * The seller takes a lot back; the caller hands the goods over in the same moment.
     */
    public Optional<Market.Lot> withdrawLot(UUID player, UUID kingdomId, long lotId) {
        Optional<Market.Lot> lot = trade.existing(kingdomId).flatMap(market -> market.lot(lotId));
        if (lot.isEmpty() || !lot.get().seller().equals(player)) {
            return Optional.empty();
        }
        return trade.takeLot(kingdomId, lotId);
    }

    /**
     * The result of buying a lot: the lot, or why not.
     */
    public record LotSale(Outcome outcome, Optional<Market.Lot> lot) {
        static LotSale failed(KingdomError error) {
            return new LotSale(Outcome.fail(error), Optional.empty());
        }
    }

    /**
     * What a lot costs a buyer.
     */
    public Optional<Market.Lot> lot(UUID kingdomId, long lotId) {
        return trade.existing(kingdomId).flatMap(market -> market.lot(lotId));
    }

    /**
     * Whether a player could buy a lot paying {@code paid} in a currency.
     */
    public Outcome checkBuyLot(UUID buyer, UUID kingdomId, long lotId, UUID currency, long paid, UUID payer) {
        Optional<Market.Lot> lot = lot(kingdomId, lotId);
        if (lot.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_LOT);
        }
        if (lot.get().seller().equals(buyer)) {
            return Outcome.fail(KingdomError.TARGET_IS_SELF);
        }
        Treasury treasury = economy.treasury(kingdomId).orElse(null);
        if (treasury == null) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        Prices.Sale sale = Prices.split(lot.get().price(), treasury.salesTaxPermille());
        return economy.checkLotSale(kingdomId, currency, paid, payer, lot.get().seller(), lot.get().price(), sale.tax());
    }

    /**
     * A player buys a lot: pays {@code paid} in a currency (from the account, or coins the caller took), the treasury
     * keeps the tax and the seller gets the rest. The caller hands the goods over in the same moment.
     *
     * @param payer the buyer paying from the account, or {@code null} for coins
     */
    public LotSale buyLot(UUID buyer, UUID kingdomId, long lotId, UUID currency, long paid, UUID payer, long day, TradeRules rules) {
        Outcome check = checkBuyLot(buyer, kingdomId, lotId, currency, paid, payer);
        if (!check.ok()) {
            return new LotSale(check, Optional.empty());
        }
        Market.Lot lot = lot(kingdomId, lotId).orElseThrow();
        Prices.Sale sale = Prices.split(lot.price(), economy.treasury(kingdomId).orElseThrow().salesTaxPermille());
        Outcome sold = economy.sellLot(kingdomId, currency, paid, payer, lot.seller(), lot.price(), sale.tax(), day);
        if (!sold.ok()) {
            return new LotSale(sold, Optional.empty());
        }
        trade.takeLot(kingdomId, lotId);
        trade.recordLotTax(kingdomId, day, sale.tax(), rules);
        return new LotSale(Outcome.DONE, Optional.of(lot));
    }

    // ---------------------------------------------------------------- deals between kingdoms

    /**
     * Whether the actor may make deals for a kingdom; {@code null} is an administrator or the kingdom itself (kingdoms
     * without a king use the same calls).
     */
    private Outcome checkTrade(UUID actor, UUID kingdomId) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.MANAGE_TRADE)) {
            return Outcome.fail(KingdomError.NO_PERMISSION);
        }
        return Outcome.DONE;
    }

    /**
     * The result of proposing a deal.
     */
    public record Proposal(Outcome outcome, Optional<Deal> deal) {
    }

    /**
     * A kingdom proposes a deal to another.
     *
     * @param actor        the player proposing, who needs {@link Permission#MANAGE_TRADE}; {@code null} for an administrator
     *                     or a kingdom without a king
     * @param sells        whether the proposing kingdom sends the goods (otherwise it receives them)
     * @param ownCurrency  whether the price is in the currency of the proposing kingdom
     * @param periodDays   days between deliveries, 0 for one delivery
     */
    public Proposal proposeDeal(UUID actor, UUID kingdomId, UUID partner, boolean sells, String item, int count, long price,
                                boolean ownCurrency, int periodDays, long now, TradeRules rules) {
        Outcome check = checkTrade(actor, kingdomId);
        if (!check.ok()) {
            return new Proposal(check, Optional.empty());
        }
        if (kingdoms.get(partner).isEmpty() || economy.treasury(partner).isEmpty() || economy.treasury(kingdomId).isEmpty()) {
            return new Proposal(Outcome.fail(KingdomError.UNKNOWN_KINGDOM), Optional.empty());
        }
        if (partner.equals(kingdomId)) {
            return new Proposal(Outcome.fail(KingdomError.SAME_KINGDOM), Optional.empty());
        }
        if (item == null || item.isEmpty() || item.length() > Goods.MAX_ITEM || count < 1 || count > Deal.MAX_COUNT
                || !io.github.sekerator.kingdomworld.core.economy.Money.isPayment(price) || periodDays < 0 || periodDays > rules.maxPeriodDays()) {
            return new Proposal(Outcome.fail(KingdomError.VALUE_OUT_OF_RANGE), Optional.empty());
        }
        if (trade.openDeals(kingdomId) >= rules.maxDealsPerKingdom() || trade.openDeals(partner) >= rules.maxDealsPerKingdom()) {
            return new Proposal(Outcome.fail(KingdomError.TOO_MANY_DEALS), Optional.empty());
        }
        UUID seller = sells ? kingdomId : partner;
        UUID buyer = sells ? partner : kingdomId;
        UUID currency = ownCurrency ? kingdomId : partner;
        long expires = now + (long) rules.proposalDays() * io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY;
        Deal deal = trade.addDeal(seller, buyer, kingdomId, item, count, price, currency, periodDays, now, expires);
        return new Proposal(Outcome.DONE, Optional.of(deal));
    }

    /**
     * Whether the actor may answer a proposal for the kingdom it was made to.
     */
    public Outcome checkAnswer(UUID actor, UUID kingdomId, long dealId) {
        Outcome check = checkTrade(actor, kingdomId);
        if (!check.ok()) {
            return check;
        }
        Optional<Deal> deal = trade.deal(dealId);
        if (deal.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_DEAL);
        }
        if (!deal.get().answerer().equals(kingdomId)) {
            return Outcome.fail(KingdomError.NOT_PARTNER);
        }
        if (deal.get().status() != Deal.Status.PROPOSED) {
            return Outcome.fail(KingdomError.DEAL_CLOSED);
        }
        return Outcome.DONE;
    }

    /**
     * Declines a proposal.
     */
    public Outcome declineDeal(UUID actor, UUID kingdomId, long dealId) {
        Outcome check = checkAnswer(actor, kingdomId, dealId);
        if (check.ok()) {
            trade.putDeal(trade.deal(dealId).orElseThrow().withStatus(Deal.Status.DECLINED));
        }
        return check;
    }

    /**
     * Calls a deal off: the proposer takes back a proposal, either kingdom ends a regular deal. Caravans on the way go on.
     */
    public Outcome cancelDeal(UUID actor, UUID kingdomId, long dealId) {
        Outcome check = checkTrade(actor, kingdomId);
        if (!check.ok()) {
            return check;
        }
        Optional<Deal> deal = trade.deal(dealId);
        if (deal.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_DEAL);
        }
        Deal found = deal.get();
        if (!found.involves(kingdomId) || (found.status() == Deal.Status.PROPOSED && !found.proposer().equals(kingdomId))) {
            return Outcome.fail(KingdomError.NOT_PARTNER);
        }
        if (found.status().closed()) {
            return Outcome.fail(KingdomError.DEAL_CLOSED);
        }
        trade.putDeal(found.withStatus(Deal.Status.CANCELLED));
        return Outcome.DONE;
    }

    /**
     * Whether the buyer of a deal could pay a delivery now.
     */
    public Outcome checkDelivery(Deal deal) {
        if (kingdoms.get(deal.seller()).isEmpty() || kingdoms.get(deal.buyer()).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        return economy.checkEscrow(deal.buyer(), deal.seller(), deal.currency(), deal.price());
    }

    /**
     * Sends a caravan with the goods of a delivery; its payment goes into escrow. The caller took the goods from the
     * seller's storehouses in the same moment and gives them back if this fails.
     */
    public Optional<Caravan> sendCaravan(Deal deal, List<Goods> cargo, String dimension, int fromX, int fromZ, int toX, int toZ, long now,
                                         long travelTicks) {
        if (!checkDelivery(deal).ok() || cargo.isEmpty()) {
            return Optional.empty();
        }
        long day = Math.floorDiv(now, io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY);
        Optional<Economy.Escrow> escrow = economy.openEscrow(deal.buyer(), deal.seller(), deal.currency(), deal.price(), day);
        if (escrow.isEmpty()) {
            return Optional.empty();
        }
        Caravan caravan = new Caravan(trade.nextId(), deal.id(), deal.seller(), deal.buyer(), cargo, deal.currency(), deal.price(),
                escrow.get().id(), dimension, fromX, fromZ, toX, toZ, now, now + Math.max(1, travelTicks), Caravan.State.TRAVELING);
        trade.putCaravan(caravan);
        return Optional.of(caravan);
    }

    // ---------------------------------------------------------------- construction

    /**
     * The result of a change to the constructions: the construction laid, changed or cancelled, or why not.
     */
    public record ConstructionChange(Outcome outcome, Optional<Construction> construction) {
        static ConstructionChange failed(KingdomError error) {
            return new ConstructionChange(Outcome.fail(error), Optional.empty());
        }
    }

    /**
     * Lays out a construction: the whole box must lie on land of the kingdom, away from every other construction and
     * building, and the kingdom must have room for one more. What stands in the box is checked by the caller.
     *
     * @param actor the player, who needs {@link Permission#MANAGE_CONSTRUCTION}; {@code null} for an administrator
     * @param clear positions in the box that hold blocks in the way
     * @param steps steps of the plan of the construction
     */
    public ConstructionChange layConstruction(UUID actor, UUID kingdomId, String templateId, long fingerprint, WorldPos anchor,
                                              Placement placement, Box box, List<int[]> clear, int steps, long now, ConstructionRules rules) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return ConstructionChange.failed(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.MANAGE_CONSTRUCTION)) {
            return ConstructionChange.failed(KingdomError.NO_PERMISSION);
        }
        for (long chunk : box.chunks()) {
            if (!kingdomId.equals(territory.ownerOf(anchor.dimension(), chunk))) {
                return ConstructionChange.failed(KingdomError.CONSTRUCTION_OUTSIDE_LAND);
            }
        }
        if (construction.overlapping(anchor.dimension(), box, -1).isPresent()) {
            return ConstructionChange.failed(KingdomError.CONSTRUCTION_OVERLAPS);
        }
        if (construction.count(kingdomId) >= rules.maxConstructions()) {
            return ConstructionChange.failed(KingdomError.TOO_MANY_CONSTRUCTIONS);
        }
        return new ConstructionChange(Outcome.DONE, Optional.of(construction.add(kingdomId, templateId, fingerprint, anchor, placement, box,
                now, clear, steps)));
    }

    /**
     * A construction of the kingdom the actor may change.
     */
    private ConstructionChange checkConstruction(UUID actor, UUID kingdomId, long id) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return ConstructionChange.failed(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null && !membership.hasPermission(actor, kingdomId, Permission.MANAGE_CONSTRUCTION)) {
            return ConstructionChange.failed(KingdomError.NO_PERMISSION);
        }
        Optional<Construction> found = construction.get(id);
        if (found.isEmpty() || !found.get().kingdomId().equals(kingdomId)) {
            return ConstructionChange.failed(KingdomError.UNKNOWN_CONSTRUCTION);
        }
        return new ConstructionChange(Outcome.DONE, found);
    }

    /**
     * Cancels a construction. The blocks placed stay; the caller takes the materials of the site back to the storehouses.
     */
    public ConstructionChange cancelConstruction(UUID actor, UUID kingdomId, long id) {
        ConstructionChange check = checkConstruction(actor, kingdomId, id);
        if (check.outcome().ok()) {
            construction.remove(id);
        }
        return check;
    }

    public ConstructionChange pauseConstruction(UUID actor, UUID kingdomId, long id, boolean paused) {
        ConstructionChange check = checkConstruction(actor, kingdomId, id);
        if (!check.outcome().ok()) {
            return check;
        }
        Construction found = check.construction().orElseThrow();
        if (found.paused() == paused) {
            return ConstructionChange.failed(KingdomError.UNCHANGED);
        }
        found.setPaused(paused);
        return check;
    }

    public ConstructionChange setConstructionPriority(UUID actor, UUID kingdomId, long id, Construction.Priority priority) {
        ConstructionChange check = checkConstruction(actor, kingdomId, id);
        if (!check.outcome().ok()) {
            return check;
        }
        Construction found = check.construction().orElseThrow();
        if (found.priority() == priority) {
            return ConstructionChange.failed(KingdomError.UNCHANGED);
        }
        found.setPriority(priority);
        return check;
    }

    /**
     * Switches whether the construction orders the materials it lacks from the workshops.
     */
    public ConstructionChange setOrderMissing(UUID actor, UUID kingdomId, long id, boolean value) {
        ConstructionChange check = checkConstruction(actor, kingdomId, id);
        if (!check.outcome().ok()) {
            return check;
        }
        Construction found = check.construction().orElseThrow();
        if (found.orderMissing() == value) {
            return ConstructionChange.failed(KingdomError.UNCHANGED);
        }
        found.setOrderMissing(value);
        return check;
    }

    // ---------------------------------------------------------------- changes of ruler

    /**
     * The king hands the crown to another member.
     */
    public Outcome abdicate(UUID kingId, UUID heirId) {
        Outcome outcome = membership.abdicate(kingId, heirId);
        if (outcome.ok()) {
            syncRuler(heirId);
        }
        return outcome;
    }

    /**
     * Administrative change of ruler.
     */
    public Outcome crown(UUID kingdomId, UUID playerId, long gameTime) {
        if (kingdoms.get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        Outcome outcome = membership.crown(kingdomId, playerId, gameTime);
        if (outcome.ok()) {
            syncRuler(playerId);
        }
        return outcome;
    }

    private void syncRuler(UUID newKing) {
        membership.kingdomOf(newKing).flatMap(kingdoms::get)
                .ifPresent(kingdom -> kingdoms.update(kingdom.withRuler(newKing)));
    }

    // ---------------------------------------------------------------- persistence

    /**
     * Restores one kingdom from saved data. Either the whole kingdom is restored or nothing changes.
     *
     * @param capital where its throne stands, or {@code null} for a kingdom without land
     * @param chunks  owned chunks in the capital's dimension
     * @throws IllegalArgumentException if the data is inconsistent or conflicts with kingdoms restored before
     */
    public void restore(Kingdom kingdom, Capital capital, Collection<Long> chunks, RolePermissions permissions,
                        List<Member> members, Map<UUID, Long> applications, Map<UUID, Long> invitations) {
        KingdomRegistry.AddResult addable = kingdoms.canAdd(kingdom);
        if (addable != KingdomRegistry.AddResult.ADDED) {
            throw new IllegalArgumentException("Kingdom " + kingdom.name() + " (" + kingdom.id() + "): " + addable);
        }
        UUID king = null;
        for (Member member : members) {
            if (member.role() == Role.KING) {
                if (king != null) {
                    throw new IllegalArgumentException("Kingdom " + kingdom.name() + " has more than one king");
                }
                king = member.playerId();
            }
        }
        if (!Objects.equals(king, kingdom.rulerId())) {
            throw new IllegalArgumentException("Ruler of " + kingdom.name() + " is " + kingdom.rulerId()
                    + " but the member with the king's position is " + king);
        }
        Set<Long> land = new LinkedHashSet<>();
        if (capital != null) {
            land.add(capital.chunk());
            land.addAll(chunks);
            for (long chunk : land) {
                UUID owner = territory.ownerOf(capital.dimension(), chunk);
                if (owner != null) {
                    throw new IllegalArgumentException("Chunk " + ChunkKey.format(chunk) + " of " + kingdom.name()
                            + " is also owned by " + owner);
                }
            }
        } else if (!chunks.isEmpty()) {
            throw new IllegalArgumentException("Kingdom " + kingdom.name() + " owns chunks but has no capital");
        }
        membership.restore(kingdom.id(), permissions, members, applications, invitations);
        if (capital != null) {
            territory.establish(kingdom.id(), capital, land);
        }
        kingdoms.add(kingdom);
    }
}
