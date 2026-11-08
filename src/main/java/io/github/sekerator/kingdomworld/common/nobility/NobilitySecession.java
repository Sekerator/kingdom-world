package io.github.sekerator.kingdomworld.common.nobility;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.npc.NpcWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.road.RoadWork;
import io.github.sekerator.kingdomworld.common.siege.SiegeWork;
import io.github.sekerator.kingdomworld.core.diplomacy.Relations;
import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomColors;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.nobility.NobleHouse;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.NpcNames;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.SpawnStage;
import io.github.sekerator.kingdomworld.core.road.City;
import io.github.sekerator.kingdomworld.core.siege.Conquest;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A city leaves a kingdom of players after a plot of the house that holds it: to the neighbour who backed the plot, or as a
 * small kingdom of the world of its own with the head of the house on its throne. The land, buildings and citizens of the
 * city go through the journal of conquests of the war in the world, step by step, so a restart in the middle neither loses
 * nor doubles anything. The crown is never lost this way; the city can be taken back by war or won back by diplomacy.
 */
public final class NobilitySecession {
    private NobilitySecession() {
    }

    /**
     * @return the kingdom the city went to, empty if it could not leave
     */
    public static Optional<UUID> secede(MinecraftServer server, UUID from, NobleHouse house, long cityId, @Nullable UUID sponsor, long day) {
        Optional<City> found = RoadWork.state(server).city(cityId);
        if (found.isEmpty() || !found.get().kingdomId().equals(from)) {
            return Optional.empty();
        }
        City city = found.get();
        UUID to = null;
        if (sponsor != null && KingdomWorldApi.kingdom(server, sponsor).isPresent()) {
            boolean sameWorld = KingdomWorldApi.state(server).territory().territory(sponsor).map(t -> t.dimension().equals(city.dimension())).orElse(false);
            if (sameWorld) {
                to = sponsor;
            }
        }
        if (to == null) {
            to = independent(server, from, house, city, day).orElse(null);
        }
        if (to == null) {
            NobilityWork.LOGGER.warn("The city {} could not leave {}: no kingdom could be made for it", city.name(), WarWork.name(server, from).getString());
            return Optional.empty();
        }
        long now = KingdomWorldApi.gameTime(server);
        SiegeWork.state(server).addConquest(Conquest.Kind.CITY, 0, to, from, city.dimension(), List.of(), city.id(), now);
        NobilityWork.state(server).seceded(from, to, house.id(), city.id(), day);
        if (to.equals(sponsor)) {
            WarWork.state(server).relate(from, sponsor, Relations.Reason.PLOT_BACKED);
        }
        Component cityName = NobilityTexts.cityName(city.name());
        NobilityWork.tell(server, from, null, Component.translatable(NobilityTexts.KEY + "msg.seceded", cityName, NobilityTexts.house(house),
                WarWork.name(server, to)).withStyle(ChatFormatting.RED));
        NobilityWork.LOGGER.info("The city {} left {} with the house {} and went to {}", city.name(), WarWork.name(server, from).getString(),
                house.family(io.github.sekerator.kingdomworld.core.citizen.CitizenNames.Alphabet.LATIN), WarWork.name(server, to).getString());
        return Optional.of(to);
    }

    /**
     * A small kingdom of the world at the town hall of the city, ruled by the head of the house; it has the chunk of the town
     * hall at once, the rest comes with the journal.
     */
    private static Optional<UUID> independent(MinecraftServer server, UUID from, NobleHouse house, City city, long day) {
        KingdomWorldState world = KingdomWorldApi.state(server);
        ServerLevel level = Dimensions.key(city.dimension()).map(server::getLevel).orElse(null);
        if (level == null) {
            return Optional.empty();
        }
        Optional<Territory> old = world.territory().territory(from);
        long chunk = ChunkKey.of(city.x() >> 4, city.z() >> 4);
        if (old.isEmpty() || old.get().capital().chunk() == chunk) {
            return Optional.empty();
        }
        Set<String> taken = new HashSet<>();
        world.kingdoms().all().forEach(kingdom -> taken.add(KingdomNames.key(kingdom.name())));
        long seed = house.familySeed() * 0x9E3779B9L + city.id();
        NpcNames.Name name = NpcNames.kingdom(seed, candidate -> taken.contains(KingdomNames.key(candidate)));
        UUID id = UUID.nameUUIDFromBytes(("kingdomworld:seceded:" + from + ":" + city.id() + ":" + day).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BlockPos throne = thronePlace(level, new BlockPos(city.x(), city.y(), city.z()));
        if (from.equals(world.territory().ownerOf(city.dimension(), chunk))) {
            world.territory().release(from, chunk);
        }
        CreateKingdomResult result = world.found(id, name.latin(), KingdomColors.PALETTE.get((int) Math.floorMod(seed, KingdomColors.PALETTE.size())),
                null, null, KingdomWorldApi.gameTime(server), KingdomWorldApi.rules());
        if (!(result instanceof CreateKingdomResult.Created)) {
            if (world.territory().ownerOf(city.dimension(), chunk) == null) {
                world.territory().assign(from, chunk);
            }
            return Optional.empty();
        }
        Capital capital = new Capital(city.dimension(), throne.getX(), throne.getY(), throne.getZ());
        world.territory().establish(id, capital, Set.of(chunk));
        if (level.getBlockState(throne).isAir()) {
            level.setBlockAndUpdate(throne, ModRegistries.THRONE.get().defaultBlockState());
        }
        long now = KingdomWorldApi.gameTime(server);
        NpcKingdom[] made = new NpcKingdom[1];
        NpcWork.change(server, "a city that left a kingdom", npc -> {
            NpcSite site = npc.addSite(city.x(), city.z(), NpcLevel.SMALL, seed, List.of());
            site.setKingdomId(id);
            site.setGroundY(city.y());
            site.advance(SpawnStage.DONE);
            site.setStatus(NpcSite.Status.SPAWNED);
            made[0] = new NpcKingdom(id, site.id(), NpcLevel.SMALL, name, house.character(), List.of(house.trait()), house.headSeed(),
                    house.headGender(), List.of(), now, day);
            npc.putKingdom(made[0]);
            return Outcome.DONE;
        });
        if (made[0] != null) {
            NpcWork.remember(made[0]);
        }
        io.github.sekerator.kingdomworld.common.data.KingdomSavedData.get(server).setDirty();
        return Optional.of(id);
    }

    /**
     * A free place for the throne next to the town hall: air with something solid under it, the town hall itself if none.
     */
    private static BlockPos thronePlace(ServerLevel level, BlockPos hall) {
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos at = hall.offset(dx, dy, dz);
                        if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                                && level.getBlockState(at.below()).isSolidRender(level, at.below())) {
                            return at;
                        }
                    }
                }
            }
        }
        return hall.above();
    }
}
