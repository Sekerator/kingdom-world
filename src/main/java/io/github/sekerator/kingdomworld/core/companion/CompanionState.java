package io.github.sekerator.kingdomworld.core.companion;

import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.economy.Economy;
import io.github.sekerator.kingdomworld.core.economy.LedgerCategory;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The companions of the players of one world, the markets of the branches of the guild and the parties of players.
 *
 * <p>Money moves only between players and the treasuries of the kingdoms: a slave bought and the wages of free companions go
 * to the treasury of the kingdom of the branch, a slave sold back is paid from that treasury when it has the money. No
 * companion appears without a purchase, a hire or an operator, none is lost but by death, escape, sale or freedom. Every
 * operation checks first and then changes everything it changes, or refuses with a reason and changes nothing.
 *
 * <p>Not thread safe; the server uses it from the server thread only.
 */
public final class CompanionState {
    /**
     * The offers of a branch for one period of days.
     */
    public record Market(long period, List<Offer> offers) {
        public Market {
            offers = List.copyOf(offers);
        }
    }

    /**
     * The result of something that makes a companion.
     */
    public record Made(Outcome outcome, Optional<Companion> companion) {
        static Made failed(Outcome outcome) {
            return new Made(outcome, Optional.empty());
        }

        static Made failed(KingdomError error) {
            return new Made(Outcome.fail(error), Optional.empty());
        }
    }

    /**
     * What came of watching over a companion's loyalty.
     */
    public enum Mood {
        /** Nothing to tell. */
        CALM,
        /** Loyalty fell low: the companion warns that it will leave. */
        WARNING,
        /** Loyalty rose again after a warning. */
        RECOVERED,
        /** The companion leaves: a slave escapes, a free companion quits. */
        LEAVES
    }

    /**
     * What came of a day's wage.
     */
    public enum Wage {
        NONE,
        PAID,
        UNPAID,
        /** Unpaid for too long: the companion leaves. */
        LEAVES
    }

    private final Map<UUID, Companion> companions = new LinkedHashMap<>();
    private final Map<UUID, Market> markets = new LinkedHashMap<>();
    private final Set<UUID> closedMarkets = new LinkedHashSet<>();
    private final PartyState parties = new PartyState();
    private long nextOffer = 1;
    private SlaveryLaw law = SlaveryLaw.ALLOWED;
    private Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
        parties.setChangeListener(listener);
    }

    private void changed() {
        changeListener.run();
    }

    /**
     * Replaces the law about slaves; the stage of laws makes kingdoms able to forbid them.
     */
    public void setLaw(SlaveryLaw value) {
        law = Objects.requireNonNull(value, "law");
    }

    public SlaveryLaw law() {
        return law;
    }

    // ---------------------------------------------------------------- reading

    public PartyState parties() {
        return parties;
    }

    public Optional<Companion> companion(UUID id) {
        return Optional.ofNullable(companions.get(id));
    }

    public Collection<Companion> companions() {
        return Collections.unmodifiableCollection(companions.values());
    }

    public List<Companion> ownedBy(UUID owner) {
        List<Companion> result = new ArrayList<>();
        for (Companion companion : companions.values()) {
            if (companion.owner().equals(owner)) {
                result.add(companion);
            }
        }
        return result;
    }

    public int count(UUID owner) {
        return ownedBy(owner).size();
    }

    public Optional<Market> market(UUID branch) {
        return Optional.ofNullable(markets.get(branch));
    }

    public Map<UUID, Market> markets() {
        return Collections.unmodifiableMap(markets);
    }

    public boolean isMarketClosed(UUID branch) {
        return closedMarkets.contains(branch);
    }

    public Set<UUID> closedMarkets() {
        return Collections.unmodifiableSet(closedMarkets);
    }

    public long nextOfferId() {
        return nextOffer;
    }

    /**
     * The offers of a branch on a day, made anew when a new period began.
     *
     * @param worldSeed a number of the world
     * @param skins     looks a person may have
     */
    public List<Offer> offers(UUID branch, long day, long worldSeed, CompanionRules rules, int skins) {
        long period = MarketOffers.period(day, rules);
        Market market = markets.get(branch);
        if (market == null || market.period() != period) {
            market = new Market(period, MarketOffers.generate(branch, period, worldSeed, rules, skins, () -> nextOffer++));
            markets.put(branch, market);
            changed();
        }
        return market.offers();
    }

    /**
     * Makes the offers of a branch anew now; for operators.
     */
    public List<Offer> refreshOffers(UUID branch, long day, long worldSeed, CompanionRules rules, int skins) {
        markets.remove(branch);
        return offers(branch, day, worldSeed ^ nextOffer * 0x632BE59BD9B4E019L, rules, skins);
    }

    private Optional<Offer> offer(UUID branch, long offerId) {
        Market market = markets.get(branch);
        if (market == null) {
            return Optional.empty();
        }
        return market.offers().stream().filter(offer -> offer.id() == offerId).findFirst();
    }

    private void removeOffer(UUID branch, long offerId) {
        Market market = markets.get(branch);
        if (market != null) {
            List<Offer> rest = new ArrayList<>(market.offers());
            rest.removeIf(offer -> offer.id() == offerId);
            markets.put(branch, new Market(market.period(), rest));
        }
    }

    // ---------------------------------------------------------------- the market

    /**
     * Whether a slave may be bought or a free companion hired at a branch: the buyer is in the guild with room for one more
     * companion by the rank, the offer is there, a slave market is open and slaves are allowed, and the money is there.
     *
     * @param rank  the buyer's rank in the guild, empty for an outsider
     * @param payer the player paying from the account, {@code null} for coins the caller takes
     */
    public Outcome checkTake(UUID player, Optional<Rank> rank, UUID branch, long offerId, UUID payer, CompanionRules rules,
                             KingdomWorldState world) {
        if (rank.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_IN_GUILD);
        }
        if (world.kingdoms().get(branch).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        Optional<Offer> offer = offer(branch, offerId);
        if (offer.isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_OFFER);
        }
        if (offer.get().slave()) {
            if (closedMarkets.contains(branch)) {
                return Outcome.fail(KingdomError.SLAVE_MARKET_CLOSED);
            }
            UUID own = world.membership().kingdomOf(player).orElse(null);
            if (!law.allowsSlavery(branch) || own != null && !law.allowsSlavery(own)) {
                return Outcome.fail(KingdomError.SLAVERY_FORBIDDEN);
            }
        }
        if (count(player) >= rules.limit(rank.get())) {
            return Outcome.fail(KingdomError.TOO_MANY_COMPANIONS);
        }
        return world.economy().checkPayKingdom(branch, branch, offer.get().price(), payer);
    }

    /**
     * Buys a slave or hires a free companion: the price, or the first day's wage, goes to the treasury of the branch's
     * kingdom and the companion follows the buyer.
     *
     * @param payer the player paying from the account, {@code null} for coins the caller took
     */
    public Made take(UUID player, Optional<Rank> rank, UUID branch, long offerId, UUID payer, UUID companionId, long now, long day,
                     CompanionRules rules, KingdomWorldState world) {
        Outcome check = checkTake(player, rank, branch, offerId, payer, rules, world);
        if (!check.ok()) {
            return Made.failed(check);
        }
        Offer offer = offer(branch, offerId).orElseThrow();
        Outcome paid = world.economy().payKingdom(branch, branch, offer.price(), payer, LedgerCategory.COMPANIONS, day);
        if (!paid.ok()) {
            return Made.failed(paid);
        }
        removeOffer(branch, offerId);
        Companion companion = new Companion(companionId, player, offer.slave() ? Companion.Status.SLAVE : Companion.Status.FREE, offer.nameSeed(),
                offer.gender(), offer.skin(), offer.origin(), offer.level(), offer.skills(), branch,
                offer.slave() ? offer.price() : CompanionPower.price(offer.level(), offer.skills(), rules), offer.slave() ? 0 : offer.price(), now,
                day, offer.slave() ? rules.slaveLoyalty : rules.freeLoyalty);
        companions.put(companion.id(), companion);
        changed();
        return new Made(Outcome.DONE, Optional.of(companion));
    }

    /**
     * An operator gives a player a slave of a level, without money; it belongs to the market of {@code branch}.
     */
    public Companion give(UUID player, UUID companionId, int nameSeed, io.github.sekerator.kingdomworld.core.citizen.Gender gender, int skin,
                          int level, Skills skills, UUID branch, long now, long day, CompanionRules rules) {
        Companion companion = new Companion(companionId, player, Companion.Status.SLAVE, nameSeed, gender, skin, Origin.BANDIT_CAPTIVE,
                Math.max(0, Math.min(Companion.MAX_LEVEL, level)), skills, branch, CompanionPower.price(level, skills, rules), 0, now, day,
                rules.slaveLoyalty);
        companions.put(companion.id(), companion);
        changed();
        return companion;
    }

    /**
     * The king of a kingdom (or an operator, {@code actor == null}) opens or closes the market of slaves on its land.
     */
    public Outcome setMarketClosed(UUID actor, UUID kingdomId, boolean closed, KingdomWorldState world) {
        if (world.kingdoms().get(kingdomId).isEmpty()) {
            return Outcome.fail(KingdomError.UNKNOWN_KINGDOM);
        }
        if (actor != null) {
            Optional<Member> member = world.membership().memberOf(actor);
            if (member.isEmpty() || member.get().role() != Role.KING || !world.membership().isMember(actor, kingdomId)) {
                return Outcome.fail(KingdomError.NOT_KING);
            }
        }
        if (closedMarkets.contains(kingdomId) == closed) {
            return Outcome.fail(KingdomError.UNCHANGED);
        }
        if (closed) {
            closedMarkets.add(kingdomId);
        } else {
            closedMarkets.remove(kingdomId);
        }
        changed();
        return Outcome.DONE;
    }

    // ---------------------------------------------------------------- owners

    private Outcome owned(UUID owner, UUID id) {
        Companion companion = companions.get(id);
        if (companion == null) {
            return Outcome.fail(KingdomError.UNKNOWN_COMPANION);
        }
        if (owner != null && !companion.owner().equals(owner)) {
            return Outcome.fail(KingdomError.NOT_YOUR_COMPANION);
        }
        return Outcome.DONE;
    }

    public Outcome setOrder(UUID owner, UUID id, CompanionOrder order, WorldPos pos) {
        Outcome check = owned(owner, id);
        if (!check.ok()) {
            return check;
        }
        if (order.atPlace() && pos == null) {
            return Outcome.fail(KingdomError.BAD_ORDER);
        }
        companions.get(id).setOrder(order, pos);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether a slave may be set free: into the owner's service for pay, or, for an owner who is a subject of a kingdom, as a
     * citizen of that kingdom.
     *
     * @param ownerKingdom the owner's kingdom, empty for none
     */
    public Outcome checkFree(UUID owner, UUID id, boolean intoKingdom, Optional<UUID> ownerKingdom) {
        Outcome check = owned(owner, id);
        if (!check.ok()) {
            return check;
        }
        if (!companions.get(id).isSlave()) {
            return Outcome.fail(KingdomError.NOT_A_SLAVE);
        }
        if (intoKingdom && ownerKingdom.isEmpty()) {
            return Outcome.fail(KingdomError.NOT_A_SUBJECT);
        }
        return Outcome.DONE;
    }

    /**
     * Sets a slave free into the owner's service: a free companion who asks a daily wage, grateful and loyal. The first day
     * is not paid for.
     */
    public Outcome freeIntoService(UUID owner, UUID id, long now, long day, CompanionRules rules) {
        Outcome check = checkFree(owner, id, false, Optional.empty());
        if (!check.ok()) {
            return check;
        }
        Companion companion = companions.get(id);
        companion.setStatus(Companion.Status.FREE);
        companion.setOrigin(Origin.FREEDMAN);
        companion.setWage(CompanionPower.wage(companion.level(), companion.skills(), rules));
        companion.setPaidDay(day);
        companion.setUnpaidDays(0);
        int before = companion.loyalty();
        companion.setLoyalty(Math.max(before, rules.freedLoyalty));
        companion.note(LoyaltyReason.FREED, companion.loyalty() - before, now);
        companion.setWarnedAt(-1);
        changed();
        return Outcome.DONE;
    }

    /**
     * Whether a slave may be sold back to the market of its branch: the treasury of the branch's kingdom pays a share of its
     * worth and must have the money.
     */
    public Outcome checkSell(UUID owner, UUID id, CompanionRules rules, KingdomWorldState world) {
        Outcome check = owned(owner, id);
        if (!check.ok()) {
            return check;
        }
        Companion companion = companions.get(id);
        if (!companion.isSlave()) {
            return Outcome.fail(KingdomError.NOT_A_SLAVE);
        }
        long amount = CompanionPower.buyBack(companion.level(), companion.skills(), rules);
        if (amount <= 0) {
            return Outcome.DONE;
        }
        return world.economy().checkKingdomPays(companion.branch(), amount, companion.owner());
    }

    /**
     * Sells a slave back: the treasury of its branch pays the owner a share of its worth onto the account, and the slave is
     * on that market again until the offers change.
     *
     * @return what the owner got, or -1 if refused
     */
    public long sell(UUID owner, UUID id, long day, CompanionRules rules, KingdomWorldState world) {
        if (!checkSell(owner, id, rules, world).ok()) {
            return -1;
        }
        Companion companion = companions.get(id);
        long amount = CompanionPower.buyBack(companion.level(), companion.skills(), rules);
        Economy economy = world.economy();
        if (amount > 0 && !economy.kingdomPays(companion.branch(), amount, companion.owner(), LedgerCategory.COMPANIONS, day).ok()) {
            return -1;
        }
        companions.remove(id);
        Market market = markets.get(companion.branch());
        if (market != null) {
            List<Offer> offers = new ArrayList<>(market.offers());
            offers.add(new Offer(nextOffer++, companion.branch(), true, companion.nameSeed(), companion.gender(), companion.skin(),
                    companion.origin(), companion.level(), companion.skills(), CompanionPower.price(companion.level(), companion.skills(), rules)));
            markets.put(companion.branch(), new Market(market.period(), offers));
        }
        changed();
        return amount;
    }

    /**
     * A companion is gone: dead, escaped, quit, or freed into a kingdom.
     */
    public Optional<Companion> remove(UUID id) {
        Companion removed = companions.remove(id);
        if (removed != null) {
            changed();
        }
        return Optional.ofNullable(removed);
    }

    // ---------------------------------------------------------------- loyalty

    private void changeLoyalty(Companion companion, LoyaltyReason reason, int change, long now) {
        int before = companion.loyalty();
        companion.setLoyalty(before + change);
        int done = companion.loyalty() - before;
        if (done != 0) {
            companion.note(reason, done, now);
        }
    }

    /**
     * The owner gives a gift worth some money: the companion likes it the more the gift is worth, up to a limit.
     *
     * @return loyalty won, or -1 if refused
     */
    public int gift(UUID owner, UUID id, long worth, long now, CompanionRules rules) {
        if (!owned(owner, id).ok()) {
            return -1;
        }
        Companion companion = companions.get(id);
        int points = (int) Math.max(1, Math.min(rules.maxGift, worth / rules.giftPerPoint));
        int before = companion.loyalty();
        changeLoyalty(companion, LoyaltyReason.GIFT, points, now);
        changed();
        return companion.loyalty() - before;
    }

    /**
     * The owner struck the companion.
     */
    public void beaten(UUID id, long now, CompanionRules rules) {
        Companion companion = companions.get(id);
        if (companion != null) {
            changeLoyalty(companion, LoyaltyReason.BEATEN, -rules.beatenLoss, now);
            changed();
        }
    }

    /**
     * The companion was badly wounded; one wound counts once a minute at most.
     */
    public void wounded(UUID id, long now, CompanionRules rules) {
        Companion companion = companions.get(id);
        if (companion == null) {
            return;
        }
        for (LoyaltyNote note : companion.notes()) {
            if (note.reason() == LoyaltyReason.WOUNDS && now - note.time() < 1_200) {
                return;
            }
        }
        changeLoyalty(companion, LoyaltyReason.WOUNDS, -rules.woundLoss, now);
        changed();
    }

    /**
     * An operator sets the loyalty.
     */
    public void setLoyalty(UUID id, int value, long now) {
        Companion companion = companions.get(id);
        if (companion != null) {
            changeLoyalty(companion, LoyaltyReason.COMMAND, value - companion.loyalty(), now);
            changed();
        }
    }

    /**
     * Watches over loyalty: a companion whose loyalty fell low warns once; if it stays low for the time of the config after
     * the warning, it leaves. Loyalty that rose again calms it.
     */
    public Mood mood(UUID id, long now, CompanionRules rules) {
        Companion companion = companions.get(id);
        if (companion == null) {
            return Mood.CALM;
        }
        if (companion.loyalty() < rules.escapeBelow) {
            if (!companion.warned()) {
                companion.setWarnedAt(now);
                changed();
                return Mood.WARNING;
            }
            return now - companion.warnedAt() >= rules.escapeTicks ? Mood.LEAVES : Mood.CALM;
        }
        if (companion.warned() && companion.loyalty() >= Math.min(CompanionRules.MAX_LOYALTY, rules.escapeBelow + 5)) {
            companion.setWarnedAt(-1);
            changed();
            return Mood.RECOVERED;
        }
        return Mood.CALM;
    }

    // ---------------------------------------------------------------- needs, growth, wages

    /**
     * Time passes for a companion whose owner is online: it gets hungry by its stamina, starves when the food is gone (losing
     * loyalty for every long stretch), wins loyalty when fed and healthy for long, and heals when it has no body (a body
     * heals in the world).
     *
     * @param hasBody whether the companion has a body in the world
     */
    public void live(UUID id, long ticks, long now, boolean hasBody, boolean inInfirmary, CompanionRules rules) {
        Companion companion = companions.get(id);
        if (companion == null || ticks <= 0) {
            return;
        }
        long progress = companion.hungerProgress() + Math.round(ticks * companion.skills().hungerRate());
        int food = companion.food();
        while (progress >= rules.hungerTicks && food > 0) {
            progress -= rules.hungerTicks;
            food--;
        }
        companion.setFood(food);
        companion.setHungerProgress(food == 0 ? 0 : progress);
        if (food == 0) {
            if (companion.starvingSince() < 0) {
                companion.setStarvingSince(now);
            }
            if (now - companion.starvingSince() >= rules.starveTicks) {
                long starve = companion.starveProgress() + ticks;
                while (starve >= rules.starveTicks) {
                    starve -= rules.starveTicks;
                    changeLoyalty(companion, LoyaltyReason.HUNGER, -rules.hungerLoss, now);
                }
                companion.setStarveProgress(starve);
            }
            companion.setKeptProgress(0);
        } else {
            companion.setStarvingSince(-1);
            companion.setStarveProgress(0);
            boolean wellKept = food > rules.eatBelow / 2 && !companion.isWounded();
            if (wellKept) {
                long kept = companion.keptProgress() + ticks;
                int cap = companion.isSlave() ? rules.wellKeptCap : CompanionRules.MAX_LOYALTY;
                while (kept >= rules.wellKeptTicks) {
                    kept -= rules.wellKeptTicks;
                    if (companion.loyalty() < cap) {
                        changeLoyalty(companion, LoyaltyReason.WELL_KEPT, 1, now);
                    }
                }
                companion.setKeptProgress(kept);
            }
        }
        if (!hasBody && companion.health() < companion.maxHealth()) {
            long per = inInfirmary ? rules.infirmaryHealTicks : rules.healTicks;
            double speed = 0.5 + companion.skills().stamina() / 10.0;
            companion.setHealth((float) (companion.health() + ticks * speed / per));
        }
        changed();
    }

    /**
     * An operator sets the food; a companion left without food starts to starve now.
     */
    public void setFood(UUID id, int food, long now) {
        Companion companion = companions.get(id);
        if (companion != null) {
            companion.setFood(food);
            companion.setHungerProgress(0);
            companion.setStarvingSince(food == 0 ? now : -1);
            companion.setStarveProgress(0);
            changed();
        }
    }

    /**
     * The companion ate something with this much food.
     */
    public void feed(UUID id, int nutrition) {
        Companion companion = companions.get(id);
        if (companion != null && nutrition > 0) {
            companion.setFood(companion.food() + nutrition);
            companion.setStarvingSince(-1);
            companion.setStarveProgress(0);
            changed();
        }
    }

    /**
     * The body's health and place, copied back into the record.
     */
    public void sync(UUID id, float health, WorldPos pos) {
        Companion companion = companions.get(id);
        if (companion == null) {
            return;
        }
        boolean moved = !Objects.equals(companion.lastPos().orElse(null), pos);
        if (moved || companion.health() != health) {
            companion.setHealth(health);
            if (pos != null) {
                companion.setLastPos(pos);
            }
            changed();
        }
    }

    /**
     * Experience for a kill or for work; levels come with it, each raising the lowest skill.
     *
     * @return the levels gained
     */
    public int addExperience(UUID id, long amount, CompanionRules rules) {
        Companion companion = companions.get(id);
        if (companion == null || amount <= 0) {
            return 0;
        }
        long experience = companion.experience() + amount;
        int gained = 0;
        while (companion.level() < rules.maxLevel && experience >= rules.experienceFor(companion.level() + 1)) {
            experience -= rules.experienceFor(companion.level() + 1);
            companion.setLevel(companion.level() + 1);
            companion.setSkills(companion.skills().raised());
            companion.setHealth(companion.health() + Companion.HEALTH_PER_LEVEL);
            gained++;
        }
        companion.setExperience(companion.level() >= rules.maxLevel ? Math.min(experience, rules.experienceFor(rules.maxLevel)) : experience);
        changed();
        return gained;
    }

    public void recordKill(UUID id) {
        Companion companion = companions.get(id);
        if (companion != null) {
            companion.setKills(companion.kills() == Integer.MAX_VALUE ? companion.kills() : companion.kills() + 1);
            changed();
        }
    }

    /**
     * A new day for a free companion of an online owner: its wage goes from the owner's account to the treasury of its
     * branch's kingdom. A day unpaid costs loyalty; unpaid longer than the config allows, it leaves.
     */
    public Wage payWage(UUID id, long day, CompanionRules rules, KingdomWorldState world, long now) {
        Companion companion = companions.get(id);
        if (companion == null || companion.isSlave() || companion.paidDay() >= day) {
            return Wage.NONE;
        }
        companion.setPaidDay(day);
        Outcome paid = companion.wage() <= 0 ? Outcome.DONE
                : world.economy().payKingdom(companion.branch(), companion.branch(), companion.wage(), companion.owner(),
                LedgerCategory.COMPANIONS, day);
        if (paid.ok()) {
            companion.setUnpaidDays(0);
            changeLoyalty(companion, LoyaltyReason.PAID, 1, now);
            changed();
            return Wage.PAID;
        }
        companion.setUnpaidDays(companion.unpaidDays() + 1);
        changeLoyalty(companion, LoyaltyReason.UNPAID, -rules.beatenLoss, now);
        changed();
        return companion.unpaidDays() > rules.unpaidDays ? Wage.LEAVES : Wage.UNPAID;
    }

    /**
     * The slaves of owners whose kingdom forbids slavery are set free into their service.
     *
     * @param kingdomOf the kingdom of a player
     * @return the companions set free
     */
    public List<Companion> obeyLaw(Function<UUID, Optional<UUID>> kingdomOf, long now, long day, CompanionRules rules) {
        List<Companion> freed = new ArrayList<>();
        for (Companion companion : List.copyOf(companions.values())) {
            Optional<UUID> kingdom = kingdomOf.apply(companion.owner());
            if (companion.isSlave() && kingdom.isPresent() && !law.allowsSlavery(kingdom.get())
                    && freeIntoService(companion.owner(), companion.id(), now, day, rules).ok()) {
                freed.add(companion);
            }
        }
        return freed;
    }

    /**
     * A kingdom was dissolved: its market is gone; its slaves stay with their owners.
     */
    public void forgetKingdom(UUID kingdomId) {
        boolean any = markets.remove(kingdomId) != null;
        any |= closedMarkets.remove(kingdomId);
        if (any) {
            changed();
        }
    }

    // ---------------------------------------------------------------- saved data

    public void restoreCompanion(Companion companion) {
        if (companions.putIfAbsent(companion.id(), companion) != null) {
            throw new IllegalArgumentException("Two companions " + companion.id());
        }
    }

    public void restoreMarket(UUID branch, Market market) {
        markets.put(branch, market);
        for (Offer offer : market.offers()) {
            nextOffer = Math.max(nextOffer, offer.id() + 1);
        }
    }

    public void restoreClosed(UUID kingdomId) {
        closedMarkets.add(kingdomId);
    }

    public void restoreNextOffer(long id) {
        nextOffer = Math.max(nextOffer, id);
    }

    public void clear() {
        companions.clear();
        markets.clear();
        closedMarkets.clear();
        parties.clear();
        nextOffer = 1;
    }
}
