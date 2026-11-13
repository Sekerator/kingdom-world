package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSites;
import io.github.sekerator.kingdomworld.common.data.CitizenNbt;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.land.ProvinceSavedData;
import io.github.sekerator.kingdomworld.common.land.ProvinceTrade;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.road.CityWalls;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenRegistry;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.diplomacy.DiplomacyState;
import io.github.sekerator.kingdomworld.core.diplomacy.War;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.npc.NpcState;
import io.github.sekerator.kingdomworld.core.npc.Settlement;
import io.github.sekerator.kingdomworld.core.npc.SettlementGrid;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.ConquestPace;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.ProvinceMap;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Carries out what a peace hands over in the world: the claims of land, a city, the throne or provinces that 9a records at the
 * end of a war become conquests in the journal, and each second every conquest does its next step. A step of many things (the
 * provinces and claims of a state, its buildings, citizens, cities and settlements) does a few of them a second within a share
 * of time common to the journal ({@link ConquestPace}) and goes on the next second. A step finds what is left to do in the
 * world each time, so a restart in the middle neither loses nor doubles land, citizens, buildings, settlements or money.
 */
final class SiegeConquests {
    /** Game ticks the players of an annexed kingdom have to choose. */
    static final long OFFER_TICKS = 3L * io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY;
    /** How much a citizen's mood falls when the city changes hands. */
    private static final int MOOD_LOSS = 20;
    /** When a conquest waiting for room in the treasury of the winner was last told in the log. */
    private static final Map<Long, Long> WAITING = new HashMap<>();
    /** What a step of many seconds handed over so far, by conquest, told in the log once the step is done. */
    private static final Map<Long, Integer> TALLY = new HashMap<>();
    /** When a conquest waiting for data that cannot be written was last told in the log. */
    private static long stalledTold = Long.MIN_VALUE / 4;

    private SiegeConquests() {
    }

    static void reset() {
        WAITING.clear();
        TALLY.clear();
        stalledTold = Long.MIN_VALUE / 4;
    }

    static void tick(MinecraftServer server, SiegeRules rules, long now) {
        if (KingdomWorldApi.isReadOnly(server) || RoadWork.isReadOnly(server)) {
            return;
        }
        SiegeState state = SiegeWork.state(server);
        ConquestPace pace = ConquestPace.start(System::nanoTime);
        claims(server, state, now);
        forgetEnded(server, state);
        for (Conquest conquest : List.copyOf(state.conquests())) {
            if (pace.spent()) {
                // The share of time of this second is used up: the other conquests go on the next second.
                break;
            }
            carry(server, state, conquest, now, pace);
        }
        state.expireOffers(now, OFFER_TICKS);
    }

    // ---------------------------------------------------------------- claims become conquests

    private static void claims(MinecraftServer server, SiegeState state, long now) {
        DiplomacyState diplomacy = WarWork.state(server);
        while (!diplomacy.claims().isEmpty()) {
            DiplomacyState.Claim claim = diplomacy.takeClaim(0).orElseThrow();
            List<Hold> holds = state.holdsOf(claim.war());
            TerritoryIndex territory = KingdomWorldApi.state(server).territory();
            String dimension = territory.territory(claim.loser()).map(Territory::dimension).orElse("");
            boolean any = false;
            // A kingdom of players that bought a province keeps the rules of chunks for its claims and cities as well.
            boolean chunkRules = claim.provinces().isEmpty() || !WarWork.isNpc(server, claim.loser());
            if (!claim.provinces().isEmpty()) {
                // A war on a state: the provinces the winner took go whole, with their claims, settlements and people.
                String where = territory.provinceMap().map(ProvinceMap::dimension).orElse(dimension);
                List<Long> ids = new ArrayList<>(claim.provinces().size());
                for (int province : claim.provinces()) {
                    ids.add((long) province);
                }
                state.addConquest(Conquest.Kind.PROVINCE, claim.war(), claim.winner(), claim.loser(), where, ids, 0, now);
                any = true;
            }
            if (chunkRules && claim.chunks() > 0) {
                List<Long> land = Conquest.landToCede(holds, claim.winner(), claim.loser(), claim.war(), claim.chunks());
                if (!land.isEmpty()) {
                    state.addConquest(Conquest.Kind.LAND, claim.war(), claim.winner(), claim.loser(), dimension, land, 0, now);
                    any = true;
                }
            }
            if (chunkRules && !claim.city().isEmpty()) {
                Optional<Long> city = Conquest.cityToCede(holds, claim.winner(), claim.loser(), claim.war(), claim.city(),
                        id -> SiegeTexts.cityName(server, claim.loser(), id));
                if (city.isPresent()) {
                    state.addConquest(Conquest.Kind.CITY, claim.war(), claim.winner(), claim.loser(), dimension, List.of(), city.get(), now);
                    any = true;
                }
            }
            if (claim.provinces().isEmpty() && claim.throne() && Conquest.throneTaken(holds, claim.winner(), claim.loser(), claim.war())) {
                state.addConquest(Conquest.Kind.ANNEX, claim.war(), claim.winner(), claim.loser(), dimension, List.of(), 0, now);
                any = true;
            }
            if (!any) {
                WarWork.tell(server, claim.winner(), Component.translatable(SiegeWork.KEY + "claim_empty").withStyle(ChatFormatting.GRAY));
            }
            SiegeWork.LOGGER.info("War #{}: the peace hands over {} in the world", claim.war(), any ? "what was taken" : "nothing (nothing was taken)");
            state.forgetWar(claim.war());
        }
    }

    /**
     * Forgets the sieges and holds of wars that are over without a claim (a white peace, a war of another goal).
     */
    private static void forgetEnded(MinecraftServer server, SiegeState state) {
        DiplomacyState diplomacy = WarWork.state(server);
        Set<Long> wars = new HashSet<>();
        state.holds().forEach(hold -> wars.add(hold.war()));
        state.sieges().forEach(siege -> wars.add(siege.war()));
        for (long id : wars) {
            Optional<War> war = diplomacy.war(id);
            if (war.isEmpty() || war.get().over()) {
                state.forgetWar(id);
            }
        }
    }

    // ---------------------------------------------------------------- the steps

    /**
     * Carries out the step a conquest stands at, or as much of it as the second allows; the conquest goes to its next step only
     * once the step is done.
     */
    private static void carry(MinecraftServer server, SiegeState state, Conquest conquest, long now, ConquestPace pace) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        if (world.kingdoms().get(conquest.winner()).isEmpty()) {
            // The winner is gone: nothing more is handed over.
            TALLY.remove(conquest.id());
            while (state.advance(conquest.id()).map(next -> !next.done()).orElse(false)) {
                continue;
            }
            return;
        }
        if (conquest.step() == Conquest.Step.TREASURY && !treasury(server, conquest, now)) {
            // The money of the loser waits for room in the treasury of the winner; the conquest goes no further until then.
            return;
        }
        boolean done = switch (conquest.step()) {
            case TREASURY, DONE -> true;
            case LAND -> land(server, state, conquest, pace);
            case BUILDINGS -> buildings(server, conquest, area(server, conquest), pace);
            case CITIZENS -> citizens(server, conquest, area(server, conquest), pace);
            case CITIES -> cities(server, conquest, area(server, conquest), now);
            case SETTLEMENTS -> settlements(server, conquest);
            case DAMAGE -> {
                Predicate<WorldPos> area = area(server, conquest);
                state.damage().move(conquest.loser(), conquest.winner(), entry -> area.test(new WorldPos(entry.dimension(),
                        (int) net.minecraft.core.BlockPos.getX(entry.pos()), (int) net.minecraft.core.BlockPos.getY(entry.pos()),
                        (int) net.minecraft.core.BlockPos.getZ(entry.pos()))));
                yield true;
            }
            case MEMBERS -> {
                members(server, state, conquest, now);
                yield true;
            }
            case DISSOLVE -> {
                dissolve(server, state, conquest, now);
                yield true;
            }
        };
        if (done) {
            state.advance(conquest.id());
        }
        state.changed();
    }

    /**
     * Counts what a step handed over this second; once the step is done, the whole count of it, else -1.
     */
    private static int tally(Conquest conquest, int moved, boolean done) {
        int total = TALLY.getOrDefault(conquest.id(), 0) + moved;
        if (done) {
            TALLY.remove(conquest.id());
            return total;
        }
        TALLY.put(conquest.id(), total);
        return -1;
    }

    /**
     * Tells the log, at most once a minute, that the journal waits for data that cannot be written now.
     */
    private static void stalled(MinecraftServer server, Conquest conquest, String what) {
        long now = KingdomWorldApi.gameTime(server);
        if (now - stalledTold >= 1_200 || now < stalledTold) {
            stalledTold = now;
            SiegeWork.LOGGER.warn("Conquest #{} ({}) waits: {} cannot be written now", conquest.id(), conquest.kind().id(), what);
        }
    }

    /**
     * The provinces of a conquest of provinces that the winner holds now: what the conquest hands over follows them, a province
     * that went to someone else meanwhile takes nothing of the loser with it.
     */
    private static Set<Integer> heldProvinces(MinecraftServer server, Conquest conquest) {
        Set<Integer> held = new HashSet<>();
        Optional<ProvinceMap> map = KingdomWorldApi.state(server).territory().provinceMap();
        if (map.isEmpty()) {
            return held;
        }
        for (int id : conquest.provinces()) {
            if (map.get().exists(id) && conquest.winner().equals(map.get().owner(id))) {
                held.add(id);
            }
        }
        return held;
    }

    /**
     * What a conquest hands over: the chunks of land, the area of a city, or everything of the loser.
     */
    private static Predicate<WorldPos> area(MinecraftServer server, Conquest conquest) {
        return switch (conquest.kind()) {
            case ANNEX -> pos -> true;
            case PROVINCE -> {
                // The chunks of the provinces of the conquest the winner holds now; another world has none of them.
                TerritoryIndex territory = KingdomWorldApi.state(server).territory();
                Set<Integer> held = heldProvinces(server, conquest);
                yield pos -> !held.isEmpty() && held.contains(territory.provinceAt(pos.dimension(), ChunkKey.of(pos.chunkX(), pos.chunkZ())));
            }
            case LAND -> {
                Set<Long> chunks = new HashSet<>(conquest.chunks());
                yield pos -> pos.dimension().equals(conquest.dimension()) && chunks.contains(ChunkKey.of(pos.chunkX(), pos.chunkZ()));
            }
            case CITY -> {
                Optional<City> city = RoadWork.state(server).city(conquest.city());
                if (city.isEmpty()) {
                    yield pos -> false;
                }
                City at = city.get();
                // The city takes its own area: near it, and nearer to it than to the capital or another city of the loser.
                List<SiegeWork.Seat> others = new ArrayList<>();
                for (SiegeWork.Seat seat : SiegeWork.seats(server, conquest.loser())) {
                    if (seat.city() != at.id() && seat.dimension().equals(at.dimension())) {
                        others.add(seat);
                    }
                }
                yield pos -> pos.dimension().equals(at.dimension()) && Math.abs(pos.x() - at.x()) <= CityWalls.AREA
                        && Math.abs(pos.z() - at.z()) <= CityWalls.AREA && others.stream().noneMatch(other ->
                        distance(other.x(), other.z(), pos) < distance(at.x(), at.z(), pos));
            }
        };
    }

    private static long distance(int x, int z, WorldPos pos) {
        long dx = (long) x - pos.x();
        long dz = (long) z - pos.z();
        return dx * dx + dz * dz;
    }

    /**
     * The treasury of the loser goes to the winner at its worth. When the treasury of the winner cannot take it all, what
     * fits moves and the step waits for room: the loser is not dissolved while money is left in it, so nothing is lost.
     *
     * @return whether the treasury of the loser is empty now
     */
    private static boolean treasury(MinecraftServer server, Conquest conquest, long now) {
        io.github.sekerator.kingdomworld.core.economy.Economy.Annexed annexed = KingdomWorldApi.state(server).economy()
                .annex(conquest.winner(), conquest.loser(), WarWork.day(server));
        if (annexed.funds() > 0 || !annexed.reserves().isEmpty()) {
            SiegeWork.LOGGER.info("Annexation #{}: {} of the money of {} went to {}, worth {} there, reserves {}, {} left", conquest.id(),
                    annexed.funds(), WarWork.name(server, conquest.loser()).getString(), WarWork.name(server, conquest.winner()).getString(),
                    annexed.worth(), annexed.reserves(), annexed.left());
        }
        if (annexed.left() > 0 && now - WAITING.getOrDefault(conquest.id(), Long.MIN_VALUE / 4) >= 1_200) {
            WAITING.put(conquest.id(), now);
            SiegeWork.LOGGER.warn("Annexation #{} waits: the treasury of {} cannot take the {} left of {} ({})", conquest.id(),
                    WarWork.name(server, conquest.winner()).getString(), annexed.left(), WarWork.name(server, conquest.loser()).getString(),
                    annexed.outcome().ok() ? "?" : annexed.outcome().error());
        }
        if (annexed.left() == 0) {
            WAITING.remove(conquest.id());
        }
        return annexed.left() == 0 || !KingdomWorldApi.kingdom(server, conquest.loser()).isPresent();
    }

    /**
     * The land of a conquest: the claims of land or of a city in one go (a few dozen chunks at most); the provinces of a conquest
     * of provinces with the claims of the loser in them; in an annexation every province of the loser, then its claims a few
     * hundred a second, the capital chunk last, with the kingdom.
     *
     * @return whether the land is handed over
     */
    private static boolean land(MinecraftServer server, SiegeState state, Conquest conquest, ConquestPace pace) {
        boolean done = switch (conquest.kind()) {
            case LAND, CITY -> claimsOfPeace(server, conquest);
            case PROVINCE -> provinceLand(server, conquest, pace);
            case ANNEX -> annexLand(server, conquest, pace);
        };
        if (done) {
            // Chunks held in other wars that left the loser with this land are taken from it no more.
            TerritoryIndex territory = KingdomWorldApi.state(server).territory();
            for (Hold hold : List.copyOf(state.holds())) {
                if (hold.kind() == Hold.Kind.CHUNK && hold.defender().equals(conquest.loser())
                        && !conquest.loser().equals(territory.claimOwner(hold.dimension(), hold.target()))) {
                    state.putHold(new Hold(hold.kind(), hold.war(), hold.attacker(), hold.defender(), hold.dimension(), hold.target(), 0, false,
                            hold.since()));
                }
            }
            state.forgetIdleHolds();
        }
        return done;
    }

    /**
     * The claimed chunks a peace for land or for a city hands over, all at once: the chunks the winner held, or the claims of
     * the loser in the area of the city.
     */
    private static boolean claimsOfPeace(MinecraftServer server, Conquest conquest) {
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        Optional<Territory> loser = territory.territory(conquest.loser());
        Optional<Territory> winner = territory.territory(conquest.winner());
        if (loser.isEmpty()) {
            return true;
        }
        String dimension = loser.get().dimension();
        boolean sameWorld = winner.isPresent() && winner.get().dimension().equals(dimension);
        long capital = loser.get().capital().chunk();
        List<Long> chunks = new ArrayList<>();
        if (conquest.kind() == Conquest.Kind.LAND) {
            chunks.addAll(conquest.chunks());
        } else {
            Predicate<WorldPos> area = area(server, conquest);
            for (long chunk : loser.get().chunks()) {
                if (area.test(new WorldPos(dimension, ChunkKey.x(chunk) * 16 + 8, 64, ChunkKey.z(chunk) * 16 + 8))) {
                    chunks.add(chunk);
                }
            }
        }
        int moved = 0;
        for (long chunk : chunks) {
            // Only claims move chunk by chunk; land held as a province changes hands with its province.
            if (chunk == capital || !conquest.loser().equals(territory.claimOwner(dimension, chunk))) {
                continue;
            }
            if (!sameWorld) {
                continue;
            }
            territory.release(conquest.loser(), chunk);
            territory.assign(conquest.winner(), chunk);
            moved++;
        }
        SiegeWork.LOGGER.info("Conquest #{} ({}): {} chunk(s) of {} went to {}", conquest.id(), conquest.kind().id(), moved,
                WarWork.name(server, conquest.loser()).getString(), WarWork.name(server, conquest.winner()).getString());
        return true;
    }

    /**
     * The provinces of a conquest of provinces still held by the loser go to the winner whole, one event a province; then the
     * claims of the loser inside the provinces the winner holds now, a few hundred a second. Nothing moves while the provinces
     * cannot be written.
     */
    private static boolean provinceLand(MinecraftServer server, Conquest conquest, ConquestPace pace) {
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        Optional<ProvinceMap> attached = territory.provinceMap();
        if (attached.isEmpty()) {
            // This world has no provinces (any more): there is nothing to hand over.
            return true;
        }
        ProvinceMap map = attached.get();
        List<Integer> left = new ArrayList<>();
        for (int id : conquest.provinces()) {
            if (map.exists(id) && conquest.loser().equals(map.owner(id))) {
                left.add(id);
            }
        }
        if (!left.isEmpty() && !provinces(server, conquest, territory, map, left, pace)) {
            return false;
        }
        Set<Integer> held = heldProvinces(server, conquest);
        Optional<Territory> loser = territory.territory(conquest.loser());
        int moved = 0;
        boolean done = true;
        if (loser.isPresent() && !held.isEmpty()) {
            String dimension = loser.get().dimension();
            List<Long> claims = ConquestPace.nextClaims(loser.get().chunks(), loser.get().capital().chunk(), ConquestPace.CLAIMS,
                    chunk -> held.contains(territory.provinceAt(dimension, chunk)));
            ConquestPace.Batch batch = pace.batch(ConquestPace.CLAIMS);
            moved = moveClaims(territory, conquest, dimension, claims, batch);
            done = !batch.cut() && claims.size() < ConquestPace.CLAIMS;
        }
        int total = tally(conquest, moved, done);
        if (total >= 0) {
            SiegeWork.LOGGER.info("Conquest #{} ({}): {} chunk(s) of {} went to {}", conquest.id(), conquest.kind().id(), total,
                    WarWork.name(server, conquest.loser()).getString(), WarWork.name(server, conquest.winner()).getString());
        }
        return done;
    }

    /**
     * An annexation takes every province of the loser whole (a state has a couple of dozen: as many events), then its claims a
     * few hundred a second, and only when the capital chunk alone is left the rest of its land with the kingdom.
     */
    private static boolean annexLand(MinecraftServer server, Conquest conquest, ConquestPace pace) {
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        List<Integer> provinces = territory.provincesOf(conquest.loser());
        Optional<ProvinceMap> map = territory.provinceMap();
        if (!provinces.isEmpty() && map.isPresent() && !provinces(server, conquest, territory, map.get(), provinces, pace)) {
            return false;
        }
        Optional<Territory> loser = territory.territory(conquest.loser());
        if (loser.isEmpty()) {
            // It claims no land (any more): its provinces were all there was.
            report(server, conquest, tally(conquest, 0, true));
            return true;
        }
        String dimension = loser.get().dimension();
        Optional<Territory> winner = territory.territory(conquest.winner());
        boolean sameWorld = winner.isPresent() && winner.get().dimension().equals(dimension);
        List<Long> claims = ConquestPace.nextClaims(loser.get().chunks(), loser.get().capital().chunk(), ConquestPace.CLAIMS, chunk -> true);
        ConquestPace.Batch batch = pace.batch(ConquestPace.CLAIMS);
        int moved = moveClaims(territory, conquest, dimension, claims, batch);
        if (batch.cut() || claims.size() >= ConquestPace.CLAIMS) {
            tally(conquest, moved, false);
            return false;
        }
        // The capital chunk alone is left: the land of the kingdom goes with it.
        for (long chunk : territory.remove(conquest.loser())) {
            if (sameWorld && territory.claimOwner(dimension, chunk) == null) {
                territory.assign(conquest.winner(), chunk);
                moved++;
            }
        }
        report(server, conquest, tally(conquest, moved, true));
        return true;
    }

    private static void report(MinecraftServer server, Conquest conquest, int chunks) {
        SiegeWork.LOGGER.info("Conquest #{} ({}): {} chunk(s) of {} went to {}", conquest.id(), conquest.kind().id(), chunks,
                WarWork.name(server, conquest.loser()).getString(), WarWork.name(server, conquest.winner()).getString());
    }

    /**
     * Hands provinces of the loser to the winner within the time of the second, one event each; a charter for one of them is
     * void, for it is the winner's now, and its bearer gets the money back. Nothing moves while the provinces cannot be
     * written.
     *
     * @return whether all of them went
     */
    private static boolean provinces(MinecraftServer server, Conquest conquest, TerritoryIndex territory, ProvinceMap map, List<Integer> ids,
                                     ConquestPace pace) {
        if (ProvinceSavedData.get(server).isReadOnly()) {
            stalled(server, conquest, "the provinces");
            return false;
        }
        long day = Math.floorDiv(KingdomWorldApi.gameTime(server), io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY);
        ConquestPace.Batch batch = pace.batch(ids.size());
        for (int id : ids) {
            if (!batch.room()) {
                break;
            }
            territory.transferProvince(id, conquest.winner(), day);
            // A charter for it is void now: its money goes back to its bearer, as when its days are over.
            ProvinceTrade.voidCharter(server, map, id);
            batch.did();
        }
        if (batch.done() > 0) {
            SiegeWork.LOGGER.info("Conquest #{}: {} province(s) of {} went to {}", conquest.id(), batch.done(),
                    WarWork.name(server, conquest.loser()).getString(), WarWork.name(server, conquest.winner()).getString());
        }
        return batch.done() == ids.size();
    }

    /**
     * Hands claimed chunks of the loser over, one at a time within the batch: in the world of the winner a chunk becomes its
     * claim; elsewhere it is only given up (inside a province of the winner it is the winner's land then all the same).
     *
     * @return how many changed hands
     */
    private static int moveClaims(TerritoryIndex territory, Conquest conquest, String dimension, List<Long> chunks, ConquestPace.Batch batch) {
        Optional<Territory> winner = territory.territory(conquest.winner());
        boolean sameWorld = winner.isPresent() && winner.get().dimension().equals(dimension);
        for (long chunk : chunks) {
            if (!conquest.loser().equals(territory.claimOwner(dimension, chunk))) {
                continue;
            }
            if (!batch.room()) {
                break;
            }
            territory.release(conquest.loser(), chunk);
            if (sameWorld && territory.claimOwner(dimension, chunk) == null) {
                territory.assign(conquest.winner(), chunk);
            }
            batch.did();
        }
        return batch.done();
    }

    /**
     * The buildings of the loser in the area, a few dozen a second.
     *
     * @return whether none of them is left
     */
    private static boolean buildings(MinecraftServer server, Conquest conquest, Predicate<WorldPos> area, ConquestPace pace) {
        var book = KingdomWorldApi.state(server).construction();
        ConquestPace.Batch batch = pace.batch(ConquestPace.BUILDINGS);
        for (Building b : book.buildingsOf(conquest.loser())) {
            if (!area.test(b.anchor())) {
                continue;
            }
            if (!batch.room()) {
                break;
            }
            book.putBuilding(new Building(b.id(), conquest.winner(), b.templateId(), b.type(), b.category(), b.role(), b.level(), b.materials(),
                    b.anchor(), b.placement(), b.box(), b.fingerprint(), b.builtTime(), b.admin(), b.integrity(), b.checked(), b.autoRepair(),
                    b.damaged()));
            batch.did();
        }
        int total = tally(conquest, batch.done(), !batch.cut());
        if (total >= 0) {
            SiegeWork.LOGGER.info("Conquest #{}: {} building(s) changed hands", conquest.id(), total);
        }
        return !batch.cut();
    }

    /**
     * The citizens of the loser living in the area, a few a second; soldiers stay with their kingdom unless it is annexed.
     *
     * @return whether none of them is left
     */
    private static boolean citizens(MinecraftServer server, Conquest conquest, Predicate<WorldPos> area, ConquestPace pace) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        CitizenRegistry registry = world.citizens().registry();
        String winnerName = world.kingdoms().get(conquest.winner()).map(Kingdom::name).orElse("?");
        ConquestPace.Batch batch = pace.batch(ConquestPace.CITIZENS);
        for (Citizen citizen : registry.citizensOf(conquest.loser())) {
            WorldPos at = citizen.home() != null ? citizen.home() : citizen.lastPos();
            if (at == null || !area.test(at)) {
                continue;
            }
            boolean soldier = Professions.isMilitary(citizen.professionId());
            if (soldier && conquest.kind() != Conquest.Kind.ANNEX) {
                // Soldiers stay loyal and leave with their kingdom.
                continue;
            }
            if (!batch.room()) {
                break;
            }
            if (soldier) {
                ArmyWork.dismiss(server, null, citizen.id());
            }
            CitizenEntity body = CitizenBodies.body(citizen.id());
            if (body != null) {
                body.discard();
            }
            Optional<Citizen> now = registry.get(citizen.id());
            if (now.isEmpty() || !now.get().kingdomId().equals(conquest.loser())) {
                continue;
            }
            CompoundTag tag = CitizenNbt.writeCitizen(now.get());
            Citizen moved = CitizenNbt.readCitizen(tag, conquest.winner(), winnerName);
            moved.setMood(Math.max(0, moved.mood() - MOOD_LOSS));
            registry.remove(citizen.id());
            registry.add(moved);
            batch.did();
        }
        int total = tally(conquest, batch.done(), !batch.cut());
        if (total >= 0) {
            SiegeWork.LOGGER.info("Conquest #{}: {} citizen(s) became citizens of {}", conquest.id(), total, winnerName);
        }
        return !batch.cut();
    }

    /**
     * The cities of the conquest, one a second: the city of a peace for a city, the cities of the loser in the provinces, or all
     * of them in an annexation, whose capital then becomes a city of the winner.
     *
     * @return whether none of them is left
     */
    private static boolean cities(MinecraftServer server, Conquest conquest, Predicate<WorldPos> area, long now) {
        RoadState roads = RoadWork.state(server);
        List<City> moving = new ArrayList<>();
        switch (conquest.kind()) {
            case CITY -> roads.city(conquest.city()).filter(c -> c.kingdomId().equals(conquest.loser())).ifPresent(moving::add);
            case PROVINCE -> {
                for (City city : roads.citiesOf(conquest.loser())) {
                    if (area.test(new WorldPos(city.dimension(), city.x(), city.y(), city.z()))) {
                        moving.add(city);
                    }
                }
            }
            case LAND, ANNEX -> moving.addAll(roads.citiesOf(conquest.loser()));
        }
        int moved = 0;
        if (!moving.isEmpty()) {
            handOver(server, roads, moving.get(0), conquest.loser(), conquest.winner());
            moved++;
        }
        boolean done = moving.size() <= 1;
        if (done && conquest.kind() == Conquest.Kind.ANNEX) {
            Optional<Wall> capitalWall = roads.wallOf(0, conquest.loser());
            Optional<io.github.sekerator.kingdomworld.core.territory.Capital> capital = RoadWork.capital(server, conquest.loser());
            if (capital.isPresent()) {
                String name = KingdomWorldApi.kingdom(server, conquest.loser()).map(Kingdom::name).orElse("?");
                if (roads.cityAt(capital.get().dimension(), capital.get().x(), capital.get().y(), capital.get().z()).isEmpty()) {
                    City old = roads.addCity(conquest.winner(), name.length() > City.MAX_NAME ? name.substring(0, City.MAX_NAME) : name,
                            capital.get().dimension(), capital.get().x(), capital.get().y(), capital.get().z(), now);
                    if (capitalWall.isPresent()) {
                        CityWalls.handOver(server, 0, conquest.loser(), conquest.winner(), old.id());
                    }
                }
            }
        }
        int total = tally(conquest, moved, done);
        if (total >= 0) {
            SiegeWork.LOGGER.info("Conquest #{}: {} city(ies) went to {}", conquest.id(), total, WarWork.name(server, conquest.winner()).getString());
        }
        return done;
    }

    private static void handOver(MinecraftServer server, RoadState roads, City city, UUID from, UUID to) {
        roads.putCity(new City(city.id(), to, city.name(), city.dimension(), city.x(), city.y(), city.z(), city.founded()));
        CityWalls.handOver(server, city.id(), from, to, city.id());
    }

    /**
     * One settlement a second goes to the winner: of the provinces of the conquest the winner holds now, or any of the loser in an
     * annexation, before the loser is dissolved (a dissolved kingdom of the world loses its settlements). Nothing moves while the
     * kingdoms of the world cannot be written.
     *
     * @return whether none of them is left
     */
    private static boolean settlements(MinecraftServer server, Conquest conquest) {
        NpcState npc = NpcWork.state(server);
        boolean all = conquest.kind() == Conquest.Kind.ANNEX;
        Set<Integer> held = all ? Set.of() : heldProvinces(server, conquest);
        Settlement next = null;
        boolean more = false;
        for (Settlement settlement : npc.settlements()) {
            if (!settlement.kingdomId().equals(conquest.loser()) || (!all && !held.contains(settlement.province()))) {
                continue;
            }
            if (next != null) {
                more = true;
                break;
            }
            next = settlement;
        }
        int moved = 0;
        if (next != null) {
            if (NpcWork.isReadOnly(server)) {
                stalled(server, conquest, "the kingdoms of the world");
                return false;
            }
            handOver(server, conquest, npc, next);
            moved++;
        }
        int total = tally(conquest, moved, !more);
        if (total >= 0) {
            SiegeWork.LOGGER.info("Conquest #{}: {} settlement(s) of {} went to {}", conquest.id(), total,
                    WarWork.name(server, conquest.loser()).getString(), WarWork.name(server, conquest.winner()).getString());
        }
        return !more;
    }

    /**
     * A settlement goes to the winner with its city and wall when it has one standing; the places citizens use in the loaded
     * chunks of its square where such places are known are looked at again, so that they become the winner's.
     */
    private static void handOver(MinecraftServer server, Conquest conquest, NpcState npc, Settlement settlement) {
        npc.moveSettlement(settlement.id(), conquest.winner());
        if (settlement.cityId() != 0) {
            RoadState roads = RoadWork.state(server);
            Optional<City> city = roads.city(settlement.cityId());
            if (city.isPresent() && city.get().kingdomId().equals(conquest.loser())) {
                handOver(server, roads, city.get(), conquest.loser(), conquest.winner());
            }
        }
        KingdomWorldState world = KingdomWorldApi.state(server);
        String dimension = world.territory().provinceMap().map(ProvinceMap::dimension).orElse(conquest.dimension());
        ServerLevel level = SiegeWork.level(server, dimension);
        if (level == null) {
            return;
        }
        var sites = world.citizens().sites();
        int reach = SettlementGrid.reach(settlement.tier());
        int centerX = settlement.x() >> 4;
        int centerZ = settlement.z() >> 4;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                long chunk = ChunkKey.of(centerX + dx, centerZ + dz);
                if (level.getChunkSource().getChunkNow(centerX + dx, centerZ + dz) != null && !sites.sitesInChunk(dimension, chunk).isEmpty()) {
                    CitizenSites.scheduleChunk(dimension, chunk);
                }
            }
        }
    }

    private static void members(MinecraftServer server, SiegeState state, Conquest conquest, long now) {
        Optional<KingdomMembers> members = KingdomWorldApi.state(server).membership().kingdom(conquest.loser());
        String winnerName = KingdomWorldApi.kingdom(server, conquest.winner()).map(Kingdom::name).orElse("?");
        if (members.isEmpty()) {
            return;
        }
        for (Member member : members.get().members().values()) {
            state.offer(member.playerId(), conquest.winner(), winnerName, now);
        }
    }

    private static void dissolve(MinecraftServer server, SiegeState state, Conquest conquest, long now) {
        Optional<Kingdom> loser = KingdomWorldApi.kingdom(server, conquest.loser());
        if (loser.isEmpty()) {
            return;
        }
        DiplomacyState diplomacy = WarWork.state(server);
        Component loserName = WarWork.name(server, conquest.loser());
        Component winnerName = WarWork.name(server, conquest.winner());
        diplomacy.record(now, "annexed", conquest.winner(), conquest.loser(), conquest.war(), "");
        UUID king = loser.get().rulerId();
        if (king != null && !WarWork.isNpc(server, conquest.loser())) {
            String kingName = io.github.sekerator.kingdomworld.common.kingdom.PlayerNames.name(server, king);
            diplomacy.record(now, "crown_lost", conquest.winner(), conquest.loser(), conquest.war(), kingName);
        }
        KingdomWorldApi.dissolveKingdom(server, conquest.loser());
        state.forget(conquest.loser());
        server.getPlayerList().broadcastSystemMessage(Component.translatable(SiegeWork.KEY + "annexed", loserName, winnerName)
                .withStyle(ChatFormatting.GOLD), false);
        for (SiegeState.Offer offer : List.copyOf(state.offers())) {
            if (offer.winner().equals(conquest.winner())) {
                KingdomWorldApi.state(server).membership().apply(offer.player(), conquest.winner(), now);
                ServerPlayer player = server.getPlayerList().getPlayer(offer.player());
                if (player != null) {
                    tellOffer(player, offer);
                }
            }
        }
        SiegeWork.LOGGER.info("Annexation #{}: {} is no more, {} took it all", conquest.id(), loserName.getString(), winnerName.getString());
    }

    /**
     * The choice of a player of an annexed kingdom: swear to the winner or stay free.
     */
    static void tellOffer(ServerPlayer player, SiegeState.Offer offer) {
        Component swear = Component.translatable(SiegeWork.KEY + "offer.swear").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kingdomworld siege swear")));
        Component free = Component.translatable(SiegeWork.KEY + "offer.free").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kingdomworld siege free")));
        player.sendSystemMessage(Component.translatable(SiegeWork.KEY + "offer.text", Component.literal(offer.winnerName()), swear, free)
                .withStyle(ChatFormatting.GOLD));
    }

    /**
     * A player of an annexed kingdom answers: becomes a subject of the winner, or stays free.
     */
    static Component answer(MinecraftServer server, ServerPlayer player, boolean swear) {
        SiegeState state = SiegeWork.state(server);
        Optional<SiegeState.Offer> offer = state.takeOffer(player.getUUID());
        if (offer.isEmpty()) {
            return Component.translatable(SiegeWork.KEY + "offer.none").withStyle(ChatFormatting.GRAY);
        }
        if (!swear) {
            return Component.translatable(SiegeWork.KEY + "offer.stayed_free").withStyle(ChatFormatting.GRAY);
        }
        var membership = KingdomWorldApi.state(server).membership();
        membership.apply(player.getUUID(), offer.get().winner(), KingdomWorldApi.gameTime(server));
        var outcome = membership.admit(offer.get().winner(), player.getUUID(), KingdomWorldApi.gameTime(server));
        return outcome.ok() ? Component.translatable(SiegeWork.KEY + "offer.sworn", Component.literal(offer.get().winnerName()))
                .withStyle(ChatFormatting.GREEN)
                : io.github.sekerator.kingdomworld.common.kingdom.KingdomActions.errorMessage(outcome.error()).copy().withStyle(ChatFormatting.RED);
    }
}
