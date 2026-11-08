package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.army.ArmyWork;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.data.CitizenNbt;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
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
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.road.RoadState;
import io.github.sekerator.kingdomworld.core.road.Wall;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.siege.Hold;
import io.github.sekerator.kingdomworld.core.siege.SiegeRules;
import io.github.sekerator.kingdomworld.core.siege.SiegeState;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.TerritoryIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Carries out what a peace hands over in the world: the claims of land, a city or the throne that 9a records at the end of a
 * war become conquests in the journal, and each second every conquest does its next step. A step that was done already finds
 * nothing left to do, so a restart in the middle neither loses nor doubles land, citizens, buildings or money.
 */
final class SiegeConquests {
    /** Game ticks the players of an annexed kingdom have to choose. */
    static final long OFFER_TICKS = 3L * io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY;
    /** How much a citizen's mood falls when the city changes hands. */
    private static final int MOOD_LOSS = 20;

    private SiegeConquests() {
    }

    static void tick(MinecraftServer server, SiegeRules rules, long now) {
        if (KingdomWorldApi.isReadOnly(server) || RoadWork.isReadOnly(server)) {
            return;
        }
        SiegeState state = SiegeWork.state(server);
        claims(server, state, now);
        forgetEnded(server, state);
        for (Conquest conquest : List.copyOf(state.conquests())) {
            carry(server, state, conquest, now);
        }
        state.expireOffers(now, OFFER_TICKS);
    }

    // ---------------------------------------------------------------- claims become conquests

    private static void claims(MinecraftServer server, SiegeState state, long now) {
        DiplomacyState diplomacy = WarWork.state(server);
        while (!diplomacy.claims().isEmpty()) {
            DiplomacyState.Claim claim = diplomacy.takeClaim(0).orElseThrow();
            List<Hold> holds = state.holdsOf(claim.war());
            String dimension = KingdomWorldApi.state(server).territory().territory(claim.loser()).map(Territory::dimension).orElse("");
            boolean any = false;
            if (claim.chunks() > 0) {
                List<Long> land = Conquest.landToCede(holds, claim.winner(), claim.loser(), claim.war(), claim.chunks());
                if (!land.isEmpty()) {
                    state.addConquest(Conquest.Kind.LAND, claim.war(), claim.winner(), claim.loser(), dimension, land, 0, now);
                    any = true;
                }
            }
            if (!claim.city().isEmpty()) {
                Optional<Long> city = Conquest.cityToCede(holds, claim.winner(), claim.loser(), claim.war(), claim.city(),
                        id -> SiegeTexts.cityName(server, claim.loser(), id));
                if (city.isPresent()) {
                    state.addConquest(Conquest.Kind.CITY, claim.war(), claim.winner(), claim.loser(), dimension, List.of(), city.get(), now);
                    any = true;
                }
            }
            if (claim.throne() && Conquest.throneTaken(holds, claim.winner(), claim.loser(), claim.war())) {
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

    private static void carry(MinecraftServer server, SiegeState state, Conquest conquest, long now) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        if (world.kingdoms().get(conquest.winner()).isEmpty()) {
            // The winner is gone: nothing more is handed over.
            while (state.advance(conquest.id()).map(next -> !next.done()).orElse(false)) {
                continue;
            }
            return;
        }
        Predicate<WorldPos> area = area(server, conquest);
        switch (conquest.step()) {
            case TREASURY -> treasury(server, conquest);
            case LAND -> land(server, state, conquest);
            case BUILDINGS -> buildings(server, conquest, area);
            case CITIZENS -> citizens(server, conquest, area);
            case CITIES -> cities(server, conquest, now);
            case DAMAGE -> state.damage().move(conquest.loser(), conquest.winner(), entry -> area.test(new WorldPos(entry.dimension(),
                    (int) net.minecraft.core.BlockPos.getX(entry.pos()), (int) net.minecraft.core.BlockPos.getY(entry.pos()),
                    (int) net.minecraft.core.BlockPos.getZ(entry.pos()))));
            case MEMBERS -> members(server, state, conquest, now);
            case DISSOLVE -> dissolve(server, state, conquest, now);
            case DONE -> {
            }
        }
        state.advance(conquest.id());
        state.changed();
    }

    /**
     * What a conquest hands over: the chunks of land, the area of a city, or everything of the loser.
     */
    private static Predicate<WorldPos> area(MinecraftServer server, Conquest conquest) {
        return switch (conquest.kind()) {
            case ANNEX -> pos -> true;
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
                yield pos -> pos.dimension().equals(at.dimension()) && Math.abs(pos.x() - at.x()) <= CityWalls.AREA
                        && Math.abs(pos.z() - at.z()) <= CityWalls.AREA;
            }
        };
    }

    private static void treasury(MinecraftServer server, Conquest conquest) {
        io.github.sekerator.kingdomworld.core.economy.Economy.Annexed annexed = KingdomWorldApi.state(server).economy()
                .annex(conquest.winner(), conquest.loser(), WarWork.day(server));
        SiegeWork.LOGGER.info("Annexation #{}: the treasury of {} ({} of its money) went to {}, worth {} there, reserves {}: {}", conquest.id(),
                WarWork.name(server, conquest.loser()).getString(), annexed.funds(), WarWork.name(server, conquest.winner()).getString(),
                annexed.worth(), annexed.reserves(), annexed.outcome().ok() ? "done" : annexed.outcome().error());
    }

    private static void land(MinecraftServer server, SiegeState state, Conquest conquest) {
        TerritoryIndex territory = KingdomWorldApi.state(server).territory();
        Optional<Territory> loser = territory.territory(conquest.loser());
        Optional<Territory> winner = territory.territory(conquest.winner());
        if (loser.isEmpty()) {
            return;
        }
        String dimension = loser.get().dimension();
        boolean sameWorld = winner.isPresent() && winner.get().dimension().equals(dimension);
        long capital = loser.get().capital().chunk();
        List<Long> chunks = new ArrayList<>();
        switch (conquest.kind()) {
            case LAND -> chunks.addAll(conquest.chunks());
            case CITY -> {
                Predicate<WorldPos> area = area(server, conquest);
                for (long chunk : loser.get().chunks()) {
                    if (area.test(new WorldPos(dimension, ChunkKey.x(chunk) * 16 + 8, 64, ChunkKey.z(chunk) * 16 + 8))) {
                        chunks.add(chunk);
                    }
                }
            }
            case ANNEX -> chunks.addAll(loser.get().chunks());
        }
        int moved = 0;
        if (conquest.kind() == Conquest.Kind.ANNEX) {
            List<Long> all = new ArrayList<>(territory.remove(conquest.loser()));
            if (sameWorld) {
                for (long chunk : all) {
                    if (territory.ownerOf(dimension, chunk) == null) {
                        territory.assign(conquest.winner(), chunk);
                        moved++;
                    }
                }
            }
        } else {
            for (long chunk : chunks) {
                if (chunk == capital || !conquest.loser().equals(territory.ownerOf(dimension, chunk))) {
                    continue;
                }
                if (!sameWorld) {
                    continue;
                }
                territory.release(conquest.loser(), chunk);
                territory.assign(conquest.winner(), chunk);
                moved++;
            }
        }
        for (Hold hold : List.copyOf(state.holds())) {
            if (hold.kind() == Hold.Kind.CHUNK && hold.defender().equals(conquest.loser())) {
                state.putHold(new Hold(hold.kind(), hold.war(), hold.attacker(), hold.defender(), hold.dimension(), hold.target(), 0, false, hold.since()));
            }
        }
        state.forgetIdleHolds();
        SiegeWork.LOGGER.info("Conquest #{} ({}): {} chunk(s) of {} went to {}", conquest.id(), conquest.kind().id(), moved,
                WarWork.name(server, conquest.loser()).getString(), WarWork.name(server, conquest.winner()).getString());
    }

    private static void buildings(MinecraftServer server, Conquest conquest, Predicate<WorldPos> area) {
        var book = KingdomWorldApi.state(server).construction();
        int moved = 0;
        for (Building b : List.copyOf(book.buildingsOf(conquest.loser()))) {
            if (!area.test(b.anchor())) {
                continue;
            }
            book.putBuilding(new Building(b.id(), conquest.winner(), b.templateId(), b.type(), b.category(), b.role(), b.level(), b.materials(),
                    b.anchor(), b.placement(), b.box(), b.fingerprint(), b.builtTime(), b.admin(), b.integrity(), b.checked(), b.autoRepair(),
                    b.damaged()));
            moved++;
        }
        SiegeWork.LOGGER.info("Conquest #{}: {} building(s) changed hands", conquest.id(), moved);
    }

    private static void citizens(MinecraftServer server, Conquest conquest, Predicate<WorldPos> area) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        CitizenRegistry registry = world.citizens().registry();
        String winnerName = world.kingdoms().get(conquest.winner()).map(Kingdom::name).orElse("?");
        int moved = 0;
        for (Citizen citizen : List.copyOf(registry.citizensOf(conquest.loser()))) {
            WorldPos at = citizen.home() != null ? citizen.home() : citizen.lastPos();
            if (at == null || !area.test(at)) {
                continue;
            }
            boolean soldier = Professions.isMilitary(citizen.professionId());
            if (soldier && conquest.kind() != Conquest.Kind.ANNEX) {
                // Soldiers stay loyal and leave with their kingdom.
                continue;
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
            Citizen moved1 = CitizenNbt.readCitizen(tag, conquest.winner(), winnerName);
            moved1.setMood(Math.max(0, moved1.mood() - MOOD_LOSS));
            registry.remove(citizen.id());
            registry.add(moved1);
            moved++;
        }
        SiegeWork.LOGGER.info("Conquest #{}: {} citizen(s) became citizens of {}", conquest.id(), moved, winnerName);
    }

    private static void cities(MinecraftServer server, Conquest conquest, long now) {
        RoadState roads = RoadWork.state(server);
        List<City> moving = new ArrayList<>();
        if (conquest.kind() == Conquest.Kind.CITY) {
            roads.city(conquest.city()).filter(c -> c.kingdomId().equals(conquest.loser())).ifPresent(moving::add);
        } else {
            moving.addAll(roads.citiesOf(conquest.loser()));
        }
        for (City city : moving) {
            roads.putCity(new City(city.id(), conquest.winner(), city.name(), city.dimension(), city.x(), city.y(), city.z(), city.founded()));
            CityWalls.handOver(server, city.id(), conquest.loser(), conquest.winner(), city.id());
        }
        if (conquest.kind() == Conquest.Kind.ANNEX) {
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
        SiegeWork.LOGGER.info("Conquest #{}: {} city(ies) went to {}", conquest.id(), moving.size(), WarWork.name(server, conquest.winner()).getString());
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
